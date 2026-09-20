package nl.logicai.wiki.services;

import nl.logicai.wiki.config.WikiLimits;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.WikiDocument;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The file blocks "image" (PNG/JPEG), "file" (PDF) and "video": each points at a file uploaded to this wiki,
 * with name and caption in the search text (spec U-01).
 */
class FileBlockValidationTest {

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
	void uploadedImageAndPdfAreAcceptedAndSearchable() {
		WikiDocument doc = validator.validate(mapper.readTree("""
			[{"type":"image","props":{"url":"%s","name":"schema.png","caption":"Netwerkschema","showPreview":true,"previewWidth":512},"children":[]},
			 {"type":"file","props":{"url":"%s","name":"handleiding.pdf","caption":"Versie 2"},"children":[]}]
			""".formatted(URL, URL)));
		assertThat(doc.searchText()).isEqualTo("schema.png Netwerkschema handleiding.pdf Versie 2");
	}

	@Test
	void fileBlocksWithAnExternalUrlAreRefused() {
		for (String type : new String[] {"image", "file", "video"}) {
			assertThatThrownBy(() -> validator.validate(mapper.readTree("""
				[{"type":"%s","props":{"url":"https://example.com/bestand"}}]
				""".formatted(type))))
				.as(type)
				.isInstanceOf(InvalidContentException.class)
				.hasMessageContaining("geüpload");
			assertThatThrownBy(() -> validator.validate(mapper.readTree("""
				[{"type":"%s","props":{}}]
				""".formatted(type))))
				.as(type)
				.isInstanceOf(InvalidContentException.class);
		}
	}

	@Test
	void fileBlocksWithTextContentOrLongCaptionAreRefused() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"image","props":{"url":"%s"},"content":[{"type":"text","text":"x","styles":{}}]}]
			""".formatted(URL))))
			.isInstanceOf(InvalidContentException.class);
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"file","props":{"url":"%s","caption":"%s"}}]
			""".formatted(URL, "a".repeat(501)))))
			.isInstanceOf(InvalidContentException.class)
			.hasMessageContaining("bijlage");
	}

	@Test
	void imageWidthMustBeAPlausibleNumber() {
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"image","props":{"url":"%s","previewWidth":"breed"}}]
			""".formatted(URL))))
			.isInstanceOf(InvalidContentException.class)
			.hasMessageContaining("breedte");
		assertThatThrownBy(() -> validator.validate(mapper.readTree("""
			[{"type":"image","props":{"url":"%s","previewWidth":0}}]
			""".formatted(URL))))
			.isInstanceOf(InvalidContentException.class);
		// BlockNote leaves the width unset until the user resizes; null and absence are both fine.
		validator.validate(mapper.readTree("""
			[{"type":"image","props":{"url":"%s","previewWidth":null}}]
			""".formatted(URL)));
	}

	@Test
	void readViewRendersAnImageOnlyForInternalUrls() {
		String html = renderer.render("""
			[{"type":"image","props":{"url":"%s","name":"a\\"b.png","caption":"<b>Schema</b>","previewWidth":300}}]
			""".formatted(URL));
		assertThat(html).isEqualTo("<figure class=\"afbeelding\"><a href=\"" + URL + "\" target=\"_blank\" rel=\"noopener\">"
			+ "<img src=\"" + URL + "\" alt=\"a&quot;b.png\" loading=\"lazy\" width=\"300\"></a>"
			+ "<figcaption>&lt;b&gt;Schema&lt;/b&gt;</figcaption></figure>");

		String asLink = renderer.render("""
			[{"type":"image","props":{"url":"%s","name":"schema.png","showPreview":false}}]
			""".formatted(URL));
		assertThat(asLink).isEqualTo("<figure class=\"bijlage\"><a href=\"" + URL
			+ "\" target=\"_blank\" rel=\"noopener\">schema.png</a></figure>");

		String external = renderer.render("""
			[{"type":"image","props":{"url":"https://evil.example/x.png"}}]
			""");
		assertThat(external).doesNotContain("<img");
	}

	@Test
	void readViewRendersAPdfAsALink() {
		String html = renderer.render("""
			[{"type":"file","props":{"url":"%s","name":"","caption":"Handleiding"}}]
			""".formatted(URL));
		assertThat(html).isEqualTo("<figure class=\"bijlage\"><a href=\"" + URL
			+ "\" target=\"_blank\" rel=\"noopener\">Bijlage</a><figcaption>Handleiding</figcaption></figure>");

		String external = renderer.render("""
			[{"type":"file","props":{"url":"javascript:alert(1)","name":"x"}}]
			""");
		assertThat(external).doesNotContain("<a");
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
