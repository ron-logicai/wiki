package nl.logicai.wiki.security;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec N-02 and acceptance A-07: CSRF protection stays active on every mutation, for HTML forms and
 * for the editor's fetch requests alike. A request without the token is refused before any service
 * code runs; a request with the token in the header (how the editor sends it) is accepted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CsrfProtectionIntegrationTest {

	private static final String EMPTY_DOCUMENT = "[]";

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void formPostWithoutTokenIsRefused() throws Exception {
		mvc.perform(post("/pages").param("title", "Zonder token"))
			.andExpect(status().isForbidden());

		UUID id = createPage("Met token");
		mvc.perform(post("/pages/{id}/delete", id).param("baseVersion", "0"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/pages/{id}", id)).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void fetchSaveWithoutTokenIsRefusedWithJsonBody() throws Exception {
		UUID id = createPage("Editorpagina");

		mvc.perform(put("/api/pages/{id}/content", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Gewijzigd zonder token")))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.error").value(ApiAccessDeniedHandler.CSRF_INVALID));

		// A wrong token is refused just like a missing one.
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf().useInvalidToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Gewijzigd met fout token")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value(ApiAccessDeniedHandler.CSRF_INVALID));

		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Editorpagina")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void fetchSaveWithTokenInHeaderIsAccepted() throws Exception {
		UUID id = createPage("Headerpagina");

		mvc.perform(put("/api/pages/{id}/content", id).with(csrf().asHeader())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Opgeslagen via header")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void uploadWithoutTokenIsRefused() throws Exception {
		mvc.perform(multipart("/api/attachments")
				.file(new MockMultipartFile("file", "leeg.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'})))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value(ApiAccessDeniedHandler.CSRF_INVALID));
	}

	@Test
	void fetchSaveWithoutSessionIsUnauthorizedNotForbidden() throws Exception {
		// An expired session loses its CSRF token too; the editor must hear 401 so it shows "log opnieuw in".
		mvc.perform(put("/api/pages/{id}/content", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Zonder sessie")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value(ApiAccessDeniedHandler.SESSION_EXPIRED));

		mvc.perform(put("/api/pages/{id}/content", UUID.randomUUID()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Zonder sessie")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerWithTokenGetsRightsMessage() throws Exception {
		mvc.perform(put("/api/pages/{id}/content", UUID.randomUUID()).with(csrf().asHeader())
				.contentType(MediaType.APPLICATION_JSON)
				.content(saveBody(0, "Verboden")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value(ApiAccessDeniedHandler.NO_PERMISSION));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editPageRendersTokenAndHeaderNameForTheEditor() throws Exception {
		UUID id = createPage("Meta-tags");
		mvc.perform(get("/pages/{id}/edit", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<meta name=\"_csrf\" content=\"")))
			.andExpect(content().string(containsString("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\"")));
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = result.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("/pages/");
		return UUID.fromString(location.substring("/pages/".length()));
	}

	private static String saveBody(long baseVersion, String title) {
		return "{\"baseVersion\":" + baseVersion + ",\"title\":\"" + title + "\",\"document\":" + EMPTY_DOCUMENT + "}";
	}

}
