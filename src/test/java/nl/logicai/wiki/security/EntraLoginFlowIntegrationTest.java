package nl.logicai.wiki.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec N-02 "test de volledige loginflow", Entra variant, over real HTTP against {@link FakeEntra}:
 * the redirect to the identity provider, the session cookie that must survive the cross-site
 * callback (SameSite=Lax), the state and nonce checks, the authorization-code exchange, the mapping
 * to an active app_user with the wiki role (spec F-01), and the refusals for a forged state, an
 * unknown account and a deactivated account. Only the browser leg to Entra itself is simulated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.security.oauth2.client.registration.entra.provider=entra",
	"spring.security.oauth2.client.registration.entra.client-id=" + EntraLoginFlowIntegrationTest.CLIENT_ID,
	"spring.security.oauth2.client.registration.entra.client-secret=test-secret",
	"spring.security.oauth2.client.registration.entra.authorization-grant-type=authorization_code",
	"spring.security.oauth2.client.registration.entra.scope=openid,profile,email",
	"spring.security.oauth2.client.registration.entra.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}"
})
@Import(TestcontainersConfiguration.class)
class EntraLoginFlowIntegrationTest {

	static final String CLIENT_ID = "wiki-test-client";

	private static final FakeEntra ENTRA = FakeEntra.start(CLIENT_ID);

	@DynamicPropertySource
	static void provider(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.entra.authorization-uri", () -> ENTRA.url("/authorize"));
		registry.add("spring.security.oauth2.client.provider.entra.token-uri", () -> ENTRA.url("/token"));
		registry.add("spring.security.oauth2.client.provider.entra.jwk-set-uri", () -> ENTRA.url("/jwks"));
	}

	@AfterAll
	static void stopEntra() {
		ENTRA.stop();
	}

	@LocalServerPort
	private int port;

	@Autowired
	private WikiUserRepository users;

	@Autowired
	private Clock clock;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void loginPageOffersEntraLogin() {
		HttpResponse<String> login = new BrowserSession(port).get("/login");
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).contains("/oauth2/authorization/entra");
	}

	@Test
	void entraLoginCompletesThroughLaxCookieAndUsesWikiRole() {
		String email = "ron.entra@example.com";
		users.save(WikiUser.create("ron.entra", "Ron Entra", email, null, Role.ADMIN, "test", clock.instant()));
		BrowserSession browser = new BrowserSession(port);

		// 1. The wiki sends the browser to Entra and stores state and nonce in a fresh session.
		HttpResponse<String> start = browser.get("/oauth2/authorization/entra");
		assertThat(start.statusCode()).isEqualTo(302);
		URI authorize = URI.create(browser.location(start));
		assertThat(authorize.toString()).startsWith(ENTRA.url("/authorize"));
		Map<String, String> query = FakeEntra.parseForm(authorize.getRawQuery());
		assertThat(query).containsEntry("response_type", "code")
			.containsEntry("client_id", CLIENT_ID)
			.containsEntry("redirect_uri", browser.baseUrl() + "/login/oauth2/code/entra");
		assertThat(query.get("scope")).contains("openid");
		assertThat(query.get("state")).isNotBlank();
		assertThat(query.get("nonce")).isNotBlank();
		// The callback is a top-level GET from another site; Lax still sends this cookie, Strict would not.
		LoginFlowIntegrationTest.assertHardened(browser.lastSetCookie("JSESSIONID").orElseThrow());
		String sessionBeforeLogin = browser.cookie("JSESSIONID").orElseThrow();

		// 2. Entra sends the browser back with a code; the wiki exchanges it and verifies the ID token.
		ENTRA.nextIdToken(Map.of("nonce", query.get("nonce"), "preferred_username", email, "email", email,
				"name", "Ron Entra"));
		HttpResponse<String> callback = browser.get(callbackPath(query.get("state")));
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(browser.location(callback)).isEqualTo("/");
		assertThat(ENTRA.lastTokenRequest()).containsEntry("grant_type", "authorization_code")
			.containsEntry("code", "fake-code")
			.containsEntry("redirect_uri", browser.baseUrl() + "/login/oauth2/code/entra");
		assertThat(browser.cookie("JSESSIONID").orElseThrow()).isNotEqualTo(sessionBeforeLogin);

		// 3. Logged in as the wiki user, with the role from app_user rather than anything Entra sent.
		HttpResponse<String> home = browser.get("/");
		assertThat(home.statusCode()).isEqualTo(200);
		assertThat(home.body()).contains("ron.entra");
		assertThat(browser.get("/admin").statusCode()).isEqualTo(200);
		assertThat(users.findByUsernameIgnoreCase("ron.entra").orElseThrow().getLastLoginAt()).isNotNull();

		// 4. Logout with the page token ends the session.
		String token = BrowserSession.csrfToken(home.body());
		HttpResponse<String> logout = browser.postForm("/logout", Map.of("_csrf", token));
		assertThat(browser.location(logout)).isEqualTo("/login?logout");
		assertThat(browser.get("/").statusCode()).isEqualTo(302);
	}

	@Test
	void callbackWithForgedStateIsRefused() {
		BrowserSession browser = new BrowserSession(port);
		HttpResponse<String> start = browser.get("/oauth2/authorization/entra");
		Map<String, String> query = FakeEntra.parseForm(URI.create(browser.location(start)).getRawQuery());
		ENTRA.nextIdToken(Map.of("nonce", query.get("nonce"), "preferred_username", "wie.dan.ook@example.com"));

		HttpResponse<String> callback = browser.get(callbackPath("forged-state"));
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(browser.location(callback)).isEqualTo("/login?error");
		assertThat(browser.get("/").statusCode()).isEqualTo(302);
	}

	@Test
	void callbackWithoutSessionCookieIsRefused() {
		// What would happen with SameSite=Strict: the browser drops the cookie on the callback.
		BrowserSession browser = new BrowserSession(port);
		HttpResponse<String> start = browser.get("/oauth2/authorization/entra");
		Map<String, String> query = FakeEntra.parseForm(URI.create(browser.location(start)).getRawQuery());
		ENTRA.nextIdToken(Map.of("nonce", query.get("nonce"), "preferred_username", "wie.dan.ook@example.com"));
		browser.forgetCookies();

		HttpResponse<String> callback = browser.get(callbackPath(query.get("state")));
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(browser.location(callback)).isEqualTo("/login?error");
	}

	@Test
	void unknownAndDeactivatedEntraAccountsAreRefused() {
		users.save(WikiUser.create("ron.uit.entra", "Uit", "ron.uit@example.com", null, Role.EDITOR, "test",
				clock.instant()));
		jdbc.update("update app_user set active = false where username = ?", "ron.uit.entra");

		assertRefused("niemand@example.com");
		assertRefused("ron.uit@example.com");
	}

	private void assertRefused(String email) {
		BrowserSession browser = new BrowserSession(port);
		HttpResponse<String> start = browser.get("/oauth2/authorization/entra");
		Map<String, String> query = FakeEntra.parseForm(URI.create(browser.location(start)).getRawQuery());
		ENTRA.nextIdToken(Map.of("nonce", query.get("nonce"), "preferred_username", email, "email", email));

		HttpResponse<String> callback = browser.get(callbackPath(query.get("state")));
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(browser.location(callback)).isEqualTo("/login?error");
		assertThat(browser.get("/").statusCode()).isEqualTo(302);
		assertThat(browser.get("/admin").statusCode()).isEqualTo(302);
	}

	private static String callbackPath(String state) {
		return "/login/oauth2/code/entra?code=fake-code&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
	}

}
