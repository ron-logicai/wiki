package nl.logicai.wiki.services;

import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.config.WikiLimits;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the documented BlockNote subset (spec section 7) and derives the search text.
 * Unknown or invalid content is rejected with an error, never silently dropped.
 */
@Component
public class DocumentValidator {

	public static final int SCHEMA_VERSION = 1;

	/**
	 * Blocks that point at a file uploaded to this wiki (spec U-01: "image" for PNG/JPEG, "file" for PDF;
	 * "video" for MP4/WebM as an extension), see AttachmentService. Keyed by the Dutch noun used in messages.
	 */
	static final Map<String, String> FILE_BLOCK_TYPES = Map.of(
		"image", "afbeelding", "file", "bijlage", "video", "video");

	/** The documented subset (spec section 7) plus the file blocks above. */
	static final Set<String> BLOCK_TYPES = Set.of(
		"paragraph", "heading", "bulletListItem", "numberedListItem",
		"checkListItem", "codeBlock", "quote", "divider", "image", "file", "video");

	private static final int MAX_CAPTION_LENGTH = 500;
	private static final int MAX_PREVIEW_WIDTH = 4_000;

	static final Set<String> STYLE_KEYS = Set.of(
		"bold", "italic", "underline", "strike", "code", "textColor", "backgroundColor");

	private static final int MAX_DEPTH = 10;
	private static final int MAX_BLOCKS = 5_000;

	private final ObjectMapper mapper;
	private final WikiLimits limits;

	public DocumentValidator(ObjectMapper mapper, WikiLimits limits) {
		this.mapper = mapper;
		this.limits = limits;
	}

	public String normalizeTitle(String title) {
		String trimmed = title == null ? "" : title.strip();
		if (trimmed.isEmpty()) {
			throw new InvalidContentException("Een titel is verplicht.");
		}
		if (trimmed.length() > limits.titleMaxLength()) {
			throw new InvalidContentException(
				"De titel mag maximaal " + limits.titleMaxLength() + " tekens bevatten.");
		}
		return trimmed;
	}

