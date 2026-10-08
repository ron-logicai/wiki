package nl.logicai.wiki.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import nl.logicai.wiki.exceptions.AttachmentNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Exports a page as PDF (an extra next to the Markdown export of spec F-17, with the same authorisation
 * rules). The PDF is rendered from the same HTML the read view shows ({@link BlockRenderer}), so there is
 * no third translation of the block subset; the layout lives in {@code templates/export-pdf.html}.
 *
 * <p>What differs from the read view, documented in {@code docs/markdown-export.md}:
 * <ul>
 *   <li>uploaded images are embedded, so the PDF needs no wiki session to show them</li>
 *   <li>uploaded videos and YouTube embeds become a link; a PDF cannot play them</li>
 *   <li>application-relative links such as {@code /pages/{id}} are made absolute with the wiki base URL</li>
 *   <li>checkboxes become {@code [x]} / {@code [ ]} text</li>
 *   <li>the standard PDF fonts are used, so emoji and non-Latin scripts are not rendered</li>
 * </ul>
 */
@Component
public class PdfExporter {

	private static final DateTimeFormatter DATUM = DateTimeFormatter
		.ofPattern("d MMMM yyyy HH:mm", Locale.forLanguageTag("nl-NL"))
		.withZone(ZoneId.of("Europe/Amsterdam"));

	private final BlockRenderer blockRenderer;
	private final AttachmentService attachments;
	private final SpringTemplateEngine templateEngine;

	public PdfExporter(BlockRenderer blockRenderer, AttachmentService attachments, SpringTemplateEngine templateEngine) {
		this.blockRenderer = blockRenderer;
		this.attachments = attachments;
		this.templateEngine = templateEngine;
	}

	/** Everything that ends up above the content: identity (spec section 5), tags and version. */
	public record PageMeta(String title, List<String> tags, String pageUrl, int version, Instant updatedAt) {
	}

	/**
	 * A complete PDF: title, metadata line, the content and a footer with the page link and page numbers.
	 *
	 * @param baseUrl the wiki root without trailing slash, used to make internal links absolute
	 * @throws IllegalStateException when the PDF cannot be produced; the export never silently returns an empty file
	 */
	public byte[] exportPage(PageMeta meta, String documentJson, String baseUrl) {
		String inhoud = prepareContent(blockRenderer.render(documentJson), baseUrl);

		Context context = new Context(Locale.forLanguageTag("nl-NL"));
		context.setVariable("titel", meta.title());
		context.setVariable("tags", meta.tags() == null ? List.of() : meta.tags());
		context.setVariable("versie", meta.version());
		context.setVariable("bijgewerkt", meta.updatedAt() == null ? null : DATUM.format(meta.updatedAt()));
		context.setVariable("bron", meta.pageUrl() == null ? "" : meta.pageUrl());
		context.setVariable("inhoudHtml", inhoud);
		String html = templateEngine.process("export-pdf", context);

		// OpenHTMLtoPDF needs well-formed XHTML; jsoup repairs the HTML and hands over a W3C DOM.
		org.w3c.dom.Document xhtml = new W3CDom().fromJsoup(Jsoup.parse(html));
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withW3cDocument(xhtml, baseUrl);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("De PDF kon niet worden gemaakt.", ex);
		}
	}

	/**
	 * Adapts the read-view HTML to a document that stands on its own: embedded images, links instead of
	 * players, absolute links and plain text instead of form controls. Package-private for the unit test.
	 */
	String prepareContent(String inhoudHtml, String baseUrl) {
		Document doc = Jsoup.parseBodyFragment(inhoudHtml);

		for (Element img : doc.select("img[src]")) {
			embedImage(img);
		}
		for (Element figure : doc.select("figure.video-bestand")) {
			figure.replaceWith(videoLink(figure));
		}
		// The YouTube player sits under the paragraph that holds the link itself, so only the player goes.
		doc.select("div.video").remove();
		for (Element input : doc.select("input[type=checkbox]")) {
			input.replaceWith(new Element("span").addClass("vinkje").text(input.hasAttr("checked") ? "[x]" : "[ ]"));
		}
		for (Element a : doc.select("a[href^=/]")) {
			a.attr("href", baseUrl + a.attr("href"));
		}
		doc.select("a").removeAttr("target").removeAttr("rel");
		doc.select("img").removeAttr("loading");
		return doc.body().html();
	}

	private static Element videoLink(Element figure) {
		Element video = figure.selectFirst("video");
		String src = video == null ? "" : video.attr("src");
		Element caption = figure.selectFirst("figcaption");
		String label = caption == null || caption.text().isBlank() ? "Video" : "Video: " + caption.text();
		Element p = new Element("p");
		p.appendChild(new Element("a").attr("href", src).text(label));
		p.appendText(" (alleen af te spelen in de wiki)");
		return p;
	}

	/** Replaces the attachment URL by the bytes themselves; a missing file becomes a visible note, not an error. */
	private void embedImage(Element img) {
		Matcher m = AttachmentService.INTERNAL_URL.matcher(img.attr("src"));
		if (!m.matches()) {
			img.replaceWith(new Element("p").addClass("ontbreekt").text("Afbeelding niet beschikbaar"));
			return;
		}
		try {
			AttachmentService.Stored stored = attachments.open(UUID.fromString(m.group(1)));
			byte[] bytes = stored.file().getContentAsByteArray();
			img.attr("src", "data:" + stored.attachment().getContentType() + ";base64,"
				+ Base64.getEncoder().encodeToString(bytes));
		}
		catch (AttachmentNotFoundException | IOException ex) {
			img.replaceWith(new Element("p").addClass("ontbreekt").text("Afbeelding ontbreekt"));
		}
	}

}
