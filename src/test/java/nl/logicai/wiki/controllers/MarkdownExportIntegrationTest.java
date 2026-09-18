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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Markdown export through the real database (spec F-17, route /pages/{id}/export.md from section 5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MarkdownExportIntegrationTest {

	private static final String DOCUMENT = """
		[{"id":"b1","type":"heading","props":{"level":2},"content":[{"type":"text","text":"Stappen","styles":{}}],"children":[]},
		 {"id":"b2","type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Klaar","styles":{"bold":true}}],"children":[]},
		 {"id":"b3","type":"checkListItem","props":{"checked":false},"content":[
		   {"type":"text","text":"Zie ","styles":{}},
		   {"type":"link","href":"/pages/00000000-0000-0000-0000-000000000001","content":[{"type":"text","text":"de andere pagina","styles":{}}]}
		 ],"children":[]}]
		""";

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void exportsAnActivePageAsMarkdownDownload() throws Exception {
		UUID id = createPage("Deploy handleiding");
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":0,\"title\":\"Deploy handleiding\",\"document\":" + DOCUMENT + "}"))
			.andExpect(status().isOk());
		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "ops"))
			.andExpect(status().is3xxRedirection());

		// The read view offers the button (viewers see it too: the export needs no editor role).
		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/pages/" + id + "/export.md\"")));

		MvcResult result = mvc.perform(get("/pages/{id}/export.md", id))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andExpect(header().string("Content-Disposition", containsString("deploy-handleiding.md")))
			.andReturn();
		String md = result.getResponse().getContentAsString();
		assertThat(md).startsWith("---\ntitle: \"Deploy handleiding\"\ntags:\n  - \"ops\"\nsource: http://localhost/pages/" + id + "\nversion: 2\n");
		assertThat(md).contains("# Deploy handleiding\n\n## Stappen\n\n"
			+ "- [x] **Klaar**\n"
			+ "- [ ] Zie [de andere pagina](http://localhost/pages/00000000-0000-0000-0000-000000000001)\n");
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerMayExportButNotEdit() throws Exception {
		UUID id = createPage("Alleen lezen");
		mvc.perform(get("/pages/{id}/export.md", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("# Alleen lezen")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void trashedPageIsNotExported() throws Exception {
		UUID id = createPage("Weg ermee");
		mvc.perform(post("/pages/{id}/delete", id).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/pages/{id}/export.md", id)).andExpect(status().isNotFound());
		mvc.perform(get("/pages/{id}/export.md", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void exportRequiresLogin() throws Exception {
		mvc.perform(get("/pages/{id}/export.md", UUID.randomUUID()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", containsString("/login")));
	}

	private UUID createPage(String title) throws Exception {
		// Pages are always created by an editor; the test user only reads when it is a viewer.
		MvcResult result = mvc.perform(post("/pages").with(csrf()).with(user("editor").roles("EDITOR")).param("title", title))
			.andExpect(status().is3xxRedirection()).andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

}
