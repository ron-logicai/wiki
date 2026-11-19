package nl.logicai.wiki.services;

import java.util.List;

import nl.logicai.wiki.config.WikiLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown to blocks (docs/mcp.md): the inverse of MarkdownExporter for the validator's subset, a graceful
 * fallback for everything else, and a merge that leaves unchanged blocks exactly as they were stored.
 */
class MarkdownImporterTest {

	private static final String BASE = "https://wiki.example.test";
	private static final String ATTACHMENT = "/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c";

	private final ObjectMapper mapper = new ObjectMapper();
	private final MarkdownExporter exporter = new MarkdownExporter(mapper);
	private final MarkdownImporter importer = new MarkdownImporter(mapper, exporter);
	private final DocumentValidator validator = new DocumentValidator(mapper, new WikiLimits(200, 2_097_152));

	/** The documents of MarkdownExporterTest: whatever the exporter can write, the importer reads back. */
	static List<String> exportedDocuments() {
		return List.of(
			"""
			[{"type":"paragraph","content":[
			  {"type":"text","text":"Hallo ","styles":{}},
			  {"type":"text","text":"vet","styles":{"bold":true}},
			  {"type":"text","text":" en ","styles":{}},
			  {"type":"text","text":"schuin","styles":{"italic":true}},
			  {"type":"text","text":", ","styles":{}},
			  {"type":"text","text":"door","styles":{"strike":true}},
			  {"type":"text","text":", ","styles":{}},
			  {"type":"text","text":"onder","styles":{"underline":true}},
			  {"type":"text","text":" en ","styles":{}},
			  {"type":"text","text":"a*b","styles":{"code":true}},
			  {"type":"text","text":" met ","styles":{}},
			  {"type":"link","href":"https://example.com/a(b)","content":[{"type":"text","text":"link","styles":{"bold":true}}]},
			  {"type":"text","text":" en ","styles":{}},
			  {"type":"link","href":"/pages/123","content":[{"type":"text","text":"intern","styles":{}}]}
			]}]
			""",
			"""
			[{"type":"paragraph","content":[
			  {"type":"text","text":"Een","styles":{}},
			  {"type":"text","text":" vet ","styles":{"bold":true}},
			  {"type":"text","text":"woord","styles":{"bold":true,"italic":true}},
			  {"type":"text","text":".","styles":{}}
			]}]
			""",
			"""
			[{"type":"paragraph","content":[{"type":"text","text":"# geen kop *niet vet* [x] <b> 1. geen lijst _x_ ~y~ `z` a\\\\b","styles":{}}]},
			 {"type":"paragraph","content":[{"type":"text","text":"- geen opsomming\\n1. ook niet\\n> geen citaat","styles":{}}]}]
			""",
			"""
			[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Kop","styles":{}}]},
			 {"type":"quote","content":[{"type":"text","text":"Eerste\\nTweede","styles":{}}]},
			 {"type":"divider"},
			 {"type":"codeBlock","props":{"language":"java"},"content":[{"type":"text","text":"int x = 1;\\n  x++;","styles":{}}]},
			 {"type":"codeBlock","props":{"language":"text"},"content":[{"type":"text","text":"```\\nfence inside","styles":{}}]},
			 {"type":"heading","props":{"level":6},"content":[{"type":"text","text":"Diep","styles":{"bold":true}}]}]
			""",
			"""
			[{"type":"bulletListItem","content":[{"type":"text","text":"Ouder","styles":{}}],
			  "children":[
			    {"type":"numberedListItem","content":[{"type":"text","text":"Een","styles":{}}]},
			    {"type":"numberedListItem","content":[{"type":"text","text":"Twee\\nregel","styles":{}}]},
			    {"type":"paragraph","content":[{"type":"text","text":"Toelichting","styles":{}}]}
			  ]},
			 {"type":"bulletListItem","content":[{"type":"text","text":"Buur","styles":{}}]},
			 {"type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Klaar","styles":{}}]},
			 {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Open","styles":{}}],
			  "children":[{"type":"codeBlock","props":{"language":"sh"},"content":[{"type":"text","text":"ls -l","styles":{}}]}]},
			 {"type":"numberedListItem","content":[{"type":"text","text":"Eerst","styles":{}}]},
			 {"type":"numberedListItem","content":[{"type":"text","text":"Dan","styles":{}}]},
			 {"type":"paragraph","content":[{"type":"text","text":"Na de lijst","styles":{}}]}]
			""",
			"""
			[{"type":"paragraph","content":[{"type":"text","text":"Boven","styles":{}}],
			  "children":[{"type":"paragraph","content":[{"type":"text","text":"Ingesprongen","styles":{}}]}]}]
			""",
			"""
			[{"type":"video","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c","name":"demo.mp4","caption":"Korte demo"}},
			 {"type":"image","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c","name":"schema_v2.png","caption":"Het schema","previewWidth":400}},
			 {"type":"file","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5d","name":"handleiding (v2).pdf"}},
			 {"type":"image","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c","name":""}}]
			""",
			"""
			[{"type":"paragraph","content":[{"type":"text","text":"Rood","styles":{"textColor":"red","backgroundColor":"yellow"}}]}]
			""");
	}

