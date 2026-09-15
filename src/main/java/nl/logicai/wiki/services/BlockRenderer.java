package nl.logicai.wiki.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the supported BlockNote subset to safe server-side HTML (spec section 7, "Lezen en zoeken").
 * All text is escaped; only whitelisted elements and attributes are emitted. The read view therefore
 * never depends on the browser editor.
 */
@Component
public class BlockRenderer {

	private final ObjectMapper mapper;

	public BlockRenderer(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public String render(String documentJson) {
		return render(mapper.readTree(documentJson));
	}

	public String render(JsonNode blocks) {
		StringBuilder html = new StringBuilder();
		renderBlocks(blocks, html);
		return html.toString();
	}

	private void renderBlocks(JsonNode blocks, StringBuilder html) {
		if (blocks == null || !blocks.isArray()) {
			return;
		}
		List<JsonNode> list = new ArrayList<>(blocks.values());
		int i = 0;
		while (i < list.size()) {
			String type = typeOf(list.get(i));
			if (isListItem(type)) {
				String tag = "numberedListItem".equals(type) ? "ol" : "ul";
				html.append('<').append(tag);
				if ("checkListItem".equals(type)) {
					html.append(" class=\"checklist\"");
				}
				html.append('>');
				while (i < list.size() && type.equals(typeOf(list.get(i)))) {
					renderListItem(list.get(i), html);
					i++;
				}
				html.append("</").append(tag).append('>');
			}
			else {
				renderBlock(list.get(i), html);
				i++;
			}
		}
	}

	private void renderBlock(JsonNode block, StringBuilder html) {
		String type = typeOf(block);
		JsonNode content = block.path("content");
		switch (type) {
			case "heading" -> {
				int level = Math.clamp(block.path("props").path("level").asInt(1), 1, 6);
				html.append("<h").append(level).append('>');
				renderInline(content, html);
				html.append("</h").append(level).append('>');
			}
			case "codeBlock" -> {
				String language = block.path("props").path("language").asString("");
				html.append("<pre><code");
				if (!language.isBlank() && language.matches("[A-Za-z0-9+#._-]{1,32}")) {
					html.append(" class=\"language-").append(language).append('"');
				}
				html.append('>');
				renderPlainText(content, html);
				html.append("</code></pre>");
			}
			case "quote" -> {
				html.append("<blockquote><p>");
				renderInline(content, html);
				html.append("</p></blockquote>");
			}
			case "divider" -> html.append("<hr>");
			default -> {
				html.append("<p>");
				renderInline(content, html);
				html.append("</p>");
			}
		}
		renderChildren(block, html);
	}

	private void renderListItem(JsonNode block, StringBuilder html) {
		html.append("<li>");
		if ("checkListItem".equals(typeOf(block))) {
			boolean checked = block.path("props").path("checked").asBoolean(false);
			html.append("<input type=\"checkbox\" disabled");
			if (checked) {
				html.append(" checked");
			}
			html.append("> ");
		}
		renderInline(block.path("content"), html);
		renderChildren(block, html);
		html.append("</li>");
	}

	private void renderChildren(JsonNode block, StringBuilder html) {
		JsonNode children = block.path("children");
		if (children.isArray() && !children.isEmpty()) {
			html.append("<div class=\"block-children\">");
			renderBlocks(children, html);
			html.append("</div>");
		}
	}

	private void renderInline(JsonNode content, StringBuilder html) {
		if (!content.isArray()) {
			return;
		}
		for (JsonNode item : content.values()) {
			String type = item.path("type").asString("");
			if ("link".equals(type)) {
				String href = LinkPolicy.sanitize(item.path("href").asString(""));
				html.append("<a href=\"").append(escape(href)).append('"');
				if (href.startsWith("http://") || href.startsWith("https://")) {
					html.append(" rel=\"noopener\"");
				}
				html.append('>');
				renderInline(item.path("content"), html);
				html.append("</a>");
			}
			else if ("text".equals(type)) {
				renderStyledText(item, html);
			}
		}
	}

	private void renderStyledText(JsonNode item, StringBuilder html) {
		JsonNode styles = item.path("styles");
		List<String> tags = new ArrayList<>();
		if (styles.path("bold").asBoolean(false)) {
			tags.add("strong");
		}
		if (styles.path("italic").asBoolean(false)) {
			tags.add("em");
		}
		if (styles.path("underline").asBoolean(false)) {
			tags.add("u");
		}
		if (styles.path("strike").asBoolean(false)) {
			tags.add("s");
		}
		if (styles.path("code").asBoolean(false)) {
			tags.add("code");
		}
		for (String tag : tags) {
			html.append('<').append(tag).append('>');
		}
		html.append(escape(item.path("text").asString("")));
		for (int i = tags.size() - 1; i >= 0; i--) {
			html.append("</").append(tags.get(i)).append('>');
		}
	}

	private void renderPlainText(JsonNode content, StringBuilder html) {
		if (!content.isArray()) {
			return;
		}
		for (JsonNode item : content.values()) {
			html.append(escape(item.path("text").asString("")));
		}
	}

	private static String typeOf(JsonNode block) {
		return block.path("type").asString("paragraph");
	}

	private static boolean isListItem(String type) {
		return "bulletListItem".equals(type) || "numberedListItem".equals(type) || "checkListItem".equals(type);
	}

	private static String escape(String text) {
		return HtmlUtils.htmlEscape(text, "UTF-8");
	}

}
