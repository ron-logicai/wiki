package nl.logicai.wiki.controllers;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tags through the real database (spec F-14): unique ignoring case, attach/detach, rename, delete. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TagFlowIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorAttachesAndDetachesTagsUniqueIgnoringCase() throws Exception {
		UUID id = createPage("Getagde pagina");

		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "  Shopify  "))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "shopify"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attributeCount(0));

		String page = body(get("/pages/{id}", id));
		assertThat(page).containsOnlyOnce("class=\"label label--tag\"").contains("Shopify");
		assertThat(page).contains("name=\"name\"");

		String overview = body(get("/tags"));
		assertThat(overview).containsOnlyOnce("Shopify").contains(">1</span> pagina's");
		assertThat(overview).doesNotContain("/rename");

		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "   "))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", "Een tagnaam is verplicht."));

		UUID tagId = tagIdFrom(page);
		mvc.perform(post("/pages/{id}/tags/{tagId}/remove", id, tagId).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(body(get("/pages/{id}", id))).doesNotContain("label--tag");
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMIN")
	void adminRenamesAndDeletesTagsWithoutLosingPages() throws Exception {
		UUID id = createPage("Beheerde pagina");
		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "Concept")).andExpect(status().is3xxRedirection());
		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "Klaar")).andExpect(status().is3xxRedirection());
		String overview = body(get("/tags"));
		assertThat(overview).contains("/rename").contains("/delete");
		UUID concept = tagIdFrom(overview, "Concept");

		mvc.perform(post("/tags/{id}/rename", concept).with(csrf()).param("name", "klaar"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("bestaat al")));

		mvc.perform(post("/tags/{id}/rename", concept).with(csrf()).param("name", "Definitief"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("melding", "Tag hernoemd."));
		assertThat(body(get("/pages/{id}", id))).contains("Definitief").doesNotContain("Concept");

		mvc.perform(post("/tags/{id}/delete", concept).with(csrf()))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Beheerde pagina</h1>")))
			.andExpect(content().string(not(containsString("Definitief"))))
			.andExpect(content().string(containsString("Klaar")));
		assertThat(body(get("/tags"))).doesNotContain("Definitief");
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorCannotRenameOrDeleteTags() throws Exception {
		mvc.perform(post("/tags/{id}/rename", UUID.randomUUID()).with(csrf()).param("name", "x"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/tags/{id}/delete", UUID.randomUUID()).with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerSeesTagsButCannotChangeThem() throws Exception {
		mvc.perform(get("/tags")).andExpect(status().isOk());
		mvc.perform(post("/pages/{id}/tags", UUID.randomUUID()).with(csrf()).param("name", "x"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/pages/{id}/tags/{tagId}/remove", UUID.randomUUID(), UUID.randomUUID()).with(csrf()))
			.andExpect(status().isForbidden());
	}

	private String body(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

	/** First tag id in a page's remove-form action. */
	private static UUID tagIdFrom(String pageHtml) {
		Matcher m = Pattern.compile("/tags/([0-9a-f-]{36})/remove").matcher(pageHtml);
		assertThat(m.find()).isTrue();
		return UUID.fromString(m.group(1));
	}

	/** Tag id of the row with the given name on the overview page. */
	private static UUID tagIdFrom(String overviewHtml, String name) {
		int at = overviewHtml.indexOf(">" + name + "</span>");
		assertThat(at).isGreaterThan(0);
		Matcher m = Pattern.compile("/tags/([0-9a-f-]{36})/rename").matcher(overviewHtml);
		assertThat(m.find(at)).isTrue();
		return UUID.fromString(m.group(1));
	}

}
