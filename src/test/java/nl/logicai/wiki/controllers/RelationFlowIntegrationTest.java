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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Related pages (the "Gerelateerd" block with its plus button): editors link and unlink existing pages,
 * the link is directed, trashed pages disappear from the list, viewers can only read.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RelationFlowIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorLinksAndUnlinksAnExistingPage() throws Exception {
		UUID a = createPage("Offerteproces");
		UUID b = createPage("Facturatie in Moneybird");

		mvc.perform(post("/pages/{id}/related", a).with(csrf()).param("relatedId", b.toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + a))
			.andExpect(flash().attribute("melding", containsString("Facturatie in Moneybird")));

		mvc.perform(get("/pages/{id}", a))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Gerelateerd")))
			.andExpect(content().string(containsString("/pages/" + b + "\"")))
			.andExpect(content().string(containsString("/pages/" + a + "/related/" + b + "/remove")));
		// Directed: the other page does not list this one.
		mvc.perform(get("/pages/{id}", b))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("/related/" + a + "/remove"))));

		// Already linked, and a page cannot be linked to itself.
		mvc.perform(post("/pages/{id}/related", a).with(csrf()).param("relatedId", b.toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("al gekoppeld")));
		mvc.perform(post("/pages/{id}/related", a).with(csrf()).param("relatedId", a.toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("zichzelf")));

		mvc.perform(post("/pages/{id}/related/{relatedId}/remove", a, b).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + a));
		mvc.perform(get("/pages/{id}", a))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("/related/" + b + "/remove"))));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void trashedPagesDropOutOfTheListAndCannotBeLinked() throws Exception {
		UUID a = createPage("Hoofdpagina");
		UUID b = createPage("Weggegooide pagina");
		mvc.perform(post("/pages/{id}/related", a).with(csrf()).param("relatedId", b.toString()))
			.andExpect(status().is3xxRedirection());

		mvc.perform(post("/pages/{id}/delete", b).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection());

		mvc.perform(get("/pages/{id}", a))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Weggegooide pagina"))));
		mvc.perform(post("/pages/{id}/related", a).with(csrf()).param("relatedId", b.toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("prullenbak")));
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerSeesNoPlusButtonAndCannotChangeLinks() throws Exception {
		UUID a = createPageAsEditor("Alleen lezen");

		mvc.perform(get("/pages/{id}", a))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("name=\"relatedId\""))))
			.andExpect(content().string(not(containsString("Nog geen gerelateerde pagina"))));
		mvc.perform(post("/pages/{id}/related", a).with(csrf()).param("relatedId", UUID.randomUUID().toString()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/pages/{id}/related/{relatedId}/remove", a, UUID.randomUUID()).with(csrf()))
			.andExpect(status().isForbidden());
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

	private UUID createPageAsEditor(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).with(user("editor").roles("EDITOR")).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

}
