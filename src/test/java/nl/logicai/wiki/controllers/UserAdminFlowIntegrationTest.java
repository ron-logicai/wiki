package nl.logicai.wiki.controllers;

import java.time.Clock;
import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User and role management through the real database (spec section 1, F-01, F-02, acceptance A-07). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserAdminFlowIntegrationTest {

	/** An admin that is not in app_user (passes through ActiveUserFilter), for tests without @WithMockUser. */
	private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private WikiUserRepository users;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private Clock clock;

	/** The admin form only invites (spec F-01, no public registration); the account itself is made on /accountaanmaken. */
	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void adminInvitesUserAndSeesAuditEntryWithoutAnAccountYet() throws Exception {
		mvc.perform(post("/admin/users").with(csrf())
				.param("email", "  Nieuwe.Admin@example.com ")
				.param("role", "ADMIN"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/admin/users"))
			.andExpect(flash().attribute("melding", containsString("Uitnodiging verstuurd naar “nieuwe.admin@example.com”")));

		Integer accounts = jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class,
				"nieuwe.admin@example.com");
		assertThat(accounts).isZero();
		Integer invitations = jdbc.queryForObject("select count(*) from user_invitation where email = ? and role = 'ADMIN'",
				Integer.class, "nieuwe.admin@example.com");
		assertThat(invitations).isEqualTo(1);

		mvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Gebruiker uitgenodigd")))
			.andExpect(content().string(containsString("nieuwe.admin@example.com als Admin")));
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void invalidFormIsRenderedAgainWithMessage() throws Exception {
		mvc.perform(post("/admin/users").with(csrf())
				.param("email", "geen-adres")
				.param("role", "VIEWER"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("fout"))
			.andExpect(content().string(containsString("E-mailadres is ongeldig.")));

		create("dubbel", "VIEWER", "dubbel@example.com");
		mvc.perform(post("/admin/users").with(csrf())
				.param("email", "DUBBEL@example.com")
				.param("role", "VIEWER"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("al in gebruik")));

		mvc.perform(post("/admin/users").with(csrf())
				.param("email", "zonder.rol@example.com")
				.param("role", ""))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Kies een rol.")));
	}

	// The two login tests run without @WithMockUser: a mock principal would shadow the session that
	// authenticated()/unauthenticated() read after the real form login, so the admin is given per request.

	@Test
	void createdUserCanLogInWithTheirPassword() throws Exception {
		create("ron1234", "ADMIN");

		mvc.perform(formLogin().user("ron1234@example.com").password("geheim123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"))
			.andExpect(authenticated().withUsername("ron1234").withRoles("ADMIN"));
		// The e-mail lookup ignores case, so "Ron1234@Example.com" logs in too.
		mvc.perform(formLogin().user("Ron1234@Example.com").password("geheim123"))
			.andExpect(authenticated().withUsername("ron1234"));
		mvc.perform(formLogin().user("ron1234@example.com").password("verkeerd!"))
			.andExpect(unauthenticated());
		// The internal username is not a login once the account has an e-mail address.
		mvc.perform(formLogin().user("ron1234").password("geheim123"))
			.andExpect(unauthenticated());
	}

	/** Accounts from before the e-mail login have no address: they keep their username until an admin sets one. */
	@Test
	void adminSetsEmailAndLegacyAccountSwitchesFromUsernameToEmailLogin() throws Exception {
		UUID id = create("oud", "EDITOR", null);
		create("ander", "VIEWER");

		mvc.perform(formLogin().user("oud").password("geheim123"))
			.andExpect(authenticated().withUsername("oud"));

		mvc.perform(post("/admin/users/{id}/email", id).with(csrf()).with(ADMIN).param("email", "ander@example.com"))
			.andExpect(flash().attribute("fout", containsString("al in gebruik")));
		mvc.perform(post("/admin/users/{id}/email", id).with(csrf()).with(ADMIN).param("email", "geen-adres"))
			.andExpect(flash().attribute("fout", containsString("ongeldig")));
		mvc.perform(post("/admin/users/{id}/email", id).with(csrf()).with(ADMIN).param("email", " Oud@Example.com "))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/admin/users"))
			.andExpect(flash().attribute("melding", containsString("oud@example.com")));

		mvc.perform(formLogin().user("oud").password("geheim123")).andExpect(unauthenticated());
		mvc.perform(formLogin().user("oud@example.com").password("geheim123"))
			.andExpect(authenticated().withUsername("oud"));
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'user.email' and details = 'oud: geen naar oud@example.com'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void adminSetsNewPasswordAndUserLogsInWithIt() throws Exception {
		UUID id = create("vergeten", "EDITOR");

		mvc.perform(post("/admin/users/{id}/password", id).with(csrf()).with(ADMIN)
				.param("password", "nieuw-ww-1").param("passwordRepeat", "anders-ww"))
			.andExpect(flash().attribute("fout", containsString("niet gelijk")));
		mvc.perform(post("/admin/users/{id}/password", id).with(csrf()).with(ADMIN)
				.param("password", "kort").param("passwordRepeat", "kort"))
			.andExpect(flash().attribute("fout", containsString("minimaal 8")));

		mvc.perform(post("/admin/users/{id}/password", id).with(csrf()).with(ADMIN)
				.param("password", "nieuw-ww-1").param("passwordRepeat", "nieuw-ww-1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/admin/users"))
			.andExpect(flash().attribute("melding", containsString("ingesteld")));

		mvc.perform(formLogin().user("vergeten@example.com").password("geheim123")).andExpect(unauthenticated());
		mvc.perform(formLogin().user("vergeten@example.com").password("nieuw-ww-1"))
			.andExpect(authenticated().withUsername("vergeten").withRoles("EDITOR"));

		mvc.perform(get("/admin").with(ADMIN))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Wachtwoord ingesteld")));

		// An Entra-only account (no password hash) gets form login once a password is set.
		UUID entra = users.save(WikiUser.create("entra.only", "entra.only", "entra.only@example.com", null,
				Role.VIEWER, "test", clock.instant())).getId();
		mvc.perform(formLogin().user("entra.only@example.com").password("nieuw-ww-1")).andExpect(unauthenticated());
		mvc.perform(post("/admin/users/{id}/password", entra).with(csrf()).with(ADMIN)
				.param("password", "nieuw-ww-1").param("passwordRepeat", "nieuw-ww-1"))
			.andExpect(flash().attributeExists("melding"));
		mvc.perform(formLogin().user("entra.only@example.com").password("nieuw-ww-1"))
			.andExpect(authenticated().withUsername("entra.only"));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorCannotSetPasswords() throws Exception {
		mvc.perform(post("/admin/users/{id}/password", UUID.randomUUID()).with(csrf())
				.param("password", "nieuw-ww-1").param("passwordRepeat", "nieuw-ww-1"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorIsRefused() throws Exception {
		mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
		mvc.perform(post("/admin/users").with(csrf())
				.param("email", "sluip@example.com").param("role", "ADMIN"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void deactivatedUserIsRefusedOnNextRequestEvenWithSession() throws Exception {
		UUID id = create("tijdelijk", "EDITOR");

		mvc.perform(get("/").with(user("tijdelijk").roles("EDITOR"))).andExpect(status().isOk());

		mvc.perform(post("/admin/users/{id}/active", id).with(csrf()).param("active", "false"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attributeExists("melding"));

		mvc.perform(get("/").with(user("tijdelijk").roles("EDITOR")))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?deactivated"));
		mvc.perform(get("/api/pages/{id}/content", UUID.randomUUID()).with(user("tijdelijk").roles("EDITOR")))
			.andExpect(status().isUnauthorized());

		mvc.perform(post("/admin/users/{id}/active", id).with(csrf()).param("active", "true"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/").with(user("tijdelijk").roles("EDITOR"))).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void roleChangeAppliesOnNextRequest() throws Exception {
		UUID id = create("promotie", "VIEWER");

		// The stored role wins over what the session claims, in both directions.
		mvc.perform(get("/admin").with(user("promotie").roles("ADMIN"))).andExpect(status().isForbidden());

		mvc.perform(post("/admin/users/{id}/role", id).with(csrf()).param("role", "ADMIN"))
			.andExpect(status().is3xxRedirection());

		mvc.perform(get("/admin").with(user("promotie").roles("VIEWER"))).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "hoofdbeheerder", roles = "ADMIN")
	void adminCannotLockThemselvesOutOrRemoveTheLastAdmin() throws Exception {
		jdbc.update("update app_user set active = false where role = 'ADMIN'");
		UUID self = create("hoofdbeheerder", "ADMIN");

		mvc.perform(post("/admin/users/{id}/active", self).with(csrf()).param("active", "false"))
			.andExpect(flash().attribute("fout", "Je kunt jezelf niet deactiveren."));
		mvc.perform(post("/admin/users/{id}/role", self).with(csrf()).param("role", "EDITOR"))
			.andExpect(flash().attribute("fout", "Je kunt je eigen beheerdersrol niet afnemen."));

		// Another admin (not in app_user, so a mock) may not demote or deactivate the last active admin.
		mvc.perform(post("/admin/users/{id}/role", self).with(csrf()).param("role", "VIEWER")
				.with(user("andere").roles("ADMIN")))
			.andExpect(flash().attribute("fout", containsString("laatste actieve beheerder")));
		mvc.perform(post("/admin/users/{id}/active", self).with(csrf()).param("active", "false")
				.with(user("andere").roles("ADMIN")))
			.andExpect(flash().attribute("fout", containsString("laatste actieve beheerder")));

		// With a second active admin the change is allowed.
		UUID other = create("tweede.beheerder", "ADMIN");
		mvc.perform(post("/admin/users/{id}/role", self).with(csrf()).param("role", "VIEWER")
				.with(user("tweede.beheerder").roles("ADMIN")))
			.andExpect(flash().attributeExists("melding"));
		mvc.perform(post("/admin/users/{id}/active", other).with(csrf()).param("active", "false")
				.with(user("andere").roles("ADMIN")))
			.andExpect(flash().attribute("fout", containsString("laatste actieve beheerder")));
	}

	/** Verwijderen: the row and its reset tokens go, and an open session is ended on the next request. */
	@Test
	void adminDeletesUserAndTheyAreLoggedOutImmediately() throws Exception {
		UUID id = create("weg", "EDITOR", "weg@example.com");
		jdbc.update("insert into password_reset_token (id, user_id, token_hash, created_at, expires_at) values (?, ?, ?, now(), now() + interval '1 hour')",
				UUID.randomUUID(), id, "a".repeat(64));

		// A real form login, so the session carries WebAuthenticationDetails like in the browser.
		MockHttpSession session = (MockHttpSession) mvc.perform(formLogin().user("weg@example.com").password("geheim123"))
			.andExpect(authenticated().withUsername("weg"))
			.andReturn().getRequest().getSession(false);
		mvc.perform(get("/").session(session)).andExpect(status().isOk());

		mvc.perform(post("/admin/users/{id}/delete", id).with(csrf()).with(ADMIN))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/admin/users"))
			.andExpect(flash().attribute("melding", "“weg” is verwijderd."));

		mvc.perform(get("/").session(session))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?verwijderd"));
		mvc.perform(get("/api/pages/{id}/content", UUID.randomUUID()).session(session))
			.andExpect(status().isUnauthorized());
		mvc.perform(formLogin().user("weg@example.com").password("geheim123")).andExpect(unauthenticated());

		assertThat(jdbc.queryForObject("select count(*) from app_user where username = 'weg'", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from password_reset_token where user_id = ?", Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'user.delete' and details = 'weg'", Integer.class))
			.isEqualTo(1);

		mvc.perform(get("/admin").with(ADMIN))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Gebruiker verwijderd")));
		mvc.perform(get("/login?verwijderd"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Je account is verwijderd.")));
	}

	@Test
	@WithMockUser(username = "enige.beheerder", roles = "ADMIN")
	void adminCannotDeleteThemselvesOrTheLastAdmin() throws Exception {
		jdbc.update("update app_user set active = false where role = 'ADMIN'");
		UUID self = create("enige.beheerder", "ADMIN");

		mvc.perform(post("/admin/users/{id}/delete", self).with(csrf()))
			.andExpect(flash().attribute("fout", "Je kunt jezelf niet verwijderen."));
		mvc.perform(post("/admin/users/{id}/delete", self).with(csrf()).with(user("andere").roles("ADMIN")))
			.andExpect(flash().attribute("fout", containsString("laatste actieve beheerder")));
		assertThat(users.findById(self)).isPresent();

		// The own-row rule and the last-admin rule are the only guards: with a second admin it goes through.
		UUID other = create("derde.beheerder", "ADMIN");
		mvc.perform(post("/admin/users/{id}/delete", other).with(csrf()))
			.andExpect(flash().attributeExists("melding"));
		assertThat(users.findById(other)).isEmpty();
	}

	/** A user with password "geheim123" and login {@code <username>@example.com}, straight into the table. */
	private UUID create(String username, String role) {
		return create(username, role, username + "@example.com");
	}

	private UUID create(String username, String role, String email) {
		return users.save(WikiUser.create(username, username, email, encoder.encode("geheim123"),
				Role.valueOf(role), "test", clock.instant())).getId();
	}

}
