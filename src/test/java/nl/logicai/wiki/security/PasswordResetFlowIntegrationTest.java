package nl.logicai.wiki.security;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.mail.ResetMailSender;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Wachtwoord vergeten" over real HTTP (spec F-01 extension, N-02): the request form, the mailed link
 * (captured by a test sender instead of SMTP), the reset form with CSRF, single use, expiry, the cooldown
 * and the refusal of unknown, deactivated and Entra-only accounts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, PasswordResetFlowIntegrationTest.CapturedMail.class})
class PasswordResetFlowIntegrationTest {

	private static final String PASSWORD = "wachtwoord-1";
	private static final String NEW_PASSWORD = "nieuw-wachtwoord-2";

	/** Replaces the SMTP/log sender so the test can read the link. */
	@TestConfiguration(proxyBeanMethods = false)
	static class CapturedMail {

		record Sent(String username, String link) {
		}

		static final List<Sent> SENT = new CopyOnWriteArrayList<>();

		@Bean
		@Primary
		ResetMailSender capturingResetMailSender() {
			return (user, link, validFor) -> SENT.add(new Sent(user.getUsername(), link));
		}

	}

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

	@BeforeEach
	void clearMail() {
		CapturedMail.SENT.clear();
	}

	@Test
	void unknownDeactivatedAndEntraOnlyAddressesAreRefusedWithoutMail() {
		WikiUser inactive = create("reset.inactief", "reset.inactief@example.com", PASSWORD, Role.VIEWER);
		inactive.setActive(false);
		users.save(inactive);
		create("reset.entra", "reset.entra@example.com", null, Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);

		for (String email : List.of("niemand@example.com", "reset.inactief@example.com", "reset.entra@example.com")) {
			HttpResponse<String> response = request(browser, email);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("Uw account bestaat niet");
		}
		assertThat(CapturedMail.SENT).isEmpty();
	}

	@Test
	void resetLinkSetsNewPasswordOnce() {
		create("reset.flow", "Reset.Flow@example.com", PASSWORD, Role.EDITOR);
		BrowserSession browser = new BrowserSession(port);

		HttpResponse<String> requested = request(browser, "reset.flow@EXAMPLE.com");
		assertThat(requested.statusCode()).isEqualTo(302);
		assertThat(browser.location(requested)).isEqualTo("/wachtwoord-vergeten?verzonden");
		HttpResponse<String> verzonden = browser.get("/wachtwoord-vergeten?verzonden");
		assertThat(verzonden.body()).contains("Wij hebben een mail gestuurd").doesNotContain("name=\"email\"");

		assertThat(CapturedMail.SENT).hasSize(1);
		assertThat(CapturedMail.SENT.getFirst().username()).isEqualTo("reset.flow");
		String link = pathOf(CapturedMail.SENT.getFirst().link());
		assertThat(link).startsWith("/wachtwoord-herstellen?token=");

		HttpResponse<String> form = browser.get(link);
		assertThat(form.statusCode()).isEqualTo(200);
		assertThat(form.body()).contains("name=\"token\"").contains("name=\"passwordRepeat\"").doesNotContain("ongeldig");
		String token = link.substring(link.indexOf('=') + 1);

		HttpResponse<String> saved = browser.postForm("/wachtwoord-herstellen", Map.of(
				"token", token, "password", NEW_PASSWORD, "passwordRepeat", NEW_PASSWORD,
				"_csrf", BrowserSession.csrfToken(form.body())));
		assertThat(saved.statusCode()).isEqualTo(302);
		assertThat(browser.location(saved)).isEqualTo("/login?reset");

		assertThat(login(browser, "reset.flow", NEW_PASSWORD)).isEqualTo("/");
		browser.forgetCookies();
		assertThat(login(browser, "reset.flow", PASSWORD)).isEqualTo("/login?error");

		// Single use: the same link is now refused, also on POST.
		HttpResponse<String> again = browser.get(link);
		assertThat(again.statusCode()).isEqualTo(200);
		assertThat(again.body()).contains("ongeldig of verlopen").doesNotContain("name=\"passwordRepeat\"");
		HttpResponse<String> reuse = browser.postForm("/wachtwoord-herstellen", Map.of(
				"token", token, "password", "nog-een-ander-3", "passwordRepeat", "nog-een-ander-3",
				"_csrf", BrowserSession.csrfToken(again.body())));
		assertThat(reuse.statusCode()).isEqualTo(200);
		assertThat(reuse.body()).contains("ongeldig of verlopen");

		assertThat(auditCount("user.password.reset-request", "reset.flow")).isEqualTo(1);
		assertThat(auditCount("user.password.reset", "reset.flow")).isEqualTo(1);
	}