	public WikiDocument validate(JsonNode root) {
		if (root == null || !root.isArray()) {
			throw new InvalidContentException("Het document moet een lijst van blokken zijn.");
		}
		StringBuilder text = new StringBuilder();
		int[] blockCount = {0};
		for (JsonNode block : root.values()) {
			validateBlock(block, 0, text, blockCount);
		}
		String json = mapper.writeValueAsString(root);
		long bytes = json.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > limits.documentMaxBytes()) {
			throw new InvalidContentException(
				"Het document is te groot (" + bytes + " bytes, maximum " + limits.documentMaxBytes() + ").");
		}
		return new WikiDocument(json, normalizeWhitespace(text), SCHEMA_VERSION);
	}

	private void validateBlock(JsonNode block, int depth, StringBuilder text, int[] blockCount) {
		if (depth > MAX_DEPTH) {
			throw new InvalidContentException("Blokken mogen maximaal " + MAX_DEPTH + " niveaus diep genest zijn.");
		}
		if (++blockCount[0] > MAX_BLOCKS) {
			throw new InvalidContentException("Een pagina mag maximaal " + MAX_BLOCKS + " blokken bevatten.");
		}
		if (!block.isObject()) {
			throw new InvalidContentException("Een blok moet een object zijn.");
		}
		String type = stringOrNull(block.path("type"));
		if (type == null || !BLOCK_TYPES.contains(type)) {
			throw new InvalidContentException("Bloktype wordt niet ondersteund: " + type);
		}
		JsonNode id = block.path("id");
		if (!id.isMissingNode() && (!id.isString() || id.stringValue().length() > 64)) {
			throw new InvalidContentException("Een blok-id moet een korte tekst zijn.");
		}
		JsonNode props = block.path("props");
		if (!props.isMissingNode() && !props.isObject()) {
			throw new InvalidContentException("Blokeigenschappen moeten een object zijn.");
		}
		if ("heading".equals(type)) {
			int level = props.path("level").asInt(1);
			if (level < 1 || level > 6) {
				throw new InvalidContentException("Kopniveau moet tussen 1 en 6 liggen.");
			}
		}

		if (FILE_BLOCK_TYPES.containsKey(type)) {
			validateFileBlock(type, props, text);
		}

		JsonNode content = block.path("content");
		if ("divider".equals(type) || FILE_BLOCK_TYPES.containsKey(type)) {
			if (!content.isMissingNode() && !(content.isArray() && content.isEmpty())) {
				throw new InvalidContentException(
					"Een scheidingslijn, afbeelding, bijlage of video heeft geen tekstinhoud.");
			}
		}
		else if (!content.isMissingNode()) {
			if (!content.isArray()) {
				throw new InvalidContentException("Blokinhoud moet een lijst zijn.");
			}
			validateInline(content, "codeBlock".equals(type), text);
			text.append('\n');
		}

		JsonNode children = block.path("children");
		if (!children.isMissingNode()) {
			if (!children.isArray()) {
				throw new InvalidContentException("Onderliggende blokken moeten een lijst zijn.");
			}
			for (JsonNode child : children.values()) {
				validateBlock(child, depth + 1, text, blockCount);
			}
		}
	}

	/**
	 * An image, attachment or video points at a file uploaded to this wiki (never an external URL); name and
	 * caption are searchable text. An image may carry the display width the editor chose.
	 */
	private void validateFileBlock(String type, JsonNode props, StringBuilder text) {
		String noun = FILE_BLOCK_TYPES.get(type);
		JsonNode url = props.path("url");
		if (!url.isString() || !AttachmentService.isInternalUrl(url.stringValue())) {
			throw new InvalidContentException("Een " + noun + " moet een bestand zijn dat in deze wiki is geüpload.");
		}
		for (String key : new String[] {"name", "caption"}) {
			JsonNode value = props.path(key);
			if (value.isMissingNode()) {
				continue;
			}
			if (!value.isString() || value.stringValue().length() > MAX_CAPTION_LENGTH) {
				throw new InvalidContentException("De naam of het bijschrift van een " + noun + " is te lang.");
			}
			text.append(value.stringValue()).append(' ');
		}
		JsonNode width = props.path("previewWidth");
		if (!width.isMissingNode() && !width.isNull()
				&& (!width.isNumber() || width.asInt(0) < 1 || width.asInt(0) > MAX_PREVIEW_WIDTH)) {
			throw new InvalidContentException("De breedte van een " + noun + " moet tussen 1 en "
				+ MAX_PREVIEW_WIDTH + " pixels liggen.");
		}
		text.append('\n');
	}

	private void validateInline(JsonNode content, boolean plainOnly, StringBuilder text) {
		for (JsonNode item : content.values()) {
			if (!item.isObject()) {
				throw new InvalidContentException("Tekstinhoud moet uit objecten bestaan.");
			}
			String type = stringOrNull(item.path("type"));
			if ("text".equals(type)) {
				JsonNode value = item.path("text");
				if (!value.isString()) {
					throw new InvalidContentException("Een tekstfragment moet een tekstwaarde hebben.");
				}
				validateStyles(item.path("styles"));
				text.append(value.stringValue()).append(' ');
			}
			else if ("link".equals(type) && !plainOnly) {
				String href = stringOrNull(item.path("href"));
				if (!LinkPolicy.isAllowed(href)) {
					throw new InvalidContentException("Linkbestemming wordt niet ondersteund: " + href);
				}
				JsonNode linkContent = item.path("content");
				if (!linkContent.isArray()) {
					throw new InvalidContentException("Een link moet tekstinhoud hebben.");
				}
				validateInline(linkContent, true, text);
			}
			else {
				throw new InvalidContentException("Inhoudstype wordt niet ondersteund: " + type);
			}
		}
	}

	private void validateStyles(JsonNode styles) {
		if (styles.isMissingNode()) {
			return;
		}
		if (!styles.isObject()) {
			throw new InvalidContentException("Tekstopmaak moet een object zijn.");
		}
		for (var entry : styles.properties()) {
			if (!STYLE_KEYS.contains(entry.getKey())) {
				throw new InvalidContentException("Tekstopmaak wordt niet ondersteund: " + entry.getKey());
			}
		}
	}

	private static String stringOrNull(JsonNode node) {
		return node.isString() ? node.stringValue() : null;
	}

	private static String normalizeWhitespace(CharSequence text) {
		return text.toString().replaceAll("\\s+", " ").strip();
	}

}
