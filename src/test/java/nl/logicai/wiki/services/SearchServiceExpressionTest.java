package nl.logicai.wiki.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The MySQL boolean-mode expression built from what a user types in the search box (spec F-13). */
class SearchServiceExpressionTest {

	@Test
	void everyWordIsRequired() {
		assertThat(SearchService.booleanExpression("Spring Boot")).isEqualTo("+spring +boot");
		assertThat(SearchService.booleanExpression("  PostgreSQL  ")).isEqualTo("+postgresql");
	}

	@Test
	void booleanOperatorsNeverReachTheDatabase() {
		assertThat(SearchService.booleanExpression("+deploy -staging \"quoted\" (group) wild* ~not <less >more a@b"))
			.isEqualTo("+deploy +staging +quoted +group +wild +not +less +more");
	}

	@Test
	void wordsSplitTheWayTheIndexSplitsThem() {
		assertThat(SearchService.booleanExpression("node.js e-mail snake_case pagina's")).isEqualTo("+node +mail +snake_case +pagina's");
	}

	@Test
	void shortWordsAndStopwordsAreLeftOut() {
		assertThat(SearchService.booleanExpression("de uitrol en de AI")).isEqualTo("+uitrol");
		assertThat(SearchService.booleanExpression("uitrol uitrol")).isEqualTo("+uitrol");
	}

	@Test
	void aQueryWithoutIndexedWordsIsEmpty() {
		assertThat(SearchService.booleanExpression("AI")).isEmpty();
		assertThat(SearchService.booleanExpression("C# de")).isEmpty();
		assertThat(SearchService.booleanExpression("+ - *")).isEmpty();
	}

}