	@Test
	void mismatchOrShortPasswordKeepsTokenUsable() {
		create("reset.typo", "reset.typo@example.com", PASSWORD, Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);
		request(browser, "reset.typo@example.com");
		String link = pathOf(CapturedMail.SENT.getFirst().link());
		String token = link.substring(link.indexOf('=') + 1);
		HttpResponse<String> form = browser.get(link);
		String csrf = BrowserSession.csrfToken(form.body());

		HttpResponse<String> mismatch = browser.postForm("/wachtwoord-herstellen", Map.of(
				"token", token, "password", NEW_PASSWORD, "passwordRepeat", "anders-anders", "_csrf", csrf));
		assertThat(mismatch.statusCode()).isEqualTo(200);
		assertThat(mismatch.body()).contains("niet gelijk").contains("name=\"token\"");

		HttpResponse<String> tooShort = browser.postForm("/wachtwoord-herstellen", Map.of(
				"token", token, "password", "kort", "passwordRepeat", "kort", "_csrf", csrf));
		assertThat(tooShort.statusCode()).isEqualTo(200);
		assertThat(tooShort.body()).contains("minimaal 8");

		HttpResponse<String> saved = browser.postForm("/wachtwoord-herstellen", Map.of(
				"token", token, "password", NEW_PASSWORD, "passwordRepeat", NEW_PASSWORD, "_csrf", csrf));
		assertThat(saved.statusCode()).isEqualTo(302);
		assertThat(browser.location(saved)).isEqualTo("/login?reset");
	}

	@Test
	void expiredTokenIsRefused() {
		create("reset.verlopen", "reset.verlopen@example.com", PASSWORD, Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);
		request(browser, "reset.verlopen@example.com");
		String link = pathOf(CapturedMail.SENT.getFirst().link());

		jdbc.update("update password_reset_token set expires_at = now() - interval '1 minute'"
				+ " where user_id = (select id from app_user where username = 'reset.verlopen')");

		HttpResponse<String> form = browser.get(link);
		assertThat(form.statusCode()).isEqualTo(200);
		assertThat(form.body()).contains("ongeldig of verlopen");
	}

	@Test
	void secondRequestWithinCooldownSendsNothingAndLaterRequestReplacesTheLink() {
		create("reset.herhaald", "reset.herhaald@example.com", PASSWORD, Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);

		HttpResponse<String> first = request(browser, "reset.herhaald@example.com");
		assertThat(first.statusCode()).isEqualTo(302);
		HttpResponse<String> second = request(browser, "reset.herhaald@example.com");
		assertThat(second.statusCode()).isEqualTo(302);
		assertThat(browser.location(second)).isEqualTo("/wachtwoord-vergeten?verzonden");
		assertThat(CapturedMail.SENT).hasSize(1);
		String firstLink = pathOf(CapturedMail.SENT.getFirst().link());

		jdbc.update("update password_reset_token set created_at = created_at - interval '10 minutes'"
				+ " where user_id = (select id from app_user where username = 'reset.herhaald')");
		request(browser, "reset.herhaald@example.com");
		assertThat(CapturedMail.SENT).hasSize(2);
		String secondLink = pathOf(CapturedMail.SENT.get(1).link());
		assertThat(secondLink).isNotEqualTo(firstLink);

		assertThat(browser.get(firstLink).body()).contains("ongeldig of verlopen");
		assertThat(browser.get(secondLink).body()).contains("name=\"token\"");
	}

	@Test
	void resetFormsRequireCsrf() {
		create("reset.csrf", "reset.csrf@example.com", PASSWORD, Role.VIEWER);
		BrowserSession browser = new BrowserSession(port);
		browser.get("/wachtwoord-vergeten");

		HttpResponse<String> request = browser.postForm("/wachtwoord-vergeten", Map.of("email", "reset.csrf@example.com"));
		assertThat(request.statusCode()).isEqualTo(403);
		assertThat(CapturedMail.SENT).isEmpty();

		HttpResponse<String> reset = browser.postForm("/wachtwoord-herstellen", Map.of(
				"token", "x", "password", NEW_PASSWORD, "passwordRepeat", NEW_PASSWORD));
		assertThat(reset.statusCode()).isEqualTo(403);
	}

	/** GETs the form for a session and CSRF token, then posts the address. */
	private HttpResponse<String> request(BrowserSession browser, String email) {
		HttpResponse<String> form = browser.get("/wachtwoord-vergeten");
		assertThat(form.statusCode()).isEqualTo(200);
		return browser.postForm("/wachtwoord-vergeten", Map.of("email", email, "_csrf", BrowserSession.csrfToken(form.body())));
	}

	/** Logs in through the form and returns where the browser is sent. */
	private String login(BrowserSession browser, String username, String password) {
		HttpResponse<String> loginPage = browser.get("/login");
		HttpResponse<String> response = browser.postForm("/login", Map.of(
				"username", username, "password", password, "_csrf", BrowserSession.csrfToken(loginPage.body())));
		assertThat(response.statusCode()).isEqualTo(302);
		return browser.location(response);
	}

	/** The mailed link uses wiki.base-url (port 8080); the test server runs on a random port, so keep path and query only. */
	private static String pathOf(String link) {
		URI uri = URI.create(link);
		assertThat(uri.getRawQuery()).isNotNull();
		return uri.getRawPath() + "?" + uri.getRawQuery();
	}

	private int auditCount(String action, String actor) {
		Integer count = jdbc.queryForObject("select count(*) from audit_event where action = ? and actor = ?",
				Integer.class, action, actor);
		return count == null ? 0 : count;
	}

	private WikiUser create(String username, String email, String password, Role role) {
		String hash = password == null ? null : encoder.encode(password);
		return users.save(WikiUser.create(username, username, email == null ? null : email.toLowerCase(), hash,
				role, "test", clock.instant()));
	}

}
