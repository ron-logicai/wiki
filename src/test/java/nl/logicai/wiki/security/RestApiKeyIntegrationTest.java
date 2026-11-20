package nl.logicai.wiki.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * The REST API on /api/v1 (docs/api.md) over real HTTP: a key made on /account/api-keys is the only way in,
 * it acts as its user with that user's current role, and the page rules of the web UI apply (spec F-02,
 * F-10, F-11). Real HTTP on purpose: only there is it visible that a session cookie does nothing on this
 * chain and that a key does nothing on the editor endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RestApiKeyIntegrationTest {

	private static final String PASSWORD = "wachtwoord-1";
	private static final Pattern KEY = Pattern.compile("wiki_[A-Za-z0-9_-]{43}");

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

	@Test
	void keyFromTheAccountPageIsShownOnceAndStoredAsHash() {
		String username = create(Role.EDITOR);
		BrowserSession browser = login(username);

		String key = createKey(browser, "Importscript");

		assertThat(jdbc.queryForObject("select token_hash from api_key where name = 'Importscript' and user_id = ?",
			String.class, userId(username))).hasSize(64).isNotEqualTo(key).doesNotContain("wiki_");
		// The list shows the name and the first characters only.
		String page = browser.get("/account/api-keys").body();
		assertThat(page).contains("Importscript").contains(key.substring(0, 12)).doesNotContain(key);
		assertThat(api("GET", "/api/v1/pages", key, null).statusCode()).isEqualTo(200);
		assertThat(jdbc.queryForList("select details from audit_event where action = 'api.key.create' and actor = ?",
			String.class, username)).containsExactly("Importscript");
	}

	@Test
	void pageIsCreatedReadChangedAndDeletedWithAKey() throws Exception {
		String username = create(Role.EDITOR);
		String key = createKey(login(username), "Koppeling");
		String title = "API-pagina " + shortId();

		HttpResponse<String> created = api("POST", "/api/v1/pages", key,
			json("title", title, "markdown", "Eerste alinea.", "tags", List.of("api-" + shortId())));
		assertThat(created.statusCode()).isEqualTo(201);
		JsonNode page = mapper.readTree(created.body());
		String id = page.get("id").asString();
		long version = page.get("version").asLong();
		assertThat(page.get("title").asString()).isEqualTo(title);
		assertThat(page.get("url").asString()).endsWith("/pages/" + id);
		assertThat(page.get("tags")).hasSize(1);

		JsonNode read = mapper.readTree(api("GET", "/api/v1/pages/" + id, key, null).body());
		assertThat(read.get("markdown").asString()).contains("Eerste alinea.");
		assertThat(read.get("version").asLong()).isEqualTo(version);

		HttpResponse<String> updated = api("PUT", "/api/v1/pages/" + id, key,
			json("baseVersion", version, "markdown", "Eerste alinea.\n\nTweede alinea."));
		assertThat(updated.statusCode()).isEqualTo(200);
		long afterUpdate = mapper.readTree(updated.body()).get("version").asLong();
		assertThat(afterUpdate).isGreaterThan(version);

		// The version the caller read is stale now (spec F-10).
		HttpResponse<String> stale = api("PUT", "/api/v1/pages/" + id, key, json("baseVersion", version, "markdown", "Te laat."));
		assertThat(stale.statusCode()).isEqualTo(409);
		assertThat(mapper.readTree(stale.body()).get("code").asString()).isEqualTo("CONFLICT");
		assertThat(mapper.readTree(stale.body()).get("currentVersion").asLong()).isEqualTo(afterUpdate);

		HttpResponse<String> appended = api("POST", "/api/v1/pages/" + id + "/append", key,
			json("baseVersion", afterUpdate, "markdown", "Derde alinea."));
		assertThat(appended.statusCode()).isEqualTo(200);
		long afterAppend = mapper.readTree(appended.body()).get("version").asLong();
		assertThat(mapper.readTree(api("GET", "/api/v1/pages/" + id, key, null).body()).get("markdown").asString())
			.contains("Eerste alinea.").contains("Tweede alinea.").contains("Derde alinea.").doesNotContain("Te laat.");

		// Changes are the user's own (spec F-11), with an audit entry that names the key.
		assertThat(jdbc.queryForObject("select updated_by from page where id = ?", String.class, id)).isEqualTo(username);
		assertThat(jdbc.queryForList("select details from audit_event where action = 'page.api.create' and page_id = ?",
			String.class, id)).containsExactly("via API (Koppeling)");
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'page.api.update' and page_id = ? and actor = ?",
			Long.class, id, username)).isEqualTo(2);

		assertThat(api("DELETE", "/api/v1/pages/" + id + "?baseVersion=" + version, key, null).statusCode()).isEqualTo(409);
		assertThat(api("DELETE", "/api/v1/pages/" + id + "?baseVersion=" + afterAppend, key, null).statusCode()).isEqualTo(204);
		// Deleted means in the trash: gone for the API, still in the table.
		HttpResponse<String> gone = api("GET", "/api/v1/pages/" + id, key, null);
		assertThat(gone.statusCode()).isEqualTo(404);
		assertThat(mapper.readTree(gone.body()).get("code").asString()).isEqualTo("NOT_FOUND");
		assertThat(jdbc.queryForObject("select count(*) from page where id = ? and deleted_at is not null", Long.class, id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'page.delete' and page_id = ? and actor = ?",
			Long.class, id, username)).isEqualTo(1);
	}

	@Test
	void pageWithSubpagesIsNotDeletedAndBadRequestsAreRefused() throws Exception {
		String key = createKey(login(create(Role.EDITOR)), "Koppeling");
		JsonNode parent = mapper.readTree(api("POST", "/api/v1/pages", key, json("title", "Ouder " + shortId())).body());
		String parentId = parent.get("id").asString();
		JsonNode child = mapper.readTree(api("POST", "/api/v1/pages", key, json("title", "Kind " + shortId(), "parentId", parentId)).body());

		JsonNode children = mapper.readTree(api("GET", "/api/v1/pages?parentId=" + parentId, key, null).body());
		assertThat(children).hasSize(1);
		assertThat(children.get(0).get("id").asString()).isEqualTo(child.get("id").asString());

		HttpResponse<String> refused = api("DELETE", "/api/v1/pages/" + parentId + "?baseVersion=" + parent.get("version").asLong(), key, null);
		assertThat(refused.statusCode()).isEqualTo(409);
		assertThat(mapper.readTree(refused.body()).get("code").asString()).isEqualTo("HAS_SUBPAGES");

		assertThat(api("POST", "/api/v1/pages", key, json("markdown", "Zonder titel.")).statusCode()).isEqualTo(400);
		assertThat(api("POST", "/api/v1/pages", key, "geen json").statusCode()).isEqualTo(400);
		assertThat(api("PUT", "/api/v1/pages/" + parentId, key, json("markdown", "Zonder versie.")).statusCode()).isEqualTo(400);
		assertThat(api("DELETE", "/api/v1/pages/" + parentId, key, null).statusCode()).isEqualTo(400);
		assertThat(api("GET", "/api/v1/pages/geen-uuid", key, null).statusCode()).isEqualTo(400);
		assertThat(api("GET", "/api/v1/pages/" + UUID.randomUUID(), key, null).statusCode()).isEqualTo(404);
	}

	@Test
	void onlyAnApiKeyGivesAccess() throws Exception {
		String username = create(Role.EDITOR);
		BrowserSession browser = login(username);
		String key = createKey(browser, "Koppeling");
		String id = mapper.readTree(api("POST", "/api/v1/pages", key, json("title", "Alleen key " + shortId())).body()).get("id").asString();

		HttpResponse<String> without = api("GET", "/api/v1/pages", null, null);
		assertThat(without.statusCode()).isEqualTo(401);
		assertThat(without.headers().firstValue("WWW-Authenticate")).contains("Bearer");
		assertThat(mapper.readTree(without.body()).get("code").asString()).isEqualTo("UNAUTHORIZED");
		assertThat(api("GET", "/api/v1/pages", "wiki_" + "x".repeat(43), null).statusCode()).isEqualTo(401);
		assertThat(api("POST", "/api/v1/pages", null, json("title", "Zonder key")).statusCode()).isEqualTo(401);

		// A logged-in browser session is not a way in: no login redirect, no access.
		assertThat(browser.get("/pages/" + id).statusCode()).isEqualTo(200);
		assertThat(browser.get("/api/v1/pages").statusCode()).isEqualTo(401);
		assertThat(browser.get("/api/v1/pages/" + id).statusCode()).isEqualTo(401);

		// And a key is no way in on the editor endpoints, which stay on session + CSRF.
		HttpResponse<String> editorEndpoint = api("PUT", "/api/pages/" + id + "/content", key,
			"{\"baseVersion\":0,\"title\":\"Via key\",\"document\":[]}");
		assertThat(editorEndpoint.statusCode()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select title from page where id = ?", String.class, id)).startsWith("Alleen key");
	}

	@Test
	void keyFollowsTheCurrentRoleAndStateOfItsUser() throws Exception {
		String username = create(Role.EDITOR);
		String key = createKey(login(username), "Koppeling");
		assertThat(api("POST", "/api/v1/pages", key, json("title", "Als editor " + shortId())).statusCode()).isEqualTo(201);

		// Demoted to viewer: reading still works, changing does not (spec F-02).
		WikiUser user = users.findByUsernameIgnoreCase(username).orElseThrow();
		user.changeRole(Role.VIEWER);
		users.save(user);
		assertThat(api("GET", "/api/v1/pages", key, null).statusCode()).isEqualTo(200);
		HttpResponse<String> forbidden = api("POST", "/api/v1/pages", key, json("title", "Als viewer " + shortId()));
		assertThat(forbidden.statusCode()).isEqualTo(403);
		assertThat(mapper.readTree(forbidden.body()).get("code").asString()).isEqualTo("FORBIDDEN");

		// A viewer cannot make a key at all.
		BrowserSession viewer = login(username);
		HttpResponse<String> form = viewer.get("/account/api-keys");
		assertThat(form.body()).doesNotContain("Key aanmaken");
		assertThat(viewer.postForm("/account/api-keys", Map.of("naam", "Stiekem", "_csrf", BrowserSession.csrfToken(form.body())))
			.statusCode()).isEqualTo(403);

		user = users.findByUsernameIgnoreCase(username).orElseThrow();
		user.setActive(false);
		users.save(user);
		assertThat(api("GET", "/api/v1/pages", key, null).statusCode()).isEqualTo(401);
	}

	@Test
	void revokedKeysStopWorking() {
		String username = create(Role.EDITOR);
		String admin = create(Role.ADMIN);
		BrowserSession browser = login(username);
		String own = createKey(browser, "Zelf ingetrokken");
		String byAdmin = createKey(browser, "Door beheerder");
		String byPassword = createKey(browser, "Door wachtwoord");

		// The user revokes one of their own keys.
		String ownId = keyId(username, "Zelf ingetrokken");
		String csrf = BrowserSession.csrfToken(browser.get("/account/api-keys").body());
		assertThat(browser.location(browser.postForm("/account/api-keys/" + ownId + "/intrekken", Map.of("_csrf", csrf))))
			.isEqualTo("/account/api-keys");
		assertThat(api("GET", "/api/v1/pages", own, null).statusCode()).isEqualTo(401);
		assertThat(api("GET", "/api/v1/pages", byAdmin, null).statusCode()).isEqualTo(200);

		// Someone else cannot revoke it through the account page; only an admin sees the overview and revokes there.
		String otherId = keyId(username, "Door beheerder");
		BrowserSession adminBrowser = login(admin);
		String adminCsrf = BrowserSession.csrfToken(adminBrowser.get("/account/api-keys").body());
		adminBrowser.postForm("/account/api-keys/" + otherId + "/intrekken", Map.of("_csrf", adminCsrf));
		assertThat(api("GET", "/api/v1/pages", byAdmin, null).statusCode()).isEqualTo(200);
		assertThat(browser.get("/admin/api-keys").statusCode()).isEqualTo(403);
		HttpResponse<String> overview = adminBrowser.get("/admin/api-keys");
		assertThat(overview.statusCode()).isEqualTo(200);
		assertThat(overview.body()).contains("Door beheerder").contains("Test " + username);
		adminBrowser.postForm("/admin/api-keys/" + otherId + "/intrekken", Map.of("_csrf", BrowserSession.csrfToken(overview.body())));
		assertThat(api("GET", "/api/v1/pages", byAdmin, null).statusCode()).isEqualTo(401);

		// A password change removes what is left: whoever knew the old password may have made a key with it.
		assertThat(api("GET", "/api/v1/pages", byPassword, null).statusCode()).isEqualTo(200);
		HttpResponse<String> form = browser.get("/account/wachtwoord");
		browser.postForm("/account/wachtwoord", Map.of("currentPassword", PASSWORD, "password", "nieuw-wachtwoord-2",
			"passwordRepeat", "nieuw-wachtwoord-2", "_csrf", BrowserSession.csrfToken(form.body())));
		assertThat(api("GET", "/api/v1/pages", byPassword, null).statusCode()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select count(*) from api_key where user_id = ?", Long.class, userId(username))).isZero();
		assertThat(jdbc.queryForList("select actor from audit_event where action = 'api.key.revoke' and details like ?",
			String.class, "%" + username)).containsExactlyInAnyOrder(username, admin, username);
	}

	@Test
	void deletingAUserRemovesTheirKeys() {
		String username = create(Role.EDITOR);
		String admin = create(Role.ADMIN);
		String key = createKey(login(username), "Van verwijderde gebruiker");
		String id = userId(username);

		BrowserSession adminBrowser = login(admin);
		String csrf = BrowserSession.csrfToken(adminBrowser.get("/admin/users").body());
		assertThat(adminBrowser.location(adminBrowser.postForm("/admin/users/" + id + "/delete", Map.of("_csrf", csrf))))
			.isEqualTo("/admin/users");

		assertThat(users.findByUsernameIgnoreCase(username)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from api_key where user_id = ?", Long.class, id)).isZero();
		assertThat(api("GET", "/api/v1/pages", key, null).statusCode()).isEqualTo(401);
	}

	/** Makes a key on the account page, the way a user does, and reads it from the one page that shows it. */
	private String createKey(BrowserSession browser, String name) {
		String csrf = BrowserSession.csrfToken(browser.get("/account/api-keys").body());
		HttpResponse<String> posted = browser.postForm("/account/api-keys", Map.of("naam", name, "_csrf", csrf));
		assertThat(browser.location(posted)).isEqualTo("/account/api-keys");
		Matcher matcher = KEY.matcher(browser.get("/account/api-keys").body());
		assertThat(matcher.find()).as("de nieuwe key staat op de pagina").isTrue();
		return matcher.group();
	}

	private BrowserSession login(String username) {
		BrowserSession browser = new BrowserSession(port);
		String csrf = BrowserSession.csrfToken(browser.get("/login").body());
		HttpResponse<String> login = browser.postForm("/login",
			Map.of("username", username + "@example.com", "password", PASSWORD, "_csrf", csrf));
		assertThat(login.statusCode()).isEqualTo(302);
		return browser;
	}

	private HttpResponse<String> api(String method, String path, String key, String body) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Accept", "application/json")
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		if (key != null) {
			request.header("Authorization", "Bearer " + key);
		}
		try {
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String json(Object... keysAndValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return mapper.writeValueAsString(map);
	}

	private String create(Role role) {
		String username = role.name().toLowerCase() + "." + shortId();
		users.save(WikiUser.create(username, "Test " + username, username + "@example.com", encoder.encode(PASSWORD), role,
			"test", clock.instant()));
		return username;
	}

	private String userId(String username) {
		return jdbc.queryForObject("select id from app_user where username = ?", String.class, username);
	}

	private String keyId(String username, String name) {
		return jdbc.queryForObject("select id from api_key where user_id = ? and name = ?", String.class, userId(username), name);
	}

	private static String shortId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

}
