package nl.logicai.wiki.security;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connecting an AI agent the way Claude or ChatGPT does it (docs/mcp.md), over real HTTP: discovery from
 * the first 401, dynamic client registration, the authorization request with PKCE, the normal wiki login,
 * consent, the token exchange and then MCP calls with the bearer token. Also what must stop working:
 * a redirect URI that is not allowlisted, a viewer who tries to write, a deactivated user, a revoked
 * connection and a refresh token that is used twice.
 *
 * <p>The test server runs on a random port while the issuer is {@code wiki.base-url}
 * ({@code http://localhost:8080}); the URLs in the metadata are therefore asserted as text and requests go
 * to the same paths on the real port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"wiki.mcp.enabled=true", "wiki.mcp.max-registrations-per-hour=1000"})
@Import(TestcontainersConfiguration.class)
class McpOAuthFlowIntegrationTest {

	private static final String PASSWORD = "wachtwoord-1";
	private static final String ISSUER = "http://localhost:8080";

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	@LocalServerPort
	private int port;

	@Autowired
	private WikiUserRepository users;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private Clock clock;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper mapper;

	/** What a client holds after connecting. */
	private record Connection(String clientId, String accessToken, String refreshToken) {
	}

	@Test
	void discoveryStartsAtThe401AndDescribesTheServer() throws Exception {
		HttpResponse<String> anonymous = mcp(null, "tools/list", "{}");
		assertThat(anonymous.statusCode()).isEqualTo(401);
		assertThat(anonymous.headers().firstValue("WWW-Authenticate").orElseThrow())
			.startsWith("Bearer").contains("resource_metadata=").contains("/.well-known/oauth-protected-resource/mcp");
		// Stateless and without a session: an agent's request never creates a session cookie.
		assertThat(anonymous.headers().allValues("Set-Cookie")).isEmpty();

		JsonNode resource = json(get("/.well-known/oauth-protected-resource/mcp"));
		assertThat(resource.path("resource").asString()).isEqualTo(ISSUER + "/mcp");
		assertThat(resource.path("authorization_servers").get(0).asString()).isEqualTo(ISSUER);
		assertThat(resource.path("scopes_supported").get(0).asString()).isEqualTo("wiki");

		JsonNode server = json(get("/.well-known/oauth-authorization-server"));
		assertThat(server.path("issuer").asString()).isEqualTo(ISSUER);
		assertThat(server.path("authorization_endpoint").asString()).isEqualTo(ISSUER + "/oauth2/authorize");
		assertThat(server.path("token_endpoint").asString()).isEqualTo(ISSUER + "/oauth2/token");
		assertThat(server.path("registration_endpoint").asString()).isEqualTo(ISSUER + "/oauth2/register");
		assertThat(server.path("code_challenge_methods_supported").valueStream().map(JsonNode::asString)).contains("S256");
		assertThat(server.path("grant_types_supported").valueStream().map(JsonNode::asString))
			.contains("authorization_code", "refresh_token");
		assertThat(server.path("token_endpoint_auth_methods_supported").valueStream().map(JsonNode::asString)).contains("none");

		// Clients probe the OpenID path as well; it must be a plain 404, not a redirect to the login page.
		assertThat(get("/.well-known/openid-configuration").statusCode()).isEqualTo(404);
	}

	@Test
	void registrationOnlyAcceptsAllowlistedRedirectUris() throws Exception {
		HttpResponse<String> evil = register("Kwaadaardig", "https://evil.example/callback");
		assertThat(evil.statusCode()).isEqualTo(400);
		assertThat(json(evil).path("error").asString()).isEqualTo("invalid_redirect_uri");

		HttpResponse<String> lookalike = register("Lijkt erop", "https://claude.ai.evil.example/api/mcp/auth_callback");
		assertThat(lookalike.statusCode()).isEqualTo(400);

		// ChatGPT's per-connector address is allowed by the /* entry, but nothing can climb out of it.
		assertThat(register("ChatGPT", "https://chatgpt.com/connector/oauth/abc_123").statusCode()).isEqualTo(201);
		assertThat(register("Klimmer", "https://chatgpt.com/connector/oauth/../../evil").statusCode()).isEqualTo(400);
		assertThat(register("Query", "https://chatgpt.com/connector/oauth/abc?next=https://evil.example").statusCode()).isEqualTo(400);

		HttpResponse<String> claude = register("Claude", "https://claude.ai/api/mcp/auth_callback");
		assertThat(claude.statusCode()).isEqualTo(201);
		JsonNode client = json(claude);
		assertThat(client.path("client_id").asString()).isNotBlank();
		// Whatever the client asks for, it gets the one scope, both grant types and no secret when it is a public client.
		assertThat(client.path("scope").asString()).isEqualTo("wiki");
		assertThat(client.path("grant_types").valueStream().map(JsonNode::asString))
			.containsExactlyInAnyOrder("authorization_code", "refresh_token");
		assertThat(client.has("client_secret")).isFalse();
	}

