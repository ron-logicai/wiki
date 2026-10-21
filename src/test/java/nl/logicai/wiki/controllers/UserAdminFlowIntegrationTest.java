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

		mvc.perform(formLogin().user("ron1234").password("geheim123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"))
			.andExpect(authenticated().withUsername("ron1234").withRoles("ADMIN"));
		// Username lookup ignores case, so "Ron1234" logs in too.
		mvc.perform(formLogin().user("Ron1234").password("geheim123"))
			.andExpect(authenticated().withUsername("ron1234"));
		mvc.perform(formLogin().user("ron1234").password("verkeerd!"))
			.andExpect(unauthenticated());
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

		mvc.perform(formLogin().user("vergeten").password("geheim123")).andExpect(unauthenticated());
		mvc.perform(formLogin().user("vergeten").password("nieuw-ww-1"))
			.andExpect(authenticated().withUsername("vergeten").withRoles("EDITOR"));

		mvc.perform(get("/admin").with(ADMIN))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Wachtwoord ingesteld")));

		// An Entra-only account (no password hash) gets form login once a password is set.
		UUID entra = users.save(WikiUser.create("entra.only", "entra.only", "entra.only@example.com", null,
				Role.VIEWER, "test", clock.instant())).getId();
		mvc.perform(formLogin().user("entra.only").password("nieuw-ww-1")).andExpect(unauthenticated());
		mvc.perform(post("/admin/users/{id}/password", entra).with(csrf()).with(ADMIN)
				.param("password", "nieuw-ww-1").param("passwordRepeat", "nieuw-ww-1"))
			.andExpect(flash().attributeExists("melding"));
		mvc.perform(formLogin().user("entra.only").password("nieuw-ww-1"))
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

	/** A user with password "geheim123", straight into the table: the admin form no longer creates accounts. */
	private UUID create(String username, String role) {
		return create(username, role, null);
	}

	private UUID create(String username, String role, String email) {
		return users.save(WikiUser.create(username, username, email, encoder.encode("geheim123"),
				Role.valueOf(role), "test", clock.instant())).getId();
	}

}
