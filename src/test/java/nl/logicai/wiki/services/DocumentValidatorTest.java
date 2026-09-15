package nl.logicai.wiki.services;

import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.config.WikiLimits;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentValidatorTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final DocumentValidator validator = new DocumentValidator(mapper, new WikiLimits(200, 2_097_152));

	@Test
	void acceptsSupportedBlocksAndDerivesSearchText() {
		WikiDocument document = validator.validate(mapper.readTree("""
			[{"id":"a1","type":"heading","props":{"level":1},"content":[{"type":"text","text":"Spring Boot","styles":{}}],"children":[]},
			 {"id":"a2","type":"paragraph","props":{},"content":[
			   {"type":"text","text":"Zie ","styles":{}},
			   {"type":"link","href":"https://spring.io","content":[{"type":"text","text":"spring.io","styles":{"bold":true}}]}
			 ],"children":[]},
			 {"id":"a3","type":"divider","props":{}}]
			"""));
		assertThat(document.searchText()).isEqualTo("Spring Boot Zie spring.io");
		assertThat(document.schemaVersion()).isEqualTo(DocumentValidator.SCHEMA_VERSION);
	}

	@Test
	void rejectsUnknownBlockType() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"table","content":[]}]
			""")))
			.isInstanceOf(InvalidContentException.class)
			.hasMessageContaining("table");
	}

	@Test
	void rejectsScriptLinks() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"paragraph","content":[{"type":"link","href":"javascript:alert(1)","content":[{"type":"text","text":"x","styles":{}}]}]}]
			""")))
			.isInstanceOf(InvalidContentException.class)
			.hasMessageContaining("Linkbestemming");
	}

	@Test
	void rejectsNonArrayDocument() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("{\"type\":\"paragraph\"}")))
			.isInstanceOf(InvalidContentException.class);
	}

	@Test
	void rejectsOversizedDocument() {
		DocumentValidator small = new DocumentValidator(mapper, new WikiLimits(200, 50));
		assertThatThrownBy(() -> small.validate(mapper.readTree("""
			[{"type":"paragraph","content":[{"type":"text","text":"Dit is een tekst die langer is dan vijftig bytes.","styles":{}}]}]
			""")))
			.isInstanceOf(InvalidContentException.class)
			.hasMessageContaining("te groot");
	}

	@Test
	void normalizesTitle() {
		assertThat(validator.normalizeTitle("  Projectpagina  ")).isEqualTo("Projectpagina");
		assertThatThrownBy(() -> validator.normalizeTitle("   ")).isInstanceOf(InvalidContentException.class);
		assertThatThrownBy(() -> validator.normalizeTitle("x".repeat(201))).isInstanceOf(InvalidContentException.class);
	}

}
