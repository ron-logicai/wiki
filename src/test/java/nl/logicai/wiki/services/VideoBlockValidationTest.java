package nl.logicai.wiki.services;

import nl.logicai.wiki.config.WikiLimits;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.WikiDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The "video" block: an uploaded file of this wiki, with name and caption in the search text. */
class VideoBlockValidationTest {

	private static final String URL = "/attachments/11111111-1111-4111-8111-111111111101";

	private final ObjectMapper mapper = new ObjectMapper();
	private final DocumentValidator validator = new DocumentValidator(mapper, new WikiLimits(200, 2_097_152));
	private final BlockRenderer renderer = new BlockRenderer(mapper);

	@Test
	void uploadedVideoIsAcceptedAndSearchable() {
		WikiDocument doc = validator.validate(mapper.readTree("""
			[{"id":"v1","type":"video","props":{"url":"%s","name":"demo.mp4","caption":"Korte demo","showPreview":true},"children":[]}]
			""".formatted(URL)));
		assertThat(doc.searchText()).isEqualTo("demo.mp4 Korte demo");
	}

	@Test
	void videoWithAnExternalUrlIsRefused() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"video","props":{"url":"https://example.com/film.mp4"}}]
			""")))
			.isInstanceOf(InvalidContentException.class)
			.hasMessageContaining("geüpload");
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"video","props":{}}]
			""")))
			.isInstanceOf(InvalidContentException.class);
	}

	@Test
	void videoWithTextContentOrLongCaptionIsRefused() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"video","props":{"url":"%s"},"content":[{"type":"text","text":"x","styles":{}}]}]
			""".formatted(URL))))
			.isInstanceOf(InvalidContentException.class);
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"video","props":{"url":"%s","caption":"%s"}}]
			""".formatted(URL, "a".repeat(501)))))
			.isInstanceOf(InvalidContentException.class);
	}

	@Test
	void readViewRendersAPlayerOnlyForInternalUrls() {
		String html = renderer.render("""
			[{"type":"video","props":{"url":"%s","caption":"<b>Demo</b>"}}]
			""".formatted(URL));
		assertThat(html).isEqualTo("<figure class=\"video-bestand\"><video controls preload=\"metadata\" src=\"" + URL
			+ "\"></video><figcaption>&lt;b&gt;Demo&lt;/b&gt;</figcaption></figure>");

		String external = renderer.render("""
			[{"type":"video","props":{"url":"https://evil.example/x.mp4"}}]
			""");
		assertThat(external).doesNotContain("<video");
	}

}
