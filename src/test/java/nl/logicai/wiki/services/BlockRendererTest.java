package nl.logicai.wiki.services;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class BlockRendererTest {

	private final BlockRenderer renderer = new BlockRenderer(new ObjectMapper());

	@Test
	void rendersParagraphWithInlineStyles() {
		String html = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"text","text":"Hallo ","styles":{}},
			  {"type":"text","text":"wereld","styles":{"bold":true,"italic":true}}
			]}]
			""");
		assertThat(html).isEqualTo("<p>Hallo <strong><em>wereld</em></strong></p>");
	}

	@Test
	void escapesHtmlInText() {
		String html = renderer.render("""
			[{"type":"paragraph","content":[{"type":"text","text":"<script>alert(1)</script>","styles":{}}]}]
			""");
		assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
	}

	@Test
	void groupsConsecutiveListItemsAndKeepsChecklistState() {
		String html = renderer.render("""
			[{"type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Klaar","styles":{}}]},
			 {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Open","styles":{}}]},
			 {"type":"paragraph","content":[{"type":"text","text":"Na de lijst","styles":{}}]}]
			""");
		assertThat(html).isEqualTo(
			"<ul class=\"checklist\">"
				+ "<li><input type=\"checkbox\" disabled checked> Klaar</li>"
				+ "<li><input type=\"checkbox\" disabled> Open</li>"
				+ "</ul><p>Na de lijst</p>");
	}

	@Test
	void rendersHeadingCodeQuoteAndDivider() {
		String html = renderer.render("""
			[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Kop","styles":{}}]},
			 {"type":"codeBlock","props":{"language":"java"},"content":[{"type":"text","text":"int x = 1;\\n  x++;","styles":{}}]},
			 {"type":"quote","content":[{"type":"text","text":"Citaat","styles":{}}]},
			 {"type":"divider"}]
			""");
		assertThat(html).isEqualTo(
			"<h2>Kop</h2>"
				+ "<pre><code class=\"language-java\">int x = 1;\n  x++;</code></pre>"
				+ "<blockquote><p>Citaat</p></blockquote>"
				+ "<hr>");
	}

	@Test
	void rejectsScriptLinksButKeepsSafeOnes() {
		String html = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"link","href":"javascript:alert(1)","content":[{"type":"text","text":"slecht","styles":{}}]},
			  {"type":"link","href":"/pages/123","content":[{"type":"text","text":"intern","styles":{}}]}
			]}]
			""");
		assertThat(html).isEqualTo("<p><a href=\"#\">slecht</a><a href=\"/pages/123\">intern</a></p>");
	}

	@Test
	void rendersNestedChildren() {
		String html = renderer.render("""
			[{"type":"bulletListItem","content":[{"type":"text","text":"Ouder","styles":{}}],
			  "children":[{"type":"bulletListItem","content":[{"type":"text","text":"Kind","styles":{}}]}]}]
			""");
		assertThat(html).isEqualTo(
			"<ul><li>Ouder<div class=\"block-children\"><ul><li>Kind</li></ul></div></li></ul>");
	}

	@Test
	void outlineGivesHeadingsSequentialIdsWhilePlainRenderStaysUnchanged() {
		String doc = """
			[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Kop","styles":{}}]},
			 {"type":"paragraph","content":[{"type":"text","text":"Tekst","styles":{}}]},
			 {"type":"heading","props":{"level":3},"content":[{"type":"text","text":"Sub & co","styles":{}}]},
			 {"type":"bulletListItem","content":[{"type":"text","text":"Item","styles":{}}],
			  "children":[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Kop","styles":{}}]}]}]
			""";

		BlockRenderer.Rendered rendered = renderer.renderWithOutline(doc);

		assertThat(rendered.html()).isEqualTo(
			"<h2 id=\"kop-1\">Kop</h2>"
				+ "<p>Tekst</p>"
				+ "<h3 id=\"kop-2\">Sub &amp; co</h3>"
				+ "<ul><li>Item<div class=\"block-children\"><h2 id=\"kop-3\">Kop</h2></div></li></ul>");
		assertThat(rendered.outline()).containsExactly(
			new BlockRenderer.Heading(2, "kop-1", "Kop"),
			new BlockRenderer.Heading(3, "kop-2", "Sub & co"),
			new BlockRenderer.Heading(2, "kop-3", "Kop"));
		assertThat(renderer.render(doc)).startsWith("<h2>Kop</h2>").doesNotContain("kop-1");
	}

}
