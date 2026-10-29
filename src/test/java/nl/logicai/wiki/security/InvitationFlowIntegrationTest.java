package nl.logicai.wiki.security;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Map;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.mail.CapturedMail;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inviting a new user over real HTTP (spec F-01: no public registration, N-02): the admin form sends a
 * mail (captured by a test sender instead of SMTP), the link opens /accountaanmaken, the invitee chooses
 * username and password with CSRF, the link is single use and expires, a newer invitation replaces the
 * older link, and non-admins cannot invite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, CapturedMail.class})
class InvitationFlowIntegrationTest {

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

	@BeforeEach
	void clearMail() {
		CapturedMail.SENT.clear();
		CapturedMail.FAIL_WITH = null;
	}

	/** A refusing SMTP server (wrong password, SMTP AUTH off) must not leave an "invitation sent" behind. */
	@Test
	void mailFailureIsShownAndLeavesNoInvitation() {
		create("invite.admin7", "invite.admin7@example.com", PASSWORD, Role.ADMIN);
		BrowserSession admin = new BrowserSession(port);
		login(admin, "invite.admin7", PASSWORD);

		CapturedMail.FAIL_WITH = "535 5.7.139 Authentication unsuccessful";
		HttpResponse<String> failed = invite(admin, "onbereikbaar@example.com", "VIEWER");
		assertThat(failed.statusCode()).isEqualTo(200);
		assertThat(failed.body()).contains("De uitnodiging is niet verstuurd").contains("Authentication unsuccessful")
			.doesNotContain("Uitnodiging verstuurd naar");
		Integer rows = jdbc.queryForObject("select count(*) from user_invitation where email = ?", Integer.class,
				"onbereikbaar@example.com");
		assertThat(rows).isZero();
		assertThat(auditCount("user.invite", "invite.admin7")).isZero();
		assertThat(CapturedMail.SENT).isEmpty();

		// After fixing the mail settings the same invitation simply works.
		CapturedMail.FAIL_WITH = null;
		HttpResponse<String> sent = invite(admin, "onbereikbaar@example.com", "VIEWER");
		assertThat(sent.statusCode()).isEqualTo(302);
		assertThat(CapturedMail.SENT).hasSize(1);
		assertThat(auditCount("user.invite", "invite.admin7")).isEqualTo(1);
	}

	@Test
	void adminInvitesAndInviteeCreatesAccountOnce() {
		create("invite.admin", "invite.admin@example.com", PASSWORD, Role.ADMIN);
		BrowserSession admin = new BrowserSession(port);
		assertThat(login(admin, "invite.admin", PASSWORD)).isEqualTo("/");

		HttpResponse<String> invited = invite(admin, "Nieuwe.Collega@example.com", "EDITOR");
		assertThat(invited.statusCode()).isEqualTo(302);
		assertThat(admin.location(invited)).isEqualTo("/admin/users");
		HttpResponse<String> list = admin.get("/admin/users");
		// The form asks for e-mail and role only; the invitee is not in the list until the link is used.
		assertThat(list.body()).contains("Uitnodiging verstuurd naar “nieuwe.collega@example.com”")
			.contains("name=\"email\"").doesNotContain("name=\"username\"").doesNotContain("nieuwe.collega@example.com</");

		assertThat(CapturedMail.SENT).hasSize(1);
		assertThat(CapturedMail.SENT.getFirst().soort()).isEqualTo("uitnodiging");
		assertThat(CapturedMail.SENT.getFirst().aan()).isEqualTo("nieuwe.collega@example.com");
		String link = pathOf(CapturedMail.SENT.getFirst().link());
		assertThat(link).startsWith("/accountaanmaken?token=");
		String token = link.substring(link.indexOf('=') + 1);

		// The invitee has no session: the page is open, but only with the token from the mail.
		BrowserSession invitee = new BrowserSession(port);
		HttpResponse<String> form = invitee.get(link);
		assertThat(form.statusCode()).isEqualTo(200);
		assertThat(form.body()).contains("name=\"token\"").contains("name=\"displayName\"").contains("name=\"username\"")
			.contains("name=\"passwordRepeat\"").contains("nieuwe.collega@example.com").contains("Editor")
			.contains("uitgenodigd door <strong>invite.admin</strong>").doesNotContain("ongeldig");

		HttpResponse<String> created = invitee.postForm("/accountaanmaken", Map.of(
				"token", token, "displayName", " Nieuwe Collega ", "username", " Nieuwe.Collega ",
				"password", PASSWORD, "passwordRepeat", PASSWORD, "_csrf", BrowserSession.csrfToken(form.body())));
		assertThat(created.statusCode()).isEqualTo(302);
		assertThat(invitee.location(created)).isEqualTo("/login?aangemaakt");
		assertThat(invitee.get("/login?aangemaakt").body()).contains("Je account is aangemaakt");

		assertThat(login(invitee, "nieuwe.collega", PASSWORD)).isEqualTo("/");
		WikiUser user = users.findByUsernameIgnoreCase("nieuwe.collega").orElseThrow();
		assertThat(user.getDisplayName()).isEqualTo("Nieuwe Collega");
		assertThat(user.getEmail()).isEqualTo("nieuwe.collega@example.com");
		assertThat(user.getRole()).isEqualTo(Role.EDITOR);
		assertThat(user.getCreatedBy()).isEqualTo("invite.admin");
		assertThat(user.isActive()).isTrue();

		// Single use: the same link is now refused, also on POST.
		invitee.forgetCookies();
		HttpResponse<String> again = invitee.get(link);
		assertThat(again.statusCode()).isEqualTo(200);
		assertThat(again.body()).contains("ongeldig of verlopen").doesNotContain("name=\"username\"");
		HttpResponse<String> reuse = post(invitee, token, "Tweede Poging", "tweede.poging", PASSWORD, PASSWORD,
				BrowserSession.csrfToken(again.body()));
		assertThat(reuse.statusCode()).isEqualTo(200);
		assertThat(reuse.body()).contains("ongeldig of verlopen");
		assertThat(users.findByUsernameIgnoreCase("tweede.poging")).isEmpty();

		assertThat(auditCount("user.invite", "invite.admin")).isEqualTo(1);
		assertThat(auditCount("user.create", "nieuwe.collega")).isEqualTo(1);
	}

