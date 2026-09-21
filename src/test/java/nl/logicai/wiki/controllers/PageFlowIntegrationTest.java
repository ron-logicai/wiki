package nl.logicai.wiki.controllers;

import nl.logicai.wiki.TestcontainersConfiguration;
import java.util.UUID;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 2 vertical slice (spec section 11): an allowed user creates a page, enters a title and
 * one paragraph, saves, and the fixed URL returns the content from PostgreSQL inside the server HTML.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PageFlowIntegrationTest {

	private static final String PARAGRAPH = """
		[{"id":"b1","type":"paragraph","props":{},"content":[{"type":"text","text":"Eerste alinea uit PostgreSQL","styles":{}}],"children":[]}]
		""";

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorCreatesSavesAndReadsPageAtFixedUrl() throws Exception {
		UUID id = createPage("Projectpagina");

		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Projectpagina", PARAGRAPH)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.revision").value(2));

		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("<h1>Projectpagina</h1>")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("<p>Eerste alinea uit PostgreSQL</p>")));

		mvc.perform(get("/pages/{id}/history", id))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("editor")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void staleBaseVersionIsRejectedWith409() throws Exception {
		UUID id = createPage("Conflictpagina");

		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Conflictpagina", PARAGRAPH)))
			.andExpect(status().isOk());

		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Conflictpagina", "[]")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentVersion").value(1));

		mvc.perform(get("/pages/{id}", id))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Eerste alinea uit PostgreSQL")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void unsupportedContentIsRejectedWith400() throws Exception {
		UUID id = createPage("Validatie");

		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Validatie", "[{\"type\":\"table\"}]")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").exists());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void titleLongerThan200CharactersIsRejectedOnCreateAndSave() throws Exception {
		String tooLong = "t".repeat(201);
		String maxLength = "t".repeat(200);

		// The new-page form stays on the form and shows the message (spec N-05).
		mvc.perform(post("/pages").with(csrf()).param("title", tooLong))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("De titel mag maximaal 200 tekens bevatten.")));

		// Exactly 200 characters is allowed.
		UUID id = createPage(maxLength);

		// The editor save answers 400 with the same Dutch message; the stored title is untouched.
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, tooLong, PARAGRAPH)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("De titel mag maximaal 200 tekens bevatten."));
		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("<h1>" + maxLength + "</h1>")));
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerCannotCreateOrSave() throws Exception {
		mvc.perform(post("/pages").with(csrf()).param("title", "Verboden"))
			.andExpect(status().isForbidden());

		mvc.perform(put("/api/pages/{id}/content", UUID.randomUUID()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Verboden", "[]")))
			.andExpect(status().isForbidden());
	}

	@Test
	void anonymousHtmlRedirectsToLoginAndApiGets401() throws Exception {
		mvc.perform(get("/pages/{id}", UUID.randomUUID()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));

		mvc.perform(put("/api/pages/{id}/content", UUID.randomUUID()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "x", "[]")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void unknownPageIs404() throws Exception {
		mvc.perform(get("/pages/{id}", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorMovesPageAndCyclesAreRefused() throws Exception {
		UUID ouder = createPage("Ouder");
		UUID kind = createPage("Kind", ouder);
		UUID nieuweOuder = createPage("Nieuwe ouder");

		mvc.perform(get("/pages/{id}/move", kind))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Hoofdniveau")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"" + nieuweOuder + "\"")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"" + kind + "\""))));

		// Move under another page: the URL stays the same, the breadcrumb follows.
		mvc.perform(post("/pages/{id}/move", kind).with(csrf())
				.param("parentId", nieuweOuder.toString()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + kind));
		mvc.perform(get("/pages/{id}", kind))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("<li><a href=\"/pages/" + nieuweOuder + "\">Nieuwe ouder</a></li>")));

		// A parent cannot go under its own (former or current) descendant, nor under itself.
		mvc.perform(post("/pages/{id}/move", nieuweOuder).with(csrf())
				.param("parentId", kind.toString()).param("baseVersion", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("eigen subpagina")));
		mvc.perform(post("/pages/{id}/move", ouder).with(csrf())
				.param("parentId", ouder.toString()).param("baseVersion", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("zichzelf")));

		// The version the user saw is checked: the child is at version 1 after its move.
		mvc.perform(post("/pages/{id}/move", kind).with(csrf())
				.param("parentId", "").param("baseVersion", "0"))
			.andExpect(status().isConflict());
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerCannotMove() throws Exception {
		mvc.perform(post("/pages/{id}/move", UUID.randomUUID()).with(csrf())
				.param("parentId", "").param("baseVersion", "0"))
			.andExpect(status().isForbidden());
	}

	private UUID createPage(String title, UUID parentId) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title)
				.param("parentId", parentId.toString()))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = result.getResponse().getRedirectedUrl();
		return UUID.fromString(location.substring("/pages/".length()));
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = result.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("/pages/");
		return UUID.fromString(location.substring("/pages/".length()));
	}

	private static String saveBody(long baseVersion, String title, String document) {
		return "{\"baseVersion\":" + baseVersion + ",\"title\":\"" + title + "\",\"document\":" + document + "}";
	}

}
