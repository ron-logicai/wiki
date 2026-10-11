package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void adminCreatesUserAndSeesItListedWithAuditEntry() throws Exception {
		mvc.perform(post("/admin/users").with(csrf())
				.param("username", "  Nieuwe.Admin ")
				.param("email", "nieuwe.admin@example.com")
				.param("password", "geheim123")
				.param("passwordRepeat", "geheim123")
				.param("role", "ADMIN"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/admin/users"))
			.andExpect(flash().attributeExists("melding"));

		mvc.perform(get("/admin/users"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("nieuwe.admin")))
			.andExpect(content().string(containsString("nieuwe.admin@example.com")))
			.andExpect(content().string(containsString("Wachtwoord")));

		mvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Gebruiker aangemaakt")))
			.andExpect(content().string(containsString("nieuwe.admin als Admin")));
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void invalidFormIsRenderedAgainWithMessage() throws Exception {
		mvc.perform(post("/admin/users").with(csrf())
				.param("username", "zonder.login")
				.param("role", "VIEWER"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("fout"))
			.andExpect(content().string(containsString("Geef een wachtwoord, of een e-mailadres")));

		create("dubbel", "VIEWER");
		mvc.perform(post("/admin/users").with(csrf())
				.param("username", "DUBBEL")
				.param("password", "geheim123")
				.param("passwordRepeat", "geheim123")
				.param("role", "VIEWER"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("bestaat al")));

		// A typo in one of the two password fields is refused, so the new user is never locked out.
		mvc.perform(post("/admin/users").with(csrf())
				.param("username", "typefout")
				.param("password", "geheim123")
				.param("passwordRepeat", "geheim124")
				.param("role", "VIEWER"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("niet gelijk")));
	}

	// The two login tests run without @WithMockUser: a mock principal would shadow the session that
	// authenticated()/unauthenticated() read after the real form login, so the admin is given per request.

	@Test
	void createdUserCanLogInWithTheirPassword() throws Exception {
		create("ron1234", "ADMIN", ADMIN);

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
		UUID id = create("vergeten", "EDITOR", ADMIN);

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

		// An Entra-only account gets form login once a password is set.
		mvc.perform(post("/admin/users").with(csrf()).with(ADMIN)
				.param("username", "entra.only")
				.param("email", "entra.only@example.com").param("role", "VIEWER"))
			.andExpect(status().is3xxRedirection());
		UUID entra = jdbc.queryForObject("select id from app_user where username = ?", UUID.class, "entra.only");
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
				.param("username", "sluip").param("password", "geheim123").param("passwordRepeat", "geheim123").param("role", "ADMIN"))
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

	private UUID create(String username, String role, RequestPostProcessor... as) throws Exception {
		MockHttpServletRequestBuilder request = post("/admin/users").with(csrf())
				.param("username", username)
				.param("password", "geheim123")
				.param("passwordRepeat", "geheim123")
				.param("role", role);
		for (RequestPostProcessor as1 : as) {
			request.with(as1);
		}
		mvc.perform(request)
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attributeExists("melding"));
		return jdbc.queryForObject("select id from app_user where username = ?", UUID.class, username);
	}

}