	@ParameterizedTest
	@MethodSource("exportedDocuments")
	void readsBackWhatTheExporterWrites(String document) {
		String markdown = exporter.exportDocument(document, BASE);

		var imported = importer.importDocument(markdown, BASE);

		assertThat(exporter.exportDocument(imported.blocks(), BASE)).isEqualTo(markdown);
		assertThat(imported.warnings()).isEmpty();
		validator.validate(imported.blocks());
	}

	@Test
	void mapsBlocksAndInlineFormatting() {
		JsonNode blocks = importer.importDocument("""
			## Kop met `code`

			Tekst met **vet**, *schuin*, ~~door~~, <u>onder</u> en een
			zachte regelovergang.\\
			Harde overgang.

			1. Een
			2. Twee
			   - Genest

			- [x] Klaar
			- [ ] Open
			- [ ]

			> Citaat
			> tweede regel

			***

			```java
			int x = 1;
			```
			""", BASE).blocks();

		assertThat(types(blocks)).containsExactly("heading", "paragraph", "numberedListItem", "numberedListItem",
			"checkListItem", "checkListItem", "checkListItem", "quote", "divider", "codeBlock");
		assertThat(blocks.get(0).path("props").path("level").asInt()).isEqualTo(2);
		assertThat(blocks.get(0).path("content").get(1).path("styles").path("code").asBoolean()).isTrue();

		JsonNode paragraph = blocks.get(1).path("content");
		assertThat(MarkdownImporter.plainText(paragraph))
			.isEqualTo("Tekst met vet, schuin, door, onder en een zachte regelovergang.\nHarde overgang.");
		assertThat(paragraph.get(1).path("styles").path("bold").asBoolean()).isTrue();
		assertThat(paragraph.get(3).path("styles").path("italic").asBoolean()).isTrue();
		assertThat(paragraph.get(5).path("styles").path("strike").asBoolean()).isTrue();
		assertThat(paragraph.get(7).path("styles").path("underline").asBoolean()).isTrue();

		assertThat(blocks.get(3).path("children").get(0).path("type").asString()).isEqualTo("bulletListItem");
		assertThat(blocks.get(4).path("props").path("checked").asBoolean()).isTrue();
		assertThat(blocks.get(5).path("props").path("checked").asBoolean()).isFalse();
		assertThat(blocks.get(6).path("content").isEmpty()).isTrue();
		assertThat(MarkdownImporter.plainText(blocks.get(7).path("content"))).isEqualTo("Citaat tweede regel");
		assertThat(blocks.get(9).path("props").path("language").asString()).isEqualTo("java");
		assertThat(MarkdownImporter.plainText(blocks.get(9).path("content"))).isEqualTo("int x = 1;");
		validator.validate(blocks);
	}

