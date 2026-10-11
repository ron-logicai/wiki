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
			.andExpect(content().string(containsString("<h1>Overzicht</h1>")))
			.andExpect(content().string(containsString("actieve pagina's")))
			.andExpect(content().string(containsString("href=\"/admin\"")));
	}

	/** The Tags and Templates tabs stay inside the admin page: /admin/tags and /admin/templates. */
	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void tagsAndTemplatesTabsStayOnAdminPage() throws Exception {
		mvc.perform(get("/admin"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/admin/tags\"")))
			.andExpect(content().string(containsString("href=\"/admin/templates\"")));

		mvc.perform(get("/admin/tags"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Tags</h1>")))
			.andExpect(content().string(containsString("class=\"tab tab--aan\" href=\"/admin/tags\"")))
			.andExpect(content().string(containsString("<li><a href=\"/admin\">Beheer</a></li>")))
			.andExpect(content().string(containsString("action=\"/admin/tags\"")));

		mvc.perform(get("/admin/templates"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Templates</h1>")))
			.andExpect(content().string(containsString("class=\"tab tab--aan\" href=\"/admin/templates\"")))
			.andExpect(content().string(containsString("action=\"/admin/templates\"")))
			.andExpect(content().string(containsString("action=\"/admin/templates/")));

		// The sidebar entries still point at the public overviews.
		mvc.perform(get("/tags"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("class=\"tab tab--aan\""))))
			.andExpect(content().string(containsString("action=\"/tags\"")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorIsRefusedAndSeesNoLink() throws Exception {
		mvc.perform(get("/admin")).andExpect(status().isForbidden());
		mvc.perform(get("/admin/tags")).andExpect(status().isForbidden());
		mvc.perform(get("/admin/templates")).andExpect(status().isForbidden());
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
