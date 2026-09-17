package nl.logicai.wiki.services;

import java.util.List;

import nl.logicai.wiki.services.SearchService.Segment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The text fragment under a search hit: a window around the first match, terms marked. */
class SearchServiceSnippetTest {

	@Test
	void marksEveryTermCaseInsensitively() {
		List<Segment> snippet = SearchService.snippet("Log in bij Shopify. Kies de winkel shopify-staging.", List.of("shopify"));

		assertThat(snippet).filteredOn(Segment::hit).extracting(Segment::text).containsExactly("Shopify", "shopify");
		assertThat(String.join("", snippet.stream().map(Segment::text).toList()))
			.isEqualTo("Log in bij Shopify. Kies de winkel shopify-staging.");
	}

	@Test
	void windowsLongTextAroundTheFirstMatch() {
		String text = "a ".repeat(200) + "PostgreSQL staat hier" + " b".repeat(200);
		List<Segment> snippet = SearchService.snippet(text, List.of("postgresql"));

		String joined = String.join("", snippet.stream().map(Segment::text).toList());
		assertThat(joined).startsWith("…").endsWith("…").contains("PostgreSQL staat hier");
		assertThat(joined.length()).isLessThan(200);
	}

	@Test
	void emptyTextGivesEmptySnippet() {
		assertThat(SearchService.snippet("   ", List.of("x"))).isEmpty();
		assertThat(SearchService.snippet(null, List.of("x"))).isEmpty();
	}

}