	@Test
	void internalLinksBecomeRelativeAndUploadsBecomeFileBlocksAgain() {
		JsonNode blocks = importer.importDocument("""
			Zie [de pagina](%1$s/pages/0f0f) en [extern](https://example.com).

			![schema.png](%1$s%2$s)\\
			*Het schema*

			[Bijlage: handleiding.pdf](%1$s%2$s)

			[Video: demo.mp4](%2$s)
			""".formatted(BASE, ATTACHMENT), BASE).blocks();

		assertThat(types(blocks)).containsExactly("paragraph", "image", "file", "video");
		assertThat(blocks.get(0).path("content").get(1).path("href").asString()).isEqualTo("/pages/0f0f");
		assertThat(blocks.get(0).path("content").get(3).path("href").asString()).isEqualTo("https://example.com");
		assertThat(blocks.get(1).path("props").path("url").asString()).isEqualTo(ATTACHMENT);
		assertThat(blocks.get(1).path("props").path("name").asString()).isEqualTo("schema.png");
		assertThat(blocks.get(1).path("props").path("caption").asString()).isEqualTo("Het schema");
		assertThat(blocks.get(2).path("props").path("name").asString()).isEqualTo("handleiding.pdf");
		assertThat(blocks.get(3).path("props").path("url").asString()).isEqualTo(ATTACHMENT);
		validator.validate(blocks);

		// The exporter writes a space in a destination as %20, so that is what comes back (a stable form).
		JsonNode spaced = importer.importDocument("[link](<https://example.com/a%20b>)", BASE).blocks();
		assertThat(spaced.get(0).path("content").get(0).path("href").asString()).isEqualTo("https://example.com/a%20b");
	}

	@Test
	void unsupportedMarkdownDegradesToTextWithAWarning() {
		var imported = importer.importDocument("""
			| Naam | Rol |
			| --- | --- |
			| Ron | Admin |

			<div class="x">blok</div>

			Tekst met <span>inline</span> html, een [slechte link](javascript:alert(1)) en ![plaatje](https://example.com/x.png).
			""", BASE);
		JsonNode blocks = imported.blocks();

		assertThat(types(blocks)).containsExactly("codeBlock", "paragraph", "paragraph");
		assertThat(MarkdownImporter.plainText(blocks.get(0).path("content")))
			.isEqualTo("| Naam | Rol |\n| --- | --- |\n| Ron | Admin |");
		assertThat(MarkdownImporter.plainText(blocks.get(1).path("content"))).isEqualTo("<div class=\"x\">blok</div>");
		JsonNode last = blocks.get(2).path("content");
		assertThat(MarkdownImporter.plainText(last))
			.isEqualTo("Tekst met <span>inline</span> html, een slechte link en plaatje.");
		// The external image is kept as a link, the javascript: link only as its text.
		assertThat(last.findValuesAsString("href")).containsExactly("https://example.com/x.png");
		assertThat(imported.warnings()).hasSize(4);
		validator.validate(blocks);
	}

	@Test
	void dropsFrontMatterAndATitleHeadingThatRepeatsThePageTitle() {
		JsonNode blocks = importer.importDocument("""
			---
			title: "Onboarding"
			tags:
			  - "java"
			version: 3
			---

			# Onboarding

			Welkom
			""", BASE, "Onboarding").blocks();

		assertThat(types(blocks)).containsExactly("paragraph");

		// Without a matching title the heading is content, and a rule at the top is not front matter.
		assertThat(types(importer.importDocument("# Iets anders\n\nWelkom", BASE, "Onboarding").blocks()))
			.containsExactly("heading", "paragraph");
		assertThat(types(importer.importDocument("---\n\nTekst\n\n---\n", BASE).blocks()))
			.containsExactly("divider", "paragraph", "divider");
	}

	@Test
	void nestingDeeperThanTheValidatorAllowsIsFlattened() {
		StringBuilder markdown = new StringBuilder();
		for (int level = 0; level < 14; level++) {
			markdown.append("  ".repeat(level)).append("- niveau ").append(level).append('\n');
		}

		JsonNode blocks = importer.importDocument(markdown.toString(), BASE).blocks();

		validator.validate(blocks);
		assertThat(blocks.toString()).contains("niveau 13");
	}

