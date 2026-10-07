package nl.logicai.wiki.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eigenaar, status en "laatst gecontroleerd" op een pagina (spec U-07, U-08) door de echte database:
 * zichtbaar voor iedereen, alleen te wijzigen door een Editor, met versiecontrole zoals elke wijziging.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PagePropertiesIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WikiUserRepository users;

	@Autowired
	private AuditEventRepository audit;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorSetsOwnerAndStatusWithVersionCheck() throws Exception {
		UUID id = createPage("Pagina met eigenschappen");
		WikiUser owner = activeUser("Ron Testeigenaar");

		String fresh = body(get("/pages/{id}", id));
		assertThat(fresh).contains("status--concept").contains("Nog niet gecontroleerd");
		assertThat(fresh).contains("name=\"owner\"").contains(owner.getDisplayName());
		assertThat(audit.findByPageIdOrderByOccurredAtDesc(id)).isEmpty();

		mvc.perform(post("/pages/{id}/properties", id).with(csrf())
				.param("owner", owner.getUsername()).param("status", "ACTUEEL").param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id))
			.andExpect(flash().attribute("melding", "Eigenschappen opgeslagen."));

		String page = body(get("/pages/{id}", id));
		assertThat(page).contains("status--actueel").doesNotContain("status--concept");
		assertThat(page).contains("name=\"baseVersion\" value=\"1\"");
		assertThat(page).contains("value=\"" + owner.getUsername() + "\" selected");

		// Verouderde versie: geweigerd, niets gewijzigd (spec section 7).
		mvc.perform(post("/pages/{id}/properties", id).with(csrf())
				.param("owner", "").param("status", "VEROUDERD").param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("intussen gewijzigd")));
		assertThat(body(get("/pages/{id}", id))).contains("status--actueel");

		// Onbekende eigenaar: geweigerd.
		mvc.perform(post("/pages/{id}/properties", id).with(csrf())
				.param("owner", "bestaat-niet").param("status", "ACTUEEL").param("baseVersion", "1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", "Kies een actieve gebruiker als eigenaar."));

		List<AuditEvent> events = audit.findByPageIdOrderByOccurredAtDesc(id);
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().getAction()).isEqualTo(AuditEvent.PROPERTIES);
		assertThat(events.getFirst().getDetails()).contains(owner.getUsername()).contains("ACTUEEL");
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorMarksPageAsReviewed() throws Exception {
		UUID id = createPage("Te controleren pagina");

		mvc.perform(post("/pages/{id}/review", id).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("melding", "Pagina gemarkeerd als gecontroleerd."));

		String page = body(get("/pages/{id}", id));
		assertThat(page).contains("Gecontroleerd door").contains("editor").doesNotContain("Nog niet gecontroleerd");
		assertThat(page).contains("name=\"baseVersion\" value=\"1\"");

		mvc.perform(post("/pages/{id}/review", id).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("intussen gewijzigd")));

		List<AuditEvent> events = audit.findByPageIdOrderByOccurredAtDesc(id);
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().getAction()).isEqualTo(AuditEvent.REVIEW);
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerSeesPropertiesButCannotChangeThem() throws Exception {
		mvc.perform(post("/pages/{id}/properties", UUID.randomUUID()).with(csrf())
				.param("status", "ACTUEEL").param("baseVersion", "0"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/pages/{id}/review", UUID.randomUUID()).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void viewerGetsLabelsWithoutForms() throws Exception {
		UUID id = createPage("Alleen lezen");
		String page = mvc.perform(get("/pages/{id}", id).with(user("viewer").roles("VIEWER")))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(page).contains("Eigenaar:").contains("status--concept").contains("Nog niet gecontroleerd");
		assertThat(page).doesNotContain("name=\"owner\"").doesNotContain("/review");
	}

	private WikiUser activeUser(String displayName) {
		String username = "eigenaar-" + UUID.randomUUID().toString().substring(0, 8);
		return users.save(WikiUser.create(username, displayName, null, null, Role.EDITOR, "test", Instant.now()));
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

}
