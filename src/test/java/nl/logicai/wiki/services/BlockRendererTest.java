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

}
