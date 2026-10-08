package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

/** PDF export through the real database (extra next to spec F-17; same rules as /pages/{id}/export.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PdfExportIntegrationTest {

	private static final String DOCUMENT = """
		[{"id":"b1","type":"heading","props":{"level":2},"content":[{"type":"text","text":"Stappen","styles":{}}],"children":[]},
		 {"id":"b2","type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Klaar","styles":{"bold":true}}],"children":[]},
		 {"id":"b3","type":"paragraph","content":[
		   {"type":"text","text":"Zie ","styles":{}},
		   {"type":"link","href":"/pages/00000000-0000-0000-0000-000000000001","content":[{"type":"text","text":"de andere pagina","styles":{}}]}
		 ],"children":[]}]
		""";

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void exportsAnActivePageAsPdfDownload() throws Exception {
		UUID id = createPage("Deploy handleiding");
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":0,\"title\":\"Deploy handleiding\",\"document\":" + DOCUMENT + "}"))
			.andExpect(status().isOk());
		mvc.perform(post("/pages/{id}/tags", id).with(csrf()).param("name", "ops"))
			.andExpect(status().is3xxRedirection());

		// The read view offers both formats in the export menu.
		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/pages/" + id + "/export.md\"")))
			.andExpect(content().string(containsString("href=\"/pages/" + id + "/export.pdf\"")));

		MvcResult result = mvc.perform(get("/pages/{id}/export.pdf", id))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andExpect(header().string("Content-Disposition", containsString("deploy-handleiding.pdf")))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andReturn();
		byte[] pdf = result.getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		String text = text(pdf);
		assertThat(text).contains("Deploy handleiding").contains("Versie 2").contains("ops");
		assertThat(text).contains("Stappen").contains("[x]").contains("Klaar").contains("de andere pagina");
		assertThat(text).contains("http://localhost/pages/" + id);
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerMayExportPdf() throws Exception {
		UUID id = createPage("Alleen lezen");
		MvcResult result = mvc.perform(get("/pages/{id}/export.pdf", id))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(text(result.getResponse().getContentAsByteArray())).contains("Alleen lezen");
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void trashedPageIsNotExported() throws Exception {
		UUID id = createPage("Weg ermee");
		mvc.perform(post("/pages/{id}/delete", id).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/pages/{id}/export.pdf", id)).andExpect(status().isNotFound());
		mvc.perform(get("/pages/{id}/export.pdf", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void exportRequiresLogin() throws Exception {
		mvc.perform(get("/pages/{id}/export.pdf", UUID.randomUUID()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", containsString("/login")));
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).with(user("editor").roles("EDITOR")).param("title", title))
			.andExpect(status().is3xxRedirection()).andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

	private static String text(byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(doc);
		}
	}

}
