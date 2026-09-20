package nl.logicai.wiki.services;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The documented Markdown subset (spec F-17, docs/markdown-export.md). */
class MarkdownExporterTest {

	private static final String BASE = "https://wiki.example.test";

	private final MarkdownExporter exporter = new MarkdownExporter(new ObjectMapper());

	@Test
	void pageHasFrontMatterTitleAndContent() {
		var meta = new MarkdownExporter.PageMeta("Onboarding \"dev\"", List.of("java", "team"),
			BASE + "/pages/0f0f", 3, Instant.parse("2026-09-18T10:00:00Z"));
		String md = exporter.exportPage(meta, """
			[{"type":"paragraph","content":[{"type":"text","text":"Welkom","styles":{}}]}]
			""", BASE);
		assertThat(md).isEqualTo("""
			---
			title: "Onboarding \\"dev\\""
			tags:
			  - "java"
			  - "team"
			source: https://wiki.example.test/pages/0f0f
			version: 3
			updated: 2026-09-18T10:00:00Z
			---

			# Onboarding "dev"

			Welkom
			""");
	}

	@Test
	void emptyDocumentGivesOnlyTheTitle() {
		var meta = new MarkdownExporter.PageMeta("Leeg", List.of(), null, 1, null);
		assertThat(exporter.exportPage(meta, "[]", BASE)).isEqualTo("""
			---
			title: "Leeg"
			version: 1
			---

			# Leeg
			""");
	}

	@Test
	void keepsBasicFormattingAndLinks() {
		String md = exporter.exportDocument("""
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
			  {"type":"link","href":"https://example.com/a b","content":[{"type":"text","text":"link","styles":{"bold":true}}]},
			  {"type":"text","text":" en ","styles":{}},
			  {"type":"link","href":"/pages/123","content":[{"type":"text","text":"intern","styles":{}}]}
			]}]
			""", BASE);
		assertThat(md).isEqualTo(
			"Hallo **vet** en *schuin*, ~~door~~, <u>onder</u> en `a*b` met "
				+ "[**link**](<https://example.com/a%20b>) en [intern](https://wiki.example.test/pages/123)\n");
	}

	@Test
	void whitespaceStaysOutsideEmphasisAndAdjacentRunsMerge() {
		String md = exporter.exportDocument("""
			[{"type":"paragraph","content":[
			  {"type":"text","text":"Een","styles":{}},
			  {"type":"text","text":" vet ","styles":{"bold":true}},
			  {"type":"text","text":"woord","styles":{"bold":true}},
			  {"type":"text","text":".","styles":{}}
			]}]
			""", BASE);
		assertThat(md).isEqualTo("Een **vet woord**.\n");
	}

	@Test
	void escapesMarkdownSyntaxInPlainText() {
		String md = exporter.exportDocument("""
			[{"type":"paragraph","content":[{"type":"text","text":"# geen kop *niet vet* [x] <b> 1. geen lijst","styles":{}}]},
			 {"type":"paragraph","content":[{"type":"text","text":"- geen opsomming\\n1. ook niet\\n> geen citaat","styles":{}}]}]
			""", BASE);
		assertThat(md).isEqualTo("""
			\\# geen kop \\*niet vet\\* \\[x\\] \\<b\\> 1. geen lijst

			\\- geen opsomming\\
			1\\. ook niet\\
			\\> geen citaat
			""");
	}

	@Test
	void headingsQuoteDividerAndCode() {
		String md = exporter.exportDocument("""
			[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Kop","styles":{}}]},
			 {"type":"quote","content":[{"type":"text","text":"Eerste\\nTweede","styles":{}}]},
			 {"type":"divider"},
			 {"type":"codeBlock","props":{"language":"java"},"content":[{"type":"text","text":"int x = 1;\\n  x++;","styles":{}}]},
			 {"type":"codeBlock","props":{"language":"text"},"content":[{"type":"text","text":"```\\nfence inside","styles":{}}]}]
			""", BASE);
		assertThat(md).isEqualTo("""
			## Kop

			> Eerste\\
			> Tweede

			---

			```java
			int x = 1;
			  x++;
			```

			````
			```
			fence inside
			````
			""");
	}

