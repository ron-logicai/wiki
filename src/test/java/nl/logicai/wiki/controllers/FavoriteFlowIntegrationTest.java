package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Personal favorites through the real database (spec U-03). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FavoriteFlowIntegrationTest {

	private static final RequestPostProcessor VIEWER = user("viewer").roles("VIEWER");
	private static final RequestPostProcessor EDITOR = user("editor").roles("EDITOR");

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void viewerTogglesAFavoriteAndSeesItInTheSidebar() throws Exception {
		String marker = "favoriet" + UUID.randomUUID().toString().substring(0, 8);
		UUID id = createPage(marker, null);

		// Before: the star in the tree is empty and the Favorieten section is empty.
		assertThat(favorietenOf("/pages/" + id, VIEWER)).contains("Nog geen favorieten").doesNotContain(marker);
		assertThat(boomOf("/pages/" + id, VIEWER)).contains("/pages/" + id + "/favorite").doesNotContain("boom__ster--aan");

		// Star on: back to the same path, star filled, page listed under Favorieten.
		mvc.perform(post("/pages/{id}/favorite", id).with(VIEWER).with(csrf()).param("terug", "/pages/" + id))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
		assertThat(favorietenOf("/pages/" + id, VIEWER)).contains(marker).contains("boom__ster--aan");
		assertThat(boomOf("/pages/" + id, VIEWER)).contains("boom__ster--aan");

		// Star off again.
		mvc.perform(post("/pages/{id}/favorite", id).with(VIEWER).with(csrf()).param("terug", "/pages/" + id))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
		assertThat(favorietenOf("/pages/" + id, VIEWER)).contains("Nog geen favorieten").doesNotContain(marker);
		assertThat(boomOf("/pages/" + id, VIEWER)).doesNotContain("boom__ster--aan");
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void favoritesArePerUser() throws Exception {
		String marker = "eigen" + UUID.randomUUID().toString().substring(0, 8);
		UUID id = createPage(marker, null);

		mvc.perform(post("/pages/{id}/favorite", id).with(VIEWER).with(csrf()).param("terug", "/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/"));

		assertThat(favorietenOf("/", VIEWER)).contains(marker);
		assertThat(favorietenOf("/", EDITOR)).doesNotContain(marker);
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void pageInTheTrashIsHiddenFromFavoritesAndComesBackAfterRestore() throws Exception {
		String marker = "prullenbak" + UUID.randomUUID().toString().substring(0, 8);
		UUID id = createPage(marker, null);

		mvc.perform(post("/pages/{id}/favorite", id).with(csrf()).param("terug", "/pages/" + id))
			.andExpect(status().is3xxRedirection());
		assertThat(favorietenOf("/", EDITOR)).contains(marker);

		mvc.perform(post("/pages/{id}/delete", id).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection());
		assertThat(favorietenOf("/", EDITOR)).doesNotContain(marker);

		// A page in the trash cannot be starred either.
		mvc.perform(post("/pages/{id}/favorite", id).with(csrf()).param("terug", "/"))
			.andExpect(status().isNotFound());

		mvc.perform(post("/trash/{id}/restore", id).with(csrf()))
			.andExpect(status().is3xxRedirection());
		assertThat(favorietenOf("/", EDITOR)).contains(marker);
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void unsafeReturnPathFallsBackToThePage() throws Exception {
		UUID id = createPage("Terugpad", null);

		mvc.perform(post("/pages/{id}/favorite", id).with(csrf()).param("terug", "https://example.org/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
		mvc.perform(post("/pages/{id}/favorite", id).with(csrf()).param("terug", "//example.org/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
		mvc.perform(post("/pages/{id}/favorite", id).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void unknownPageGives404() throws Exception {
		mvc.perform(post("/pages/{id}/favorite", UUID.randomUUID()).with(csrf()).param("terug", "/"))
			.andExpect(status().isNotFound());
	}

	/** The sidebar between the "Favorieten" and "Ruimtes" headings. */
	private String favorietenOf(String path, RequestPostProcessor als) throws Exception {
		String zij = zijbalkOf(path, als);
		return zij.substring(zij.indexOf(">Favorieten<"), zij.indexOf(">Ruimtes<"));
	}

	/** The sidebar from the "Ruimtes" heading to the end of the nav. */
	private String boomOf(String path, RequestPostProcessor als) throws Exception {
		String zij = zijbalkOf(path, als);
		return zij.substring(zij.indexOf(">Ruimtes<"));
	}

	private String zijbalkOf(String path, RequestPostProcessor als) throws Exception {
		String html = mvc.perform(get(path).with(als)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return html.substring(html.indexOf("<nav"), html.indexOf("</nav>"));
	}

	private UUID createPage(String title, UUID parentId) throws Exception {
		var request = post("/pages").with(csrf()).param("title", title);
		if (parentId != null) {
			request = request.param("parentId", parentId.toString());
		}
		MvcResult result = mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

}
