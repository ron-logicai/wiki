package nl.logicai.wiki.controllers;

import java.time.OffsetDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search through PostgreSQL (spec F-13, section 7): Dutch, English and technical terms, tag filter,
 * paging, index updated right after saving, deleted pages excluded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockUser(username = "editor", roles = "EDITOR")
class SearchFlowIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void findsDutchEnglishAndTechnicalTermsInTitleAndContent() throws Exception {
		String marker = "zoekmarker" + UUID.randomUUID().toString().substring(0, 8);
		UUID nl = createPage("Uitrol " + marker);
		saveText(nl, "Uitrol " + marker, "De applicatie draait op Spring Boot met PostgreSQL als database.");
		UUID en = createPage("Deployment notes " + marker);
		saveText(en, "Deployment notes " + marker, "Deploy the storefront to staging before production.");

		String body = search("q=" + marker);
		assertThat(body).contains("/pages/" + nl).contains("/pages/" + en).contains("2 resultaten");

		assertThat(search("q=PostgreSQL")).contains("/pages/" + nl).contains("<mark>PostgreSQL</mark>");
		assertThat(search("q=spring+boot")).contains("/pages/" + nl);
		assertThat(search("q=staging")).contains("/pages/" + en).doesNotContain("/pages/" + nl);
		assertThat(search("q=applicatie")).contains("/pages/" + nl);
	}

	@Test
	void tagFilterNarrowsResultsAndCountsAreShown() throws Exception {
		String marker = "tagzoek" + UUID.randomUUID().toString().substring(0, 8);
		UUID a = createPage("Alpha " + marker);
		UUID b = createPage("Beta " + marker);
		mvc.perform(post("/pages/{id}/tags", a).with(csrf()).param("name", "Shopify")).andExpect(status().is3xxRedirection());

		String all = search("q=" + marker);
		assertThat(all).contains("/pages/" + a).contains("/pages/" + b).contains("Filter op tag").contains("Shopify");
		assertThat(all).contains("/search?q=" + marker + "&amp;tag=Shopify&amp;page=1");

		String filtered = search("q=" + marker + "&tag=shopify");
		assertThat(filtered).contains("/pages/" + a).doesNotContain("/pages/" + b)
			.contains("zoek__tag--aan").contains("Filters wissen");
		assertThat(filtered).contains("/search?q=" + marker + "&amp;tag=&amp;page=1");
	}

	@Test
	void resultsArePagedTwentyPerPage() throws Exception {
		String marker = "paginering" + UUID.randomUUID().toString().substring(0, 8);
		for (int i = 1; i <= 21; i++) {
			createPage(marker + " nummer " + i);
		}
		String first = search("q=" + marker);
		assertThat(first).contains("21 resultaten").contains("Volgende").doesNotContain(">Vorige<");
		assertThat(first).contains("/search?q=" + marker + "&amp;tag=&amp;page=2");
		assertThat(first.split("class=\"zoek__hit\"").length - 1).isEqualTo(20);

		String second = search("q=" + marker + "&page=2");
		assertThat(second).contains(">Vorige<").doesNotContain(">Volgende<");
		assertThat(second).contains("/search?q=" + marker + "&amp;tag=&amp;page=1");
		assertThat(second.split("class=\"zoek__hit\"").length - 1).isEqualTo(1);
	}

	@Test
	void savedTextIsFoundImmediatelyAndDeletedPagesAreNot() throws Exception {
		String marker = "vers" + UUID.randomUUID().toString().substring(0, 8);
		UUID id = createPage("Nieuwe tekst");
		assertThat(search("q=" + marker)).doesNotContain("/pages/" + id);

		saveText(id, "Nieuwe tekst", "Deze alinea bevat " + marker + " en wordt direct gevonden.");
		assertThat(search("q=" + marker)).contains("/pages/" + id).contains("<mark>" + marker + "</mark>");

		jdbc.update("update page set deleted_at = ? where id = ?", OffsetDateTime.now(), id);
		assertThat(search("q=" + marker)).doesNotContain("/pages/" + id);
	}

	@Test
	void emptyQueryShowsHintOnly() throws Exception {
		mvc.perform(get("/search"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Typ een woord")))
			.andExpect(content().string(not(containsString("Filter op tag"))));
	}

	/** The page without the sidebar, so "/pages/{id}" checks only look at the results and filters. */
	private String search(String queryString) throws Exception {
		String html = mvc.perform(get("/search?" + queryString))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		int main = html.indexOf("<main");
		assertThat(main).isGreaterThan(0);
		return html.substring(main);
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

	private void saveText(UUID id, String title, String paragraph) throws Exception {
		String document = "[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
			+ paragraph.replace("\"", "\\\"") + "\",\"styles\":{}}]}]";
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":0,\"title\":\"" + title + "\",\"document\":" + document + "}"))
			.andExpect(status().isOk());
	}

}