	@Test
	void listsKeepNumberingChecklistStateAndNesting() {
		String md = exporter.exportDocument("""
			[{"type":"bulletListItem","content":[{"type":"text","text":"Ouder","styles":{}}],
			  "children":[
			    {"type":"numberedListItem","content":[{"type":"text","text":"Een","styles":{}}]},
			    {"type":"numberedListItem","content":[{"type":"text","text":"Twee\\nregel","styles":{}}]},
			    {"type":"paragraph","content":[{"type":"text","text":"Toelichting","styles":{}}]}
			  ]},
			 {"type":"bulletListItem","content":[{"type":"text","text":"Buur","styles":{}}]},
			 {"type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Klaar","styles":{}}]},
			 {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Open","styles":{}}]},
			 {"type":"paragraph","content":[{"type":"text","text":"Na de lijst","styles":{}}]}]
			""", BASE);
		assertThat(md).isEqualTo("""
			- Ouder
			    1. Een
			    2. Twee\\
			        regel

			    Toelichting
			- Buur

			- [x] Klaar
			- [ ] Open

			Na de lijst
			""");
	}

	@Test
	void childrenOfAParagraphFollowItAtTheSameLevel() {
		String md = exporter.exportDocument("""
			[{"type":"paragraph","content":[{"type":"text","text":"Boven","styles":{}}],
			  "children":[{"type":"paragraph","content":[{"type":"text","text":"Ingesprongen","styles":{}}]}]}]
			""", BASE);
		assertThat(md).isEqualTo("Boven\n\nIngesprongen\n");
	}

	@Test
	void uploadedVideoBecomesALinkWithCaption() {
		String md = exporter.exportDocument("""
			[{"type":"video","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c","name":"demo.mp4","caption":"Korte demo"}},
			 {"type":"video","props":{"url":"https://evil.example/x.mp4","name":"buiten"}}]
			""", BASE);
		assertThat(md).isEqualTo("""
			[Video: demo.mp4](https://wiki.example.test/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c)\\
			*Korte demo*

			Video: buiten
			""");
	}

	@Test
	void uploadedImageAndPdfBecomeImageSyntaxAndALink() {
		String md = exporter.exportDocument("""
			[{"type":"image","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c","name":"schema_v2.png","caption":"Het schema","previewWidth":400}},
			 {"type":"file","props":{"url":"/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5d","name":"handleiding.pdf"}},
			 {"type":"image","props":{"url":"https://evil.example/x.png","name":"buiten"}}]
			""", BASE);
		assertThat(md).isEqualTo("""
			![schema\\_v2.png](https://wiki.example.test/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5c)\\
			*Het schema*

			[Bijlage: handleiding.pdf](https://wiki.example.test/attachments/6f1d2c3b-4a5e-4f60-8b9c-0d1e2f3a4b5d)

			Afbeelding: buiten
			""");
	}

	@Test
	void unsafeLinkFallsBackToFragment() {
		String md = exporter.exportDocument("""
			[{"type":"paragraph","content":[{"type":"link","href":"javascript:alert(1)","content":[{"type":"text","text":"slecht","styles":{}}]}]}]
			""", BASE);
		assertThat(md).isEqualTo("[slecht](#)\n");
	}

	@Test
	void coloursAreDroppedWithoutOtherChanges() {
		String md = exporter.exportDocument("""
			[{"type":"paragraph","content":[{"type":"text","text":"Rood","styles":{"textColor":"red","backgroundColor":"yellow"}}]}]
			""", BASE);
		assertThat(md).isEqualTo("Rood\n");
	}

}