	@Test
	void takenUsernameOrBadPasswordKeepsInvitationUsable() {
		create("invite.admin2", "invite.admin2@example.com", PASSWORD, Role.ADMIN);
		create("bezet", "bezet@example.com", PASSWORD, Role.VIEWER);
		BrowserSession admin = new BrowserSession(port);
		login(admin, "invite.admin2", PASSWORD);
		invite(admin, "typo@example.com", "VIEWER");
		String link = pathOf(CapturedMail.SENT.getFirst().link());
		String token = link.substring(link.indexOf('=') + 1);

		BrowserSession invitee = new BrowserSession(port);
		String csrf = BrowserSession.csrfToken(invitee.get(link).body());

		HttpResponse<String> taken = post(invitee, token, "Ty Po", "BEZET", PASSWORD, PASSWORD, csrf);
		assertThat(taken.statusCode()).isEqualTo(200);
		assertThat(taken.body()).contains("bestaat al").contains("name=\"token\"").contains("value=\"BEZET\"")
			.contains("value=\"Ty Po\"");

		HttpResponse<String> noName = post(invitee, token, "   ", "typo", PASSWORD, PASSWORD, csrf);
		assertThat(noName.body()).contains("Naam:").contains("name=\"token\"");

		HttpResponse<String> badChars = post(invitee, token, "Ty Po", "Bad Name!", PASSWORD, PASSWORD, csrf);
		assertThat(badChars.body()).contains("alleen letters, cijfers");

		HttpResponse<String> mismatch = post(invitee, token, "Ty Po", "typo", PASSWORD, "anders-anders", csrf);
		assertThat(mismatch.body()).contains("niet gelijk");

		HttpResponse<String> tooShort = post(invitee, token, "Ty Po", "typo", "kort", "kort", csrf);
		assertThat(tooShort.body()).contains("minimaal 8");

		HttpResponse<String> saved = post(invitee, token, "Ty Po", "typo", PASSWORD, PASSWORD, csrf);
		assertThat(saved.statusCode()).isEqualTo(302);
		assertThat(invitee.location(saved)).isEqualTo("/login?aangemaakt");
		assertThat(users.findByUsernameIgnoreCase("typo")).map(WikiUser::getDisplayName).contains("Ty Po");
	}

	@Test
	void expiredInvitationIsRefused() {
		create("invite.admin3", "invite.admin3@example.com", PASSWORD, Role.ADMIN);
		BrowserSession admin = new BrowserSession(port);
		login(admin, "invite.admin3", PASSWORD);
		invite(admin, "verlopen@example.com", "VIEWER");
		String link = pathOf(CapturedMail.SENT.getFirst().link());

		jdbc.update("update user_invitation set expires_at = now() - interval '1 minute' where email = ?", "verlopen@example.com");

		HttpResponse<String> form = new BrowserSession(port).get(link);
		assertThat(form.statusCode()).isEqualTo(200);
		assertThat(form.body()).contains("ongeldig of verlopen").doesNotContain("name=\"username\"");
	}

