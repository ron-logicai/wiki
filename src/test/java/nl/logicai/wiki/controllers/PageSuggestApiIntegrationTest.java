package nl.logicai.wiki.controllers;

import java.time.OffsetDateTime;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Link suggestions for the editor (spec U-06): GET /api/pages/suggest?q= returns active pages whose
 * title contains the query, with the fixed page URL, bounded to ten; viewers may read it, anonymous
 * requests get 401 like every other API call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockUser(username = "editor", roles = "EDITOR")
class PageSuggestApiIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void matchesTitleSubstringCaseInsensitiveWithPathAndUrl() throws Exception {
		String marker = "sugg" + UUID.randomUUID().toString().substring(0, 8);
		UUID parent = createPage("Ouder " + marker, null);
		UUID child = createPage("Kind " + marker, parent);

		mvc.perform(get("/api/pages/suggest").param("q", "kind " + marker.toUpperCase())
				.with(user("viewer").roles("VIEWER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(child.toString()))
			.andExpect(jsonPath("$[0].title").value("Kind " + marker))
			.andExpect(jsonPath("$[0].path").value("Ouder " + marker))
			.andExpect(jsonPath("$[0].url").value("/pages/" + child));

		mvc.perform(get("/api/pages/suggest").param("q", marker))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].title").value("Kind " + marker))
			.andExpect(jsonPath("$[0].path").value("Ouder " + marker))
			.andExpect(jsonPath("$[1].title").value("Ouder " + marker))
			.andExpect(jsonPath("$[1].path").value(""));
	}

	@Test
	void excludesPagesInTrash() throws Exception {
		String marker = "prul" + UUID.randomUUID().toString().substring(0, 8);
		UUID kept = createPage("Blijft " + marker, null);
		UUID trashed = createPage("Weg " + marker, null);
		jdbc.update("update page set deleted_at = ? where id = ?", OffsetDateTime.now(), trashed);

		mvc.perform(get("/api/pages/suggest").param("q", marker))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[*].id", hasItem(kept.toString())))
			.andExpect(jsonPath("$[*].id", not(hasItem(trashed.toString()))));
	}

	@Test
	void capsAtTenSortedByTitle() throws Exception {
		String marker = "cap" + UUID.randomUUID().toString().substring(0, 8);
		for (int i = 12; i >= 1; i--) {
			createPage("Lijst " + marker + " " + String.format("%02d", i), null);
		}

		mvc.perform(get("/api/pages/suggest").param("q", "lijst " + marker))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(10))
			.andExpect(jsonPath("$[0].title").value("Lijst " + marker + " 01"))
			.andExpect(jsonPath("$[9].title").value("Lijst " + marker + " 10"));
	}

	@Test
	void emptyQueryReturnsRecentPagesBounded() throws Exception {
		String marker = "leeg" + UUID.randomUUID().toString().substring(0, 8);
		UUID newest = createPage("Nieuwste " + marker, null);

		mvc.perform(get("/api/pages/suggest"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()", lessThanOrEqualTo(10)))
			.andExpect(jsonPath("$[0].id").value(newest.toString()));
	}

	@Test
	void anonymousGets401() throws Exception {
		mvc.perform(get("/api/pages/suggest").param("q", "x").with(anonymous()))
			.andExpect(status().isUnauthorized());
	}

	private UUID createPage(String title, UUID parentId) throws Exception {
		var request = post("/pages").with(csrf()).param("title", title);
		if (parentId != null) {
			request = request.param("parentId", parentId.toString());
		}
		MvcResult result = mvc.perform(request)
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = result.getResponse().getRedirectedUrl();
		return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
	}

}
