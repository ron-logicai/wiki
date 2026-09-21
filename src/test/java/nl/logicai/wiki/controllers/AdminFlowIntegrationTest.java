package nl.logicai.wiki.controllers;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin page is for admins only (spec section 1, route /admin). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminFlowIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void adminSeesOverviewAndUserRowLinksToAdminPage() throws Exception {
		mvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Beheer</h1>")))
			.andExpect(content().string(containsString("actieve pagina's")))
			.andExpect(content().string(containsString("href=\"/admin\"")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorIsRefusedAndSeesNoLink() throws Exception {
		mvc.perform(get("/admin")).andExpect(status().isForbidden());
		mvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("href=\"/admin\""))));
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerIsRefused() throws Exception {
		mvc.perform(get("/admin")).andExpect(status().isForbidden());
	}

}
