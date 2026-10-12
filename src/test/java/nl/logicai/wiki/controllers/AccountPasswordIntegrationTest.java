package nl.logicai.wiki.controllers;

import java.time.Clock;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A logged-in user changes their own password on /account/wachtwoord (backlog item under spec F-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountPasswordIntegrationTest {

	private static final String PASSWORD = "huidig-wachtwoord";
	private static final String NEW_PASSWORD = "nieuw-wachtwoord";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WikiUserRepository users;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private Clock clock;

	@Test
	void anonymousVisitorIsSentToLogin() throws Exception {
		mvc.perform(get("/account/wachtwoord"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	void userChangesOwnPasswordAfterWrongAttempts() throws Exception {
		create("ron.account", PASSWORD);
		RequestPostProcessor me = user("ron.account").roles("VIEWER");

		mvc.perform(get("/account/wachtwoord").with(me))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"currentPassword\"")));

		mvc.perform(post("/account/wachtwoord").with(me).with(csrf())
				.param("currentPassword", "fout-wachtwoord")
				.param("password", NEW_PASSWORD)
				.param("passwordRepeat", NEW_PASSWORD))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("klopt niet")));

		mvc.perform(post("/account/wachtwoord").with(me).with(csrf())
				.param("currentPassword", PASSWORD)
				.param("password", NEW_PASSWORD)
				.param("passwordRepeat", "anders-anders"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("niet gelijk")));

		mvc.perform(post("/account/wachtwoord").with(me).with(csrf())
				.param("currentPassword", PASSWORD)
				.param("password", NEW_PASSWORD)
				.param("passwordRepeat", NEW_PASSWORD))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/account/wachtwoord"))
			.andExpect(flash().attributeExists("melding"));

		mvc.perform(formLogin().user("ron.account").password(NEW_PASSWORD)).andExpect(authenticated());
		mvc.perform(formLogin().user("ron.account").password(PASSWORD)).andExpect(unauthenticated());

		mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Wachtwoord gewijzigd")));
	}

	@Test
	void entraOnlyUserHasNoPasswordToChange() throws Exception {
		create("ron.entra", null);
		RequestPostProcessor me = user("ron.entra").roles("VIEWER");

		mvc.perform(get("/account/wachtwoord").with(me))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Microsoft")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"currentPassword\""))));

		mvc.perform(post("/account/wachtwoord").with(me).with(csrf())
				.param("currentPassword", "x")
				.param("password", NEW_PASSWORD)
				.param("passwordRepeat", NEW_PASSWORD))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("geen wiki-wachtwoord")));
	}

	private void create(String username, String password) {
		String hash = password == null ? null : encoder.encode(password);
		users.save(WikiUser.create(username, username, username + "@example.com", hash, Role.VIEWER, "test", clock.instant()));
	}

}
