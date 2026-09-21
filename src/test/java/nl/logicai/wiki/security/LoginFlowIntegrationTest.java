package nl.logicai.wiki.security;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec N-02 "test de volledige loginflow", password variant, over real HTTP: anonymous redirect,
 * login form with CSRF, session cookie attributes (HttpOnly, Secure, SameSite=Lax), session id
 * rotation on login, an authenticated fetch save with the header token, logout, and the refusals
 * for a wrong password, a deactivated user and an Entra-only account (spec F-01, F-02).
 * Runs without the {@code local} profile, so the cookie flags are the production ones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginFlowIntegrationTest {

	private static final String PASSWORD = "wachtwoord-1";

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

	@Test
	void anonymousVisitorIsSentToLoginAndApiGets401() {
		BrowserSession browser = new BrowserSession(port);

		HttpResponse<String> page = browser.get("/pages/" + UUID.randomUUID());
		assertThat(page.statusCode()).isEqualTo(302);
		assertThat(browser.location(page)).isEqualTo("/login");

		HttpResponse<String> api = browser.get("/api/pages/" + UUID.randomUUID() + "/content");
		assertThat(api.statusCode()).isEqualTo(401);
		assertThat(api.headers().firstValue("Location")).isEmpty();
	}

	@Test
	void passwordLoginRotatesHardenedSessionCookieAndLogoutEndsIt() {
		String username = create("ron.flow", PASSWORD, Role.EDITOR);
		BrowserSession browser = new BrowserSession(port);

		HttpResponse<String> loginPage = browser.get("/login");
		assertThat(loginPage.statusCode()).isEqualTo(200);
		String loginToken = BrowserSession.csrfToken(loginPage.body());
		String sessionBeforeLogin = browser.cookie("JSESSIONID").orElseThrow();
		assertHardened(browser.lastSetCookie("JSESSIONID").orElseThrow());

		HttpResponse<String> login = browser.postForm("/login",
				Map.of("username", username, "password", PASSWORD, "_csrf", loginToken));
		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(browser.location(login)).isEqualTo("/");
		// Session fixation protection: the id changes on login, and the new cookie has the same flags.
		String sessionAfterLogin = browser.cookie("JSESSIONID").orElseThrow();
		assertThat(sessionAfterLogin).isNotEqualTo(sessionBeforeLogin);
		assertHardened(browser.lastSetCookie("JSESSIONID").orElseThrow());

		HttpResponse<String> home = browser.get("/");
		assertThat(home.statusCode()).isEqualTo(200);
		assertThat(home.body()).contains(username);
		assertThat(home.body()).contains("<meta name=\"_csrf\" content=\"");

		// The token from the page is accepted as a header, like the editor sends it.
		String pageToken = BrowserSession.csrfToken(home.body());
		HttpResponse<String> save = browser.putJson("/api/pages/" + UUID.randomUUID() + "/content",
				"{\"baseVersion\":0,\"title\":\"x\",\"document\":[]}", Map.of("X-CSRF-TOKEN", pageToken));
		assertThat(save.statusCode()).isEqualTo(404);
		HttpResponse<String> saveWithoutToken = browser.putJson("/api/pages/" + UUID.randomUUID() + "/content",
				"{\"baseVersion\":0,\"title\":\"x\",\"document\":[]}", Map.of());
		assertThat(saveWithoutToken.statusCode()).isEqualTo(403);
		assertThat(saveWithoutToken.body()).contains(ApiAccessDeniedHandler.CSRF_INVALID);

		HttpResponse<String> logout = browser.postForm("/logout", Map.of("_csrf", pageToken));
		assertThat(logout.statusCode()).isEqualTo(302);
		assertThat(browser.location(logout)).isEqualTo("/login?logout");

		HttpResponse<String> afterLogout = browser.get("/");
		assertThat(afterLogout.statusCode()).isEqualTo(302);
		assertThat(browser.location(afterLogout)).isEqualTo("/login");

		assertThat(users.findByUsernameIgnoreCase(username).orElseThrow().getLastLoginAt()).isNotNull();
	}

	@Test
	void loginFormItselfRequiresCsrfToken() {
		String username = create("ron.csrf", PASSWORD, Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);
		browser.get("/login");

		HttpResponse<String> login = browser.postForm("/login", Map.of("username", username, "password", PASSWORD));
		assertThat(login.statusCode()).isEqualTo(403);

		HttpResponse<String> home = browser.get("/");
		assertThat(home.statusCode()).isEqualTo(302);
	}

	@Test
	void wrongPasswordDeactivatedAndEntraOnlyAccountsAreRefused() {
		String wrong = create("ron.fout", PASSWORD, Role.VIEWER);
		String deactivated = create("ron.uit", PASSWORD, Role.VIEWER);
		jdbc.update("update app_user set active = false where username = ?", deactivated);
		String entraOnly = "ron.entra.only";
		users.save(WikiUser.create(entraOnly, "Alleen Entra", entraOnly + "@example.com", null, Role.VIEWER,
				"test", clock.instant()));

		assertRefused(wrong, "verkeerd");
		assertRefused(deactivated, PASSWORD);
		assertRefused(entraOnly, PASSWORD);
	}

	private void assertRefused(String username, String password) {
		BrowserSession browser = new BrowserSession(port);
		String token = BrowserSession.csrfToken(browser.get("/login").body());
		HttpResponse<String> login = browser.postForm("/login",
				Map.of("username", username, "password", password, "_csrf", token));
		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(browser.location(login)).isEqualTo("/login?error");
		assertThat(browser.get("/").statusCode()).isEqualTo(302);
	}

	/** Spec N-02: the session cookie is HttpOnly and Secure; Lax fits the OIDC callback (see application.yaml). */
	static void assertHardened(String setCookie) {
		assertThat(setCookie).containsIgnoringCase("HttpOnly");
		assertThat(setCookie).containsIgnoringCase("Secure");
		assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
	}

	private String create(String username, String password, Role role) {
		users.save(WikiUser.create(username, username, null, encoder.encode(password), role, "test", clock.instant()));
		return username;
	}

}
