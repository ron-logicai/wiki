package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Templates through the real database (spec F-15, acceptance A-01 and A-09). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TemplateFlowIntegrationTest {

	/** Seeded by V4__templates.sql. */
	private static final UUID WERKINSTRUCTIE = UUID.fromString("11111111-1111-4111-8111-111111111103");

	private static final String NEW_TEMPLATE_DOC = """
		[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Nieuwe kop","styles":{}}]}]
		""";

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void pageFromTemplateGetsIndependentCopy() throws Exception {
		mvc.perform(get("/templates"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Projectinformatie")))
			.andExpect(content().string(containsString("Developer-onboarding")))
			.andExpect(content().string(containsString("Werkinstructie")));

		mvc.perform(get("/pages/new"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Leeg")))
			.andExpect(content().string(containsString("value=\"" + WERKINSTRUCTIE + "\"")));

		UUID pageId = createPage("Pull request maken", WERKINSTRUCTIE);
		mvc.perform(get("/pages/{id}", pageId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Pull request maken</h1>")))
			.andExpect(content().string(containsString("<h2>Doel</h2>")))
			.andExpect(content().string(containsString("<h2>Gerelateerde links</h2>")));

		// A-09: change the template afterwards; the existing page does not change.
		MvcResult edit = mvc.perform(get("/templates/{id}/edit", WERKINSTRUCTIE))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/api/templates/" + WERKINSTRUCTIE + "/content")))
			.andReturn();
		long version = versionFrom(edit.getResponse().getContentAsString());
		mvc.perform(put("/api/templates/{id}/content", WERKINSTRUCTIE).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(version, "Werkinstructie v2", NEW_TEMPLATE_DOC)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(version + 1));

		mvc.perform(get("/pages/{id}", pageId))
			.andExpect(content().string(containsString("<h2>Doel</h2>")))
			.andExpect(content().string(not(containsString("Nieuwe kop"))));

		// A new page now starts from the changed template.
		UUID later = createPage("Latere pagina", WERKINSTRUCTIE);
		mvc.perform(get("/pages/{id}", later))
			.andExpect(content().string(containsString("<h2>Nieuwe kop</h2>")))
			.andExpect(content().string(not(containsString("<h2>Doel</h2>"))));

		// Stale version on the template is a 409, like on pages.
		mvc.perform(put("/api/templates/{id}/content", WERKINSTRUCTIE).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(version, "Werkinstructie v3", NEW_TEMPLATE_DOC)))
			.andExpect(status().isConflict());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorCreatesAndDeletesTemplate() throws Exception {
		MvcResult created = mvc.perform(post("/templates").with(csrf())
				.param("title", "Vergadernotitie").param("description", "Agenda, besluiten, acties"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = created.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("/templates/").endsWith("/edit");
		UUID id = UUID.fromString(location.substring("/templates/".length(), location.length() - "/edit".length()));

		UUID pageId = createPage("Weekstart", id);
		mvc.perform(get("/pages/{id}", pageId)).andExpect(status().isOk());

		mvc.perform(post("/templates/{id}/delete", id).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/templates"));
		mvc.perform(get("/templates"))
			.andExpect(content().string(not(containsString("Vergadernotitie"))));
		mvc.perform(get("/pages/{id}", pageId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Weekstart</h1>")));
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerCanReadButNotChangeTemplates() throws Exception {
		mvc.perform(get("/templates"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("/templates/" + WERKINSTRUCTIE + "/edit"))));
		mvc.perform(post("/templates").with(csrf()).param("title", "x"))
			.andExpect(status().isForbidden());
		mvc.perform(put("/api/templates/{id}/content", WERKINSTRUCTIE).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "x", "[]")))
			.andExpect(status().isForbidden());
		mvc.perform(post("/templates/{id}/delete", WERKINSTRUCTIE).with(csrf()))
			.andExpect(status().isForbidden());
	}

	private UUID createPage(String title, UUID templateId) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf())
				.param("title", title).param("templateId", templateId.toString()))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

	private static long versionFrom(String editHtml) {
		int at = editHtml.indexOf("data-base-version=\"");
		assertThat(at).isGreaterThan(0);
		int start = at + "data-base-version=\"".length();
		return Long.parseLong(editHtml.substring(start, editHtml.indexOf('"', start)));
	}

	private static String saveBody(long baseVersion, String title, String document) {
		return "{\"baseVersion\":" + baseVersion + ",\"title\":\"" + title + "\",\"document\":" + document + "}";
	}

}
