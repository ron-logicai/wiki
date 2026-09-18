package nl.logicai.wiki.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** YouTube links render as an embedded player in the read view; anything else stays a plain link. */
class VideoEmbedTest {

	private static final String EMBED = "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ";

	private final BlockRenderer renderer = new BlockRenderer(new ObjectMapper());

	@ParameterizedTest
	@ValueSource(strings = {
		"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
		"https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&list=PL123",
		"https://youtube.com/watch?v=dQw4w9WgXcQ",
		"https://m.youtube.com/watch?v=dQw4w9WgXcQ",
		"https://youtu.be/dQw4w9WgXcQ",
		"https://youtu.be/dQw4w9WgXcQ?si=abc",
		"https://www.youtube.com/shorts/dQw4w9WgXcQ",
		"https://www.youtube.com/embed/dQw4w9WgXcQ",
		"https://www.youtube.com/live/dQw4w9WgXcQ",
		"http://www.youtube.com/watch?v=dQw4w9WgXcQ",
		"HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ"
	})
	void recognisesTheCommonYoutubeUrlForms(String href) {
		assertThat(VideoEmbed.embedUrl(href)).contains(EMBED);
	}

	@Test
	void keepsANumericStartTime() {
		assertThat(VideoEmbed.embedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s")).contains(EMBED + "?start=90");
		assertThat(VideoEmbed.embedUrl("https://youtu.be/dQw4w9WgXcQ?t=42")).contains(EMBED + "?start=42");
		assertThat(VideoEmbed.embedUrl("https://youtu.be/dQw4w9WgXcQ?t=1m30s")).contains(EMBED); // unsupported form: ignored
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"https://vimeo.com/123456",
		"https://example.com/watch?v=dQw4w9WgXcQ",
		"https://www.youtube.com/watch?v=tooshort",
		"https://www.youtube.com/watch?v=dQw4w9WgXcQ<script>",
		"https://www.youtube.com/user/somebody",
		"https://www.youtube.com/",
		"https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
		"javascript:alert(1)",
		"/pages/123",
		"not a url"
	})
	void ignoresEverythingElse(String href) {
		assertThat(VideoEmbed.embedUrl(href)).isEmpty();
	}

	@Test
	void paragraphWithOnlyAYoutubeLinkGetsAPlayerBelowTheLink() {
		String html = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"link","href":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","content":[{"type":"text","text":"Demo","styles":{}}]}
			]}]
			""");
		assertThat(html).startsWith("<p><a href=\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\" rel=\"noopener\">Demo</a></p>");
		assertThat(html).contains("<div class=\"video\"><iframe src=\"" + EMBED + "\" title=\"Demo\"");
		assertThat(html).contains("allowfullscreen").endsWith("</iframe></div>");
	}

	@Test
	void whitespaceAroundTheLinkIsAllowed() {
		String html = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"text","text":" ","styles":{}},
			  {"type":"link","href":"https://youtu.be/dQw4w9WgXcQ","content":[{"type":"text","text":"https://youtu.be/dQw4w9WgXcQ","styles":{}}]},
			  {"type":"text","text":"  ","styles":{}}
			]}]
			""");
		assertThat(html).contains("<iframe src=\"" + EMBED + "\"");
	}

	@Test
	void linkInsideRunningTextOrNextToAnotherLinkStaysALink() {
		String inText = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"text","text":"Kijk ","styles":{}},
			  {"type":"link","href":"https://youtu.be/dQw4w9WgXcQ","content":[{"type":"text","text":"hier","styles":{}}]}
			]}]
			""");
		String twoLinks = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"link","href":"https://youtu.be/dQw4w9WgXcQ","content":[{"type":"text","text":"een","styles":{}}]},
			  {"type":"link","href":"https://youtu.be/dQw4w9WgXcQ","content":[{"type":"text","text":"twee","styles":{}}]}
			]}]
			""");
		String heading = renderer.render("""
			[{"type":"heading","props":{"level":2},"content":[
			  {"type":"link","href":"https://youtu.be/dQw4w9WgXcQ","content":[{"type":"text","text":"Kop","styles":{}}]}
			]}]
			""");
		assertThat(inText).doesNotContain("<iframe");
		assertThat(twoLinks).doesNotContain("<iframe");
		assertThat(heading).doesNotContain("<iframe");
	}

	@Test
	void linkTextIsEscapedInThePlayerTitle() {
		String html = renderer.render("""
			[{"type":"paragraph","content":[
			  {"type":"link","href":"https://youtu.be/dQw4w9WgXcQ","content":[{"type":"text","text":"\\"><script>x</script>","styles":{}}]}
			]}]
			""");
		assertThat(html).doesNotContain("<script>").contains("title=\"&quot;&gt;&lt;script&gt;x&lt;/script&gt;\"");
	}

}
