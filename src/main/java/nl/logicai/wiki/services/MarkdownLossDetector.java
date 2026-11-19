package nl.logicai.wiki.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * Names the formatting of a block that Markdown cannot express (docs/markdown-export.md: colours, the chosen
 * image width, alignment, nesting under a non-list block). The Markdown export drops it silently by design;
 * an AI agent that rewrites a page from that Markdown would make the loss permanent, so the MCP tools show
 * these descriptions as warnings when a page is read and refuse to overwrite such a block unasked.
 * The texts are English because their reader is the agent.
 */
public final class MarkdownLossDetector {

	private static final Set<String> LIST_ITEMS = Set.of("bulletListItem", "numberedListItem", "checkListItem");

	private MarkdownLossDetector() {
	}

	/** One line per top-level block that carries such formatting; empty when Markdown says it all. */
	public static List<String> inspect(JsonNode document) {
		List<String> found = new ArrayList<>();
		if (document != null && document.isArray()) {
			int position = 1;
			for (JsonNode block : document.values()) {
				found.addAll(describe(block, position++));
			}
		}
		return found;
	}

	/**
	 * @param position the block's place among the top-level blocks, starting at 1
	 * @return at most one line, such as {@code Block 3 (paragraph "Let op: …"): text colour, nested blocks}
	 */
	public static List<String> describe(JsonNode block, int position) {
		Set<String> features = new LinkedHashSet<>();
		collect(block, features);
		if (features.isEmpty()) {
			return List.of();
		}
		String type = block.path("type").asString("paragraph");
		String text = MarkdownImporter.plainText(block.path("content")).strip().replaceAll("\\s+", " ");
		if (text.isEmpty()) {
			text = block.path("props").path("name").asString("");
		}
		if (text.length() > 40) {
			text = text.substring(0, 40) + "…";
		}
		return List.of("Block " + position + " (" + type + (text.isEmpty() ? "" : " \"" + text + "\"") + "): "
			+ String.join(", ", features));
	}

	private static void collect(JsonNode block, Set<String> features) {
		JsonNode props = block.path("props");
		if (isColour(props.path("textColor"))) {
			features.add("text colour");
		}
		if (isColour(props.path("backgroundColor"))) {
			features.add("background colour");
		}
		String alignment = props.path("textAlignment").asString("left");
		if (!alignment.isEmpty() && !"left".equals(alignment)) {
			features.add("text alignment");
		}
		if (props.path("previewWidth").isNumber()) {
			features.add("display width");
		}
		collectInline(block.path("content"), features);

		JsonNode children = block.path("children");
		if (children.isArray() && !children.isEmpty()) {
			if (!LIST_ITEMS.contains(block.path("type").asString("paragraph"))) {
				features.add("nested blocks");
			}
			for (JsonNode child : children.values()) {
				collect(child, features);
			}
		}
	}

	private static void collectInline(JsonNode content, Set<String> features) {
		if (!content.isArray()) {
			return;
		}
		for (JsonNode item : content.values()) {
			JsonNode styles = item.path("styles");
			if (isColour(styles.path("textColor"))) {
				features.add("text colour");
			}
			if (isColour(styles.path("backgroundColor"))) {
				features.add("background colour");
			}
			collectInline(item.path("content"), features);
		}
	}

	private static boolean isColour(JsonNode value) {
		return value.isString() && !value.stringValue().isBlank() && !"default".equals(value.stringValue());
	}

}