	@Test
	void mergeKeepsUnchangedBlocksExactlyAsStored() {
		JsonNode stored = mapper.readTree("""
			[{"id":"a","type":"heading","props":{"level":2,"textColor":"blue"},"content":[{"type":"text","text":"Kop","styles":{}}],"children":[]},
			 {"id":"b","type":"paragraph","props":{},"content":[{"type":"text","text":"Rood","styles":{"textColor":"red"}}],"children":[]},
			 {"id":"c","type":"paragraph","props":{},"content":[],"children":[]},
			 {"id":"d","type":"paragraph","props":{},"content":[{"type":"text","text":"Boven","styles":{}}],
			  "children":[{"id":"d1","type":"paragraph","props":{},"content":[{"type":"text","text":"Ingesprongen","styles":{}}],"children":[]}]},
			 {"id":"e","type":"image","props":{"url":"%s","name":"x.png","previewWidth":300},"children":[]},
			 {"id":"f","type":"paragraph","props":{},"content":[{"type":"text","text":"Slot","styles":{}}],"children":[]}]
			""".formatted(ATTACHMENT));
		String markdown = exporter.exportDocument(stored, BASE);

		// Sending the exported Markdown back unchanged changes nothing at all.
		var same = importer.merge(stored, markdown, BASE, null);
		assertThat(same.blocks()).isEqualTo(stored);
		assertThat(same.losses()).isEmpty();

		// One paragraph rewritten and one added: everything else is still the stored block.
		var edited = importer.merge(stored, markdown.replace("Slot", "Nieuw slot") + "\nExtra alinea\n", BASE, null);
		assertThat(ids(edited.blocks()).subList(0, 5)).containsExactly("a", "b", "c", "d", "e");
		assertThat(edited.blocks().get(1).path("content").get(0).path("styles").path("textColor").asString()).isEqualTo("red");
		assertThat(edited.blocks().get(4).path("props").path("previewWidth").asInt()).isEqualTo(300);
		assertThat(MarkdownImporter.plainText(edited.blocks().get(5).path("content"))).isEqualTo("Nieuw slot");
		assertThat(MarkdownImporter.plainText(edited.blocks().get(6).path("content"))).isEqualTo("Extra alinea");
		assertThat(edited.losses()).isEmpty();
		validator.validate(edited.blocks());
	}

	@Test
	void mergeReportsFormattingThatAChangedBlockLoses() {
		JsonNode stored = mapper.readTree("""
			[{"id":"a","type":"paragraph","props":{},"content":[{"type":"text","text":"Let op: rood","styles":{"textColor":"red"}}],"children":[]},
			 {"id":"b","type":"paragraph","props":{},"content":[{"type":"text","text":"Gewoon","styles":{}}],"children":[]}]
			""");

		var merged = importer.merge(stored, "Let op: aangepast\n\nGewoon\n", BASE, null);

		assertThat(merged.losses()).containsExactly("Block 1 (paragraph \"Let op: rood\"): text colour");
		assertThat(ids(merged.blocks()).get(1)).isEqualTo("b");
		assertThat(MarkdownLossDetector.inspect(stored)).containsExactly("Block 1 (paragraph \"Let op: rood\"): text colour");
	}

	@Test
	void mergeIntoAnEmptyPageDoesNotKeepTheEmptyParagraph() {
		JsonNode stored = mapper.readTree("""
			[{"id":"leeg","type":"paragraph","props":{},"content":[],"children":[]}]
			""");

		var merged = importer.merge(stored, "Eerste inhoud", BASE, null);

		assertThat(types(merged.blocks())).containsExactly("paragraph");
		assertThat(MarkdownImporter.plainText(merged.blocks().get(0).path("content"))).isEqualTo("Eerste inhoud");
	}

	private static List<String> types(JsonNode blocks) {
		return blocks.valueStream().map(block -> block.path("type").asString()).toList();
	}

	private static List<String> ids(JsonNode blocks) {
		return blocks.valueStream().map(block -> block.path("id").asString()).toList();
	}

}
