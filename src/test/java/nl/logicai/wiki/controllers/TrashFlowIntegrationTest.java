package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Trash through the real database (spec F-12, acceptance A-06). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrashFlowIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void leafPageGoesToTrashDisappearsAndComesBackAtTheSameUrl() throws Exception {
		String marker = "bladpagina" + UUID.randomUUID().toString().substring(0, 8);
		UUID id = createPage(marker, null);

		mvc.perform(post("/pages/{id}/delete", id).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/"))
			.andExpect(flash().attribute("melding", containsString("prullenbak")));

		// The link still answers, with a clear message instead of another page (F-12, F-16).
		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isGone())
			.andExpect(content().string(containsString("staat in de prullenbak")))
			.andExpect(content().string(containsString("/trash")));

		// Gone from navigation and search.
		assertThat(mainOf("/")).doesNotContain("/pages/" + id);
		assertThat(mainOf("/search?q=" + marker)).doesNotContain("/pages/" + id);

		mvc.perform(get("/trash"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/pages/" + id)))
			.andExpect(content().string(containsString("stond op het hoofdniveau")));

		mvc.perform(post("/trash/{id}/restore", id).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + id));
		mvc.perform(get("/pages/{id}", id))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>" + marker + "</h1>")));
		assertThat(mainOf("/search?q=" + marker)).contains("/pages/" + id);

		Integer events = jdbc.queryForObject("select count(*) from audit_event where page_id = ?", Integer.class, id);
		assertThat(events).isEqualTo(2);
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void pageWithActiveSubpagesCannotBeDeleted() throws Exception {
		UUID ouder = createPage("Ouder met kind", null);
		UUID kind = createPage("Kind", ouder);

		mvc.perform(post("/pages/{id}/delete", ouder).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + ouder))
			.andExpect(flash().attribute("fout", containsString("subpagina")));
		mvc.perform(get("/pages/{id}", ouder)).andExpect(status().isOk());

		// After the child is gone, the parent can go too.
		mvc.perform(post("/pages/{id}/delete", kind).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/pages/{id}/delete", ouder).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("melding", containsString("prullenbak")));
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void restoringUnderDeletedParentRequiresAChoice() throws Exception {
		UUID ouder = createPage("Verdwenen ouder", null);
		UUID nieuweOuder = createPage("Nieuwe ouder", null);
		UUID kind = createPage("Weeskind", ouder);
		mvc.perform(post("/pages/{id}/delete", kind).with(csrf()).param("baseVersion", "0")).andExpect(status().is3xxRedirection());
		mvc.perform(post("/pages/{id}/delete", ouder).with(csrf()).param("baseVersion", "0")).andExpect(status().is3xxRedirection());

		mvc.perform(get("/trash"))
			.andExpect(content().string(containsString("(ook verwijderd)")))
			.andExpect(content().string(containsString("id=\"plek-" + kind + "\"")));

		// Without a choice the old parent is gone: refused with a message.
		mvc.perform(post("/trash/{id}/restore", kind).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/trash"))
			.andExpect(flash().attribute("fout", containsString("Kies een andere plek")));

		// Choose another active parent: restored there, same URL.
		mvc.perform(post("/trash/{id}/restore", kind).with(csrf())
				.param("parentChosen", "true").param("parentId", nieuweOuder.toString()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + kind));
		mvc.perform(get("/pages/{id}", kind))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<li><a href=\"/pages/" + nieuweOuder + "\">Nieuwe ouder</a></li>")));

		// The old parent can be restored to the top level.
		mvc.perform(post("/trash/{id}/restore", ouder).with(csrf()).param("parentChosen", "true").param("parentId", ""))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", "/pages/" + ouder));
		mvc.perform(get("/pages/{id}", ouder)).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void staleVersionIsRefused() throws Exception {
		UUID id = createPage("Verouderd", null);
		mvc.perform(post("/pages/{id}/delete", id).with(csrf()).param("baseVersion", "5"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("fout", containsString("intussen gewijzigd")));
		mvc.perform(get("/pages/{id}", id)).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerCannotDeleteRestoreOrSeeTheTrash() throws Exception {
		mvc.perform(get("/trash")).andExpect(status().isForbidden());
		mvc.perform(post("/pages/{id}/delete", UUID.randomUUID()).with(csrf()).param("baseVersion", "0"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/trash/{id}/restore", UUID.randomUUID()).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/pages/{id}", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	private String mainOf(String path) throws Exception {
		String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return html.substring(html.indexOf("<main"));
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
