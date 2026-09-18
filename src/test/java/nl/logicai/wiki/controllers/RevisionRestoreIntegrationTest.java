package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Restoring an earlier revision as a new revision (spec F-11): the history never loses a version. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RevisionRestoreIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void restoringAnOldRevisionAppendsANewOneAndKeepsTheRest() throws Exception {
		UUID id = createPage("Herstelbaar");
		save(id, 0, "Herstelbaar", paragraph("Eerste tekst"));   // revision 2, version 1
		save(id, 1, "Nieuwe titel", paragraph("Tweede tekst"));  // revision 3, version 2
		UUID tweede = revisionId(id, 2);

		mvc.perform(get("/pages/{id}/history/{rev}", id, tweede))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Deze versie terugzetten")));

		mvc.perform(post("/pages/{id}/history/{rev}/restore", id, tweede).with(csrf()).param("baseVersion", "2"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id))
			.andExpect(flash().attribute("melding", containsString("Versie 2 is teruggezet als versie 4")));

		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Herstelbaar</h1>")))
			.andExpect(content().string(containsString("<p>Eerste tekst</p>")))
			.andExpect(content().string(not(containsString("Tweede tekst"))));

		// Revision 3 is still there and unchanged; revision 4 is the restored copy.
		Integer aantal = jdbc.queryForObject("select count(*) from page_revision where page_id = ?", Integer.class, id);
		assertThat(aantal).isEqualTo(4);
		mvc.perform(get("/pages/{id}/history/{rev}", id, revisionId(id, 3)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Nieuwe titel</h1>")))
			.andExpect(content().string(containsString("<p>Tweede tekst</p>")));
		mvc.perform(get("/pages/{id}/history/{rev}", id, revisionId(id, 4)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Dit is de actuele versie.")))
			.andExpect(content().string(not(containsString("Deze versie terugzetten"))));

		Integer events = jdbc.queryForObject(
			"select count(*) from audit_event where page_id = ? and action = 'page.restore-revision'", Integer.class, id);
		assertThat(events).isEqualTo(1);
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void restoringTheCurrentStateChangesNothing() throws Exception {
		UUID id = createPage("Onveranderd");
		save(id, 0, "Onveranderd", paragraph("Tekst"));       // revision 2
		save(id, 1, "Onveranderd", paragraph("Andere tekst")); // revision 3
		save(id, 2, "Onveranderd", paragraph("Tekst"));       // revision 4, same content as revision 2

		mvc.perform(post("/pages/{id}/history/{rev}/restore", id, revisionId(id, 2)).with(csrf()).param("baseVersion", "3"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("melding", containsString("niets gewijzigd")));

		Integer aantal = jdbc.queryForObject("select count(*) from page_revision where page_id = ?", Integer.class, id);
		assertThat(aantal).isEqualTo(4);
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void staleVersionIsRefusedAndNothingChanges() throws Exception {
		UUID id = createPage("Verouderd");
		save(id, 0, "Verouderd", paragraph("Tekst"));
		UUID eerste = revisionId(id, 1);

		mvc.perform(post("/pages/{id}/history/{rev}/restore", id, eerste).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id + "/history/" + eerste))
			.andExpect(flash().attribute("fout", containsString("intussen gewijzigd")));

		mvc.perform(get("/pages/{id}", id))
			.andExpect(content().string(containsString("<p>Tekst</p>")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void revisionOfAnotherPageIsNotFound() throws Exception {
		UUID een = createPage("Een");
		UUID ander = createPage("Ander");

		mvc.perform(post("/pages/{id}/history/{rev}/restore", een, revisionId(ander, 1)).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().isNotFound());
	}

	@Test
	void viewerSeesHistoryWithoutRestoreButtonsAndCannotRestore() throws Exception {
		MvcResult aangemaakt = mvc.perform(post("/pages").with(csrf()).with(user("editor").roles("EDITOR")).param("title", "Alleen lezen"))
			.andExpect(status().is3xxRedirection()).andReturn();
		UUID id = UUID.fromString(aangemaakt.getResponse().getRedirectedUrl().substring("/pages/".length()));
		var viewer = user("viewer").roles("VIEWER");

		mvc.perform(get("/pages/{id}/history", id).with(viewer))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Terugzetten"))));
		mvc.perform(post("/pages/{id}/history/{rev}/restore", id, revisionId(id, 1)).with(csrf()).with(viewer).param("baseVersion", "0"))
			.andExpect(status().isForbidden());
	}

	private UUID revisionId(UUID pageId, int number) {
		return jdbc.queryForObject("select id from page_revision where page_id = ? and revision_number = ?",
			UUID.class, pageId, number);
	}

	private void save(UUID id, long baseVersion, String title, String document) throws Exception {
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":" + baseVersion + ",\"title\":\"" + title + "\",\"document\":" + document + "}"))
			.andExpect(status().isOk());
	}

	private static String paragraph(String text) {
		return "[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{},\"content\":[{\"type\":\"text\",\"text\":\"" + text
			+ "\",\"styles\":{}}],\"children\":[]}]";
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection()).andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

}
