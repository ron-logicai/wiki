package nl.logicai.wiki.services;

import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.config.WikiLimits;
import java.nio.charset.StandardCharsets;
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

	static final Set<String> BLOCK_TYPES = Set.of(
		"paragraph", "heading", "bulletListItem", "numberedListItem",
		"checkListItem", "codeBlock", "quote", "divider");

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

		JsonNode content = block.path("content");
		if ("divider".equals(type)) {
			if (!content.isMissingNode() && !(content.isArray() && content.isEmpty())) {
				throw new InvalidContentException("Een scheidingslijn heeft geen inhoud.");
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