	@Test
	void unknownOrMissingTokenShowsNoForm() {
		BrowserSession browser = new BrowserSession(port);
		for (String path : new String[] {"/accountaanmaken", "/accountaanmaken?token=", "/accountaanmaken?token=x"}) {
			HttpResponse<String> response = browser.get(path);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("ongeldig of verlopen").doesNotContain("name=\"username\"");
		}
	}

	@Test
	void newInvitationReplacesOlderLinkForSameAddress() {
		create("invite.admin4", "invite.admin4@example.com", PASSWORD, Role.ADMIN);
		BrowserSession admin = new BrowserSession(port);
		login(admin, "invite.admin4", PASSWORD);
		invite(admin, "herhaald@example.com", "VIEWER");
		invite(admin, "HERHAALD@example.com", "EDITOR");
		assertThat(CapturedMail.SENT).hasSize(2);
		String firstLink = pathOf(CapturedMail.SENT.get(0).link());
		String secondLink = pathOf(CapturedMail.SENT.get(1).link());
		assertThat(secondLink).isNotEqualTo(firstLink);

		BrowserSession invitee = new BrowserSession(port);
		assertThat(invitee.get(firstLink).body()).contains("ongeldig of verlopen");
		assertThat(invitee.get(secondLink).body()).contains("name=\"token\"").contains("Editor");
	}

	@Test
	void existingAddressCannotBeInvited() {
		create("invite.admin5", "invite.admin5@example.com", PASSWORD, Role.ADMIN);
		create("al.lid", "al.lid@example.com", PASSWORD, Role.VIEWER);
		BrowserSession admin = new BrowserSession(port);
		login(admin, "invite.admin5", PASSWORD);

		HttpResponse<String> refused = invite(admin, "Al.Lid@example.com", "VIEWER");
		assertThat(refused.statusCode()).isEqualTo(200);
		assertThat(refused.body()).contains("al in gebruik");
		assertThat(CapturedMail.SENT).isEmpty();
	}

	@Test
	void formsRequireCsrf() {
		create("invite.admin6", "invite.admin6@example.com", PASSWORD, Role.ADMIN);
		BrowserSession admin = new BrowserSession(port);
		login(admin, "invite.admin6", PASSWORD);

		HttpResponse<String> invited = admin.postForm("/admin/users", Map.of("email", "csrf@example.com", "role", "VIEWER"));
		assertThat(invited.statusCode()).isEqualTo(403);
		assertThat(CapturedMail.SENT).isEmpty();

		BrowserSession invitee = new BrowserSession(port);
		invitee.get("/accountaanmaken");
		HttpResponse<String> created = invitee.postForm("/accountaanmaken", Map.of(
				"token", "x", "displayName", "Csrf", "username", "csrf", "password", PASSWORD, "passwordRepeat", PASSWORD));
		assertThat(created.statusCode()).isEqualTo(403);
	}

	@Test
	void editorCannotInvite() {
		create("invite.editor", "invite.editor@example.com", PASSWORD, Role.EDITOR);
		BrowserSession editor = new BrowserSession(port);
		assertThat(login(editor, "invite.editor", PASSWORD)).isEqualTo("/");

		assertThat(editor.get("/admin/users").statusCode()).isEqualTo(403);
		HttpResponse<String> home = editor.get("/");
		HttpResponse<String> invited = editor.postForm("/admin/users", Map.of(
				"email", "sluip@example.com", "role", "ADMIN", "_csrf", BrowserSession.csrfToken(home.body())));
		assertThat(invited.statusCode()).isEqualTo(403);
		assertThat(CapturedMail.SENT).isEmpty();
	}

	/** GETs the admin page for a CSRF token, then posts the invitation. */
	private HttpResponse<String> invite(BrowserSession admin, String email, String role) {
		HttpResponse<String> page = admin.get("/admin/users");
		assertThat(page.statusCode()).isEqualTo(200);
		return admin.postForm("/admin/users", Map.of("email", email, "role", role, "_csrf", BrowserSession.csrfToken(page.body())));
	}

	private static HttpResponse<String> post(BrowserSession browser, String token, String displayName, String username,
			String password, String passwordRepeat, String csrf) {
		return browser.postForm("/accountaanmaken", Map.of("token", token, "displayName", displayName,
				"username", username, "password", password, "passwordRepeat", passwordRepeat, "_csrf", csrf));
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
