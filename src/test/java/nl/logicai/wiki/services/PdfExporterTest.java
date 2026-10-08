package nl.logicai.wiki.services;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import nl.logicai.wiki.exceptions.AttachmentNotFoundException;
import nl.logicai.wiki.models.Attachment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The PDF export renders the same HTML as the read view into a self-contained file (docs/markdown-export.md). */
class PdfExporterTest {

	private static final String BASE = "https://wiki.example.test";
	private static final UUID IMAGE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	private final AttachmentService attachments = mock(AttachmentService.class);
	private final PdfExporter exporter = new PdfExporter(new BlockRenderer(new ObjectMapper()), attachments, templateEngine());

	private static SpringTemplateEngine templateEngine() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(resolver);
		return engine;
	}

	@Test
	void pageProducesAPdfWithTitleMetadataAndContent() throws IOException {
		var meta = new PdfExporter.PageMeta("Deploy handleiding", List.of("ops", "java"),
			BASE + "/pages/0f0f", 3, Instant.parse("2026-09-18T10:00:00Z"));
		byte[] pdf = exporter.exportPage(meta, """
			[{"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Stappen","styles":{}}],"children":[]},
			 {"type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Klaar","styles":{"bold":true}}],"children":[]},
			 {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Nog niet","styles":{}}],"children":[]},
			 {"type":"codeBlock","props":{"language":"bash"},"content":[{"type":"text","text":"mvn verify","styles":{}}],"children":[]}]
			""", BASE);

		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		String text = text(pdf);
		assertThat(text).contains("Deploy handleiding");
		assertThat(text).contains("Versie 3");
		assertThat(text).contains("18 september 2026");
		assertThat(text).contains("ops").contains("java");
		assertThat(text).contains("Stappen");
		assertThat(text).contains("[x]").contains("Klaar");
		assertThat(text).contains("[ ]").contains("Nog niet");
		assertThat(text).contains("mvn verify");
		// Footer: the permanent page link (spec section 5) and page numbers.
		assertThat(text).contains(BASE + "/pages/0f0f");
		assertThat(text).contains("Pagina 1 van 1");
	}

	@Test
	void emptyDocumentStillGivesAPdf() throws IOException {
		var meta = new PdfExporter.PageMeta("Leeg", List.of(), null, 1, null);
		String text = text(exporter.exportPage(meta, "[]", BASE));
		assertThat(text).contains("Leeg").contains("Versie 1");
	}

	@Test
	void internalImageIsEmbeddedAsDataUri() throws IOException {
		when(attachments.open(IMAGE_ID)).thenReturn(new AttachmentService.Stored(
			Attachment.create(IMAGE_ID, UUID.randomUUID(), "schema.png", "image/png", 10, "editor", Instant.now()),
			new ByteArrayResource(png())));

		String html = exporter.prepareContent(
			"<figure class=\"afbeelding\"><a href=\"/attachments/" + IMAGE_ID + "\" target=\"_blank\" rel=\"noopener\">"
				+ "<img src=\"/attachments/" + IMAGE_ID + "\" alt=\"schema.png\" loading=\"lazy\" width=\"300\"></a>"
				+ "<figcaption>Het schema</figcaption></figure>", BASE);

		assertThat(html).contains("src=\"data:image/png;base64,");
		assertThat(html).doesNotContain("loading=").doesNotContain("target=");
		assertThat(html).contains("href=\"" + BASE + "/attachments/" + IMAGE_ID + "\"");

		byte[] pdf = exporter.exportPage(new PdfExporter.PageMeta("Met plaatje", List.of(), null, 1, null), """
			[{"type":"image","props":{"url":"/attachments/%s","name":"schema.png","caption":"Het schema","showPreview":true,"previewWidth":300},"content":[],"children":[]}]
			""".formatted(IMAGE_ID), BASE);
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertThat(doc.getPage(0).getResources().getXObjectNames()).isNotEmpty();
		}
		assertThat(text(pdf)).contains("Het schema");
	}

	@Test
	void missingAttachmentBecomesANoteInsteadOfAnError() {
		when(attachments.open(any())).thenThrow(new AttachmentNotFoundException(IMAGE_ID));

		String html = exporter.prepareContent("<img src=\"/attachments/" + IMAGE_ID + "\" alt=\"x\">", BASE);

		assertThat(html).contains("Afbeelding ontbreekt").doesNotContain("<img");
	}

	@Test
	void videosBecomeLinksAndYoutubePlayerIsDropped() {
		String html = exporter.prepareContent(
			"<figure class=\"video-bestand\"><video controls preload=\"metadata\" src=\"/attachments/" + IMAGE_ID + "\"></video>"
				+ "<figcaption>Demo</figcaption></figure>"
				+ "<p><a href=\"https://youtu.be/abc\" rel=\"noopener\">Kijk</a></p>"
				+ "<div class=\"video\"><iframe src=\"https://www.youtube-nocookie.com/embed/abc\"></iframe></div>", BASE);

		assertThat(html).doesNotContain("<video").doesNotContain("<iframe");
		assertThat(html).contains("href=\"" + BASE + "/attachments/" + IMAGE_ID + "\"").contains("Video: Demo");
		assertThat(html).contains("href=\"https://youtu.be/abc\"").contains("Kijk");
	}

	@Test
	void internalLinksBecomeAbsoluteAndCheckboxesBecomeText() {
		String html = exporter.prepareContent(
			"<ul class=\"checklist\"><li><input type=\"checkbox\" disabled checked> Zie <a href=\"/pages/0f0f\">daar</a></li>"
				+ "<li><input type=\"checkbox\" disabled> Open</li></ul>", BASE);

		assertThat(html).contains("href=\"" + BASE + "/pages/0f0f\"");
		assertThat(html).doesNotContain("<input");
		assertThat(html).contains("[x]").contains("[ ]");
	}

	private static String text(byte[] pdf) throws IOException {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(doc);
		}
	}

	private static byte[] png() throws IOException {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

}