	@Test
	void editorConnectsAnAgentAndWorksThroughMcp() throws Exception {
		String username = create("mcp.editor." + shortId(), Role.EDITOR);

		Connection connection = connect(username, true);

		HttpResponse<String> tools = mcp(connection.accessToken(), "tools/list", "{}");
		assertThat(tools.statusCode()).isEqualTo(200);
		assertThat(json(tools).path("result").path("tools").findValuesAsString("name")).contains("search", "create_page");

		String title = "Via OAuth " + shortId();
		JsonNode created = call(connection.accessToken(), "create_page",
			"{\"title\":\"" + title + "\",\"markdown\":\"Geschreven door een agent.\"}");
		assertThat(created.path("isError").asBoolean(false)).isFalse();
		String pageId = created.path("structuredContent").path("id").asString();

		// The page and its revisions carry the wiki username of the user, as if they had typed it themselves.
		assertThat(jdbc.queryForObject("select updated_by from page where id = ?", String.class, pageId)).isEqualTo(username);
		assertThat(jdbc.queryForList("select action, actor, details from audit_event where page_id = ?", pageId))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.get("action")).isEqualTo("page.mcp.create");
				assertThat(row.get("actor")).isEqualTo(username);
				assertThat(row.get("details")).isEqualTo("via MCP (Testagent)");
			});
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'mcp.connect' and actor = ?",
			Long.class, username)).isEqualTo(1);
		// The stored connection holds a plain principal: no password hash, no ID token (spec N-04).
		assertThat(jdbc.queryForList("select cast(attributes as char) from oauth2_authorization where principal_name = ?",
			String.class, username)).isNotEmpty()
			.allSatisfy(attributes -> assertThat(attributes).contains(username).doesNotContain("{bcrypt}").doesNotContain("id_token"));
	}

	@Test
	void viewerCanReadButNotWriteAndARoleChangeAppliesAtOnce() throws Exception {
		String username = create("mcp.viewer." + shortId(), Role.VIEWER);
		Connection connection = connect(username, true);

		assertThat(call(connection.accessToken(), "list_tags", "{}").path("isError").asBoolean(false)).isFalse();
		JsonNode refused = call(connection.accessToken(), "create_page", "{\"title\":\"Mag niet " + shortId() + "\"}");
		assertThat(refused.path("isError").asBoolean()).isTrue();
		assertThat(refused.path("content").get(0).path("text").asString()).startsWith("FORBIDDEN");

		// The token carries no role: the same token can write as soon as the user is an editor (spec F-02).
		jdbc.update("update app_user set role = 'EDITOR' where username = ?", username);
		JsonNode allowed = call(connection.accessToken(), "create_page", "{\"title\":\"Mag nu wel " + shortId() + "\"}");
		assertThat(allowed.path("isError").asBoolean(false)).isFalse();
	}

	@Test
	void refreshRotatesTheTokenAndAUsedRefreshTokenIsRefused() throws Exception {
		String username = create("mcp.refresh." + shortId(), Role.EDITOR);
		Connection connection = connect(username, true);

		HttpResponse<String> refreshed = token(Map.of("grant_type", "refresh_token",
			"refresh_token", connection.refreshToken(), "client_id", connection.clientId()));
		assertThat(refreshed.statusCode()).as(refreshed.body()).isEqualTo(200);
		JsonNode tokens = json(refreshed);
		assertThat(tokens.path("refresh_token").asString()).isNotBlank().isNotEqualTo(connection.refreshToken());
		assertThat(mcp(tokens.path("access_token").asString(), "tools/list", "{}").statusCode()).isEqualTo(200);

		HttpResponse<String> replay = token(Map.of("grant_type", "refresh_token",
			"refresh_token", connection.refreshToken(), "client_id", connection.clientId()));
		assertThat(replay.statusCode()).isEqualTo(400);
		assertThat(json(replay).path("error").asString()).isEqualTo("invalid_grant");
	}

	@Test
	void deactivatedUserAndRevokedConnectionAreCutOffAtOnce() throws Exception {
		String deactivated = create("mcp.uit." + shortId(), Role.EDITOR);
		Connection first = connect(deactivated, true);
		assertThat(mcp(first.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(200);

		jdbc.update("update app_user set active = false where username = ?", deactivated);
		assertThat(mcp(first.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(401);
		HttpResponse<String> refresh = token(Map.of("grant_type", "refresh_token",
			"refresh_token", first.refreshToken(), "client_id", first.clientId()));
		assertThat(refresh.statusCode()).isEqualTo(400);
		assertThat(json(refresh).path("error").asString()).isEqualTo("invalid_grant");

		String revoked = create("mcp.ingetrokken." + shortId(), Role.EDITOR);
		Connection second = connect(revoked, true);
		assertThat(mcp(second.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(200);
		jdbc.update("delete from oauth2_authorization where principal_name = ?", revoked);
		assertThat(mcp(second.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(401);

		// A token that was never issued by this wiki is refused as well.
		assertThat(mcp("geen.echt.token", "tools/list", "{}").statusCode()).isEqualTo(401);
	}

	@Test
	void userAndAdminSeeConnectionsAndRevokeThem() throws Exception {
		String username = create("mcp.lijst." + shortId(), Role.EDITOR);
		String admin = create("mcp.beheer." + shortId(), Role.ADMIN);
		Connection own = connect(username, true);
		Connection byAdmin = connect(username, true);

		// The user sees both connections on the account page and revokes the first one.
		BrowserSession browser = new BrowserSession(port);
		assertThat(login(browser, username).statusCode()).isEqualTo(302);
		HttpResponse<String> page = browser.get("/account/koppelingen");
		assertThat(page.statusCode()).isEqualTo(200);
		assertThat(page.body()).contains("Gekoppelde apps").contains("Testagent").contains("localhost")
			.contains(ISSUER + "/mcp");
		String ownId = jdbc.queryForObject("select id from oauth2_authorization where principal_name = ? order by access_token_issued_at limit 1",
			String.class, username);
		HttpResponse<String> revoked = browser.postForm("/account/koppelingen/" + ownId + "/intrekken",
			Map.of("_csrf", BrowserSession.csrfToken(page.body())));
		assertThat(browser.location(revoked)).isEqualTo("/account/koppelingen");
		assertThat(mcp(own.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(401);
		assertThat(mcp(byAdmin.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(200);

		// Someone else cannot revoke it through the account page, and only an admin sees the overview.
		String otherId = jdbc.queryForObject("select id from oauth2_authorization where principal_name = ?", String.class, username);
		BrowserSession adminBrowser = new BrowserSession(port);
		assertThat(login(adminBrowser, admin).statusCode()).isEqualTo(302);
		String adminCsrf = BrowserSession.csrfToken(adminBrowser.get("/account/koppelingen").body());
		adminBrowser.postForm("/account/koppelingen/" + otherId + "/intrekken", Map.of("_csrf", adminCsrf));
		assertThat(mcp(byAdmin.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(200);
		assertThat(browser.get("/admin/koppelingen").statusCode()).isEqualTo(403);

		HttpResponse<String> overview = adminBrowser.get("/admin/koppelingen");
		assertThat(overview.statusCode()).isEqualTo(200);
		assertThat(overview.body()).contains("Testagent").contains("Test " + username.replace('.', ' '));
		adminBrowser.postForm("/admin/koppelingen/" + otherId + "/intrekken",
			Map.of("_csrf", BrowserSession.csrfToken(overview.body())));
		assertThat(mcp(byAdmin.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(401);
		// With the last connection gone the consent is gone too: connecting again asks again.
		assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization_consent where principal_name = ?",
			Long.class, username)).isZero();
		assertThat(jdbc.queryForList("select actor from audit_event where action = 'mcp.revoke' and details like ?",
			String.class, "%" + username)).containsExactlyInAnyOrder(username, admin);
	}

	@Test
	void changingThePasswordDisconnectsEveryAgent() throws Exception {
		String username = create("mcp.wachtwoord." + shortId(), Role.EDITOR);
		Connection connection = connect(username, true);
		BrowserSession browser = new BrowserSession(port);
		assertThat(login(browser, username).statusCode()).isEqualTo(302);

		HttpResponse<String> form = browser.get("/account/wachtwoord");
		assertThat(form.body()).contains("Gekoppelde apps");
		HttpResponse<String> changed = browser.postForm("/account/wachtwoord", Map.of(
			"currentPassword", PASSWORD, "password", "nieuw-wachtwoord-2", "passwordRepeat", "nieuw-wachtwoord-2",
			"_csrf", BrowserSession.csrfToken(form.body())));
		assertThat(browser.location(changed)).isEqualTo("/account/wachtwoord");

		assertThat(mcp(connection.accessToken(), "tools/list", "{}").statusCode()).isEqualTo(401);
		HttpResponse<String> refresh = token(Map.of("grant_type", "refresh_token",
			"refresh_token", connection.refreshToken(), "client_id", connection.clientId()));
		assertThat(refresh.statusCode()).isEqualTo(400);
	}

	@Test
	void refusingConsentSendsTheUserBackWithoutACode() throws Exception {
		String username = create("mcp.weiger." + shortId(), Role.EDITOR);

		Connection connection = connect(username, false);

		assertThat(connection.accessToken()).isNull();
		assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization_consent where principal_name = ?",
			Long.class, username)).isZero();
	}

	@Test
	void anOrdinaryLoginStillLandsOnTheHomePage() {
		String username = create("mcp.gewoon." + shortId(), Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);
		assertThat(browser.location(browser.get("/pages/" + UUID.randomUUID()))).isEqualTo("/login");

		HttpResponse<String> login = login(browser, username);

		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(browser.location(login)).isEqualTo("/");
	}

	/**
	 * The whole connect flow for one user: register a client, authorize with PKCE, log in, answer the consent
	 * page and exchange the code. With {@code consent} false the user clicks "Weigeren" and no tokens exist.
	 */
	private Connection connect(String username, boolean consent) throws Exception {
		// A desktop client registers a loopback redirect and later listens on a port it picks at that moment.
		JsonNode client = json(register("Testagent", "http://localhost:3118/callback"));
		String clientId = client.path("client_id").asString();
		String redirectUri = "http://localhost:" + (40000 + (int) (Math.random() * 20000)) + "/callback";
		String verifier = UUID.randomUUID() + "-" + UUID.randomUUID();
		String challenge = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		String state = "state-" + shortId();

		Map<String, String> query = new LinkedHashMap<>();
		query.put("response_type", "code");
		query.put("client_id", clientId);
		query.put("redirect_uri", redirectUri);
		query.put("state", state);
		query.put("code_challenge", challenge);
		query.put("code_challenge_method", "S256");
		query.put("resource", ISSUER + "/mcp");
		String authorize = "/oauth2/authorize?" + form(query);

		// Not logged in: to the login page, and after logging in back to the authorization request.
		BrowserSession browser = new BrowserSession(port);
		assertThat(browser.location(browser.get(authorize))).isEqualTo("/login");
		HttpResponse<String> login = login(browser, username);
		assertThat(login.statusCode()).isEqualTo(302);
		String back = browser.location(login);
		assertThat(back).startsWith("/oauth2/authorize?");

		// Logged in but no consent yet: to the consent page, which names the app and where it sends the user.
		HttpResponse<String> authorized = browser.get(back);
		assertThat(authorized.statusCode()).isEqualTo(302);
		String consentUrl = browser.location(authorized);
		assertThat(consentUrl).startsWith("/oauth2/consent?");
		HttpResponse<String> consentPage = browser.get(consentUrl);
		assertThat(consentPage.statusCode()).isEqualTo(200);
		assertThat(consentPage.body()).contains("Toegang geven aan").contains("Testagent").contains("localhost")
			.contains("Test " + username.replace('.', ' '));
		String consentState = param(consentUrl, "state");

		Map<String, String> answer = new LinkedHashMap<>();
		answer.put("client_id", clientId);
		answer.put("state", consentState);
		if (consent) {
			answer.put("scope", "wiki");
		}
		HttpResponse<String> decided = browser.postForm("/oauth2/authorize", answer);
		assertThat(decided.statusCode()).as(decided.body()).isEqualTo(302);
		String callback = decided.headers().firstValue("Location").orElseThrow();
		assertThat(callback).startsWith(redirectUri + "?");
		assertThat(param(callback, "state")).isEqualTo(state);
		if (!consent) {
			assertThat(param(callback, "error")).isEqualTo("access_denied");
			assertThat(callback).doesNotContain("code=");
			return new Connection(clientId, null, null);
		}

		// Without the PKCE verifier the code is worthless: a public client proves itself with the verifier.
		HttpResponse<String> withoutVerifier = token(Map.of("grant_type", "authorization_code",
			"code", param(callback, "code"), "redirect_uri", redirectUri, "client_id", clientId));
		assertThat(withoutVerifier.statusCode()).isEqualTo(401);
		HttpResponse<String> wrongVerifier = token(Map.of("grant_type", "authorization_code",
			"code", param(callback, "code"), "redirect_uri", redirectUri, "client_id", clientId,
			"code_verifier", "x".repeat(64)));
		assertThat(wrongVerifier.statusCode()).isIn(400, 401);

		// Asking again while logged in and with consent given: straight back to the client with a fresh code.
		HttpResponse<String> again = browser.get(authorize);
		assertThat(again.statusCode()).isEqualTo(302);
		String secondCallback = again.headers().firstValue("Location").orElseThrow();
		// Consent was given, so this time there is no consent page.
		assertThat(secondCallback).startsWith(redirectUri + "?");

		Map<String, String> exchange = new LinkedHashMap<>();
		exchange.put("grant_type", "authorization_code");
		exchange.put("code", param(secondCallback, "code"));
		exchange.put("redirect_uri", redirectUri);
		exchange.put("client_id", clientId);
		exchange.put("code_verifier", verifier);
		exchange.put("resource", ISSUER + "/mcp");
		HttpResponse<String> exchanged = token(exchange);
		assertThat(exchanged.statusCode()).as(exchanged.body()).isEqualTo(200);
		JsonNode tokens = json(exchanged);
		assertThat(tokens.path("token_type").asString()).isEqualToIgnoringCase("Bearer");
		assertThat(tokens.path("expires_in").asInt()).isBetween(1700, 1800);
		assertThat(tokens.path("refresh_token").asString()).isNotBlank();
		return new Connection(clientId, tokens.path("access_token").asString(), tokens.path("refresh_token").asString());
	}

	private HttpResponse<String> login(BrowserSession browser, String username) {
		String csrf = BrowserSession.csrfToken(browser.get("/login").body());
		return browser.postForm("/login", Map.of("username", username + "@example.com", "password", PASSWORD, "_csrf", csrf));
	}

	private HttpResponse<String> register(String name, String redirectUri) throws Exception {
		String body = mapper.writeValueAsString(Map.of(
			"client_name", name,
			"redirect_uris", java.util.List.of(redirectUri),
			"grant_types", java.util.List.of("authorization_code", "refresh_token"),
			"response_types", java.util.List.of("code"),
			"token_endpoint_auth_method", "none",
			"scope", "openid offline_access"));
		return http.send(HttpRequest.newBuilder(uri("/oauth2/register"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> token(Map<String, String> fields) throws Exception {
		return http.send(HttpRequest.newBuilder(uri("/oauth2/token"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form(fields))).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> mcp(String accessToken, String method, String params) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri("/mcp"))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(
				"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}"));
		if (accessToken != null) {
			request.header("Authorization", "Bearer " + accessToken);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private JsonNode call(String accessToken, String tool, String arguments) throws Exception {
		HttpResponse<String> response = mcp(accessToken, "tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return json(response).path("result");
	}

	private JsonNode json(HttpResponse<String> response) {
		return mapper.readTree(response.body());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String form(Map<String, String> fields) {
		return fields.entrySet().stream()
			.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
	}

	private static String param(String url, String name) {
		String query = url.substring(url.indexOf('?') + 1);
		for (String pair : query.split("&")) {
			String[] parts = pair.split("=", 2);
			if (parts[0].equals(name)) {
				return parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
			}
		}
		return null;
	}

	private String create(String username, Role role) {
		users.save(WikiUser.create(username, "Test " + username.replace('.', ' '), username + "@example.com",
			encoder.encode(PASSWORD), role, "test", clock.instant()));
		return username;
	}

	private static String shortId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

}
