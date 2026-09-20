package nl.logicai.wiki.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exports a page as Markdown (spec F-17). The export is lossy by design (spec section 7); what is kept
 * and what is not is written down in {@code docs/markdown-export.md} and tested in MarkdownExporterTest,
 * so nothing is lost silently.
 *
 * <p>Mapping of the supported subset:
 * <ul>
 *   <li>paragraph: text, a blank line between blocks; a line break inside a paragraph becomes a hard break (trailing backslash)</li>
 *   <li>heading: {@code #} times the level (1–6)</li>
 *   <li>bulletListItem / numberedListItem / checkListItem: {@code - }, {@code 1. }, {@code - [ ] } / {@code - [x] }; nested children are indented</li>
 *   <li>codeBlock: fenced with backticks and the language; the fence grows when the code contains backticks</li>
 *   <li>quote: every line prefixed with {@code > }</li>
 *   <li>divider: {@code ---}</li>
 *   <li>image (uploaded PNG/JPEG): {@code ![name](url)} plus the caption; the bytes are not part of the export</li>
 *   <li>file and video (uploaded PDF or video): a link {@code [Bijlage: name](url)} / {@code [Video: name](url)} plus the caption</li>
 *   <li>bold, italic, strike, code: {@code **}, {@code *}, {@code ~~}, backticks; underline becomes {@code <u>} (no Markdown equivalent)</li>
 *   <li>textColor / backgroundColor: dropped, Markdown has no colours (documented loss)</li>
 *   <li>links: {@code [text](href)}; application-relative destinations such as {@code /pages/{id}} are made absolute with the wiki base URL</li>
 *   <li>children of a non-list block: exported after the block, at the same level (nesting has no Markdown form outside lists)</li>
 * </ul>
 * Text is escaped so that literal {@code *}, {@code _}, {@code #} and the like in the page do not turn into Markdown syntax.
 */
@Component
public class MarkdownExporter {

	private static final String INDENT = "    ";

	/** Characters that carry meaning anywhere in Markdown text. */
	private static final Pattern INLINE_SPECIAL = Pattern.compile("([\\\\`*_\\[\\]<>~])");

	/** Characters and sequences that start a block when they open a line. */
	private static final Pattern LINE_START_SPECIAL = Pattern.compile("(?m)^(\\s*)([#>+-]|\\d{1,9}[.)])(?=\\s|$)");

	private final ObjectMapper mapper;

	public MarkdownExporter(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	/** Everything that ends up above the content: identity (spec section 5), tags and version. */
	public record PageMeta(String title, List<String> tags, String pageUrl, int version, Instant updatedAt) {
	}

	/**
	 * A complete .md file: YAML front matter, the title as level-1 heading and the content.
	 *
	 * @param baseUrl the wiki root without trailing slash, used to make internal links absolute
	 */
	public String exportPage(PageMeta meta, String documentJson, String baseUrl) {
		StringBuilder md = new StringBuilder();
		md.append("---\n");
		md.append("title: ").append(yamlString(meta.title())).append('\n');
		if (meta.tags() != null && !meta.tags().isEmpty()) {
			md.append("tags:\n");
			for (String tag : meta.tags()) {
				md.append("  - ").append(yamlString(tag)).append('\n');
			}
		}
		if (meta.pageUrl() != null) {
			md.append("source: ").append(meta.pageUrl()).append('\n');
		}
		md.append("version: ").append(meta.version()).append('\n');
		if (meta.updatedAt() != null) {
			md.append("updated: ").append(meta.updatedAt()).append('\n');
		}
		md.append("---\n\n");
		md.append("# ").append(escapeInline(meta.title())).append('\n');
		String body = exportDocument(documentJson, baseUrl);
		if (!body.isEmpty()) {
			md.append('\n').append(body);
		}
		return md.toString();
	}

	/** Only the content, without front matter or title. Ends with a single newline unless empty. */
	public String exportDocument(String documentJson, String baseUrl) {
		return exportDocument(mapper.readTree(documentJson), baseUrl);
	}

	public String exportDocument(JsonNode blocks, String baseUrl) {
		List<Part> parts = new ArrayList<>();
		renderBlocks(blocks, baseUrl == null ? "" : baseUrl, parts);
		if (parts.isEmpty()) {
			return "";
		}
		return parts.stream().map(Part::markdown).collect(Collectors.joining("\n\n")) + "\n";
	}

	/**
	 * One rendered block (or one whole list) without trailing newline. {@code interruptsParagraph} says whether
	 * the block may directly follow a line of text (lists, headings, fences, quotes, rules) or needs a blank
	 * line first (paragraph-like blocks, which would otherwise continue the previous paragraph).
	 */
	private record Part(String markdown, boolean interruptsParagraph) {
	}

	/** Each element of {@code out} is one block (or one list) without a trailing newline. */
	private void renderBlocks(JsonNode blocks, String baseUrl, List<Part> out) {
		if (blocks == null || !blocks.isArray()) {
			return;
		}
		List<JsonNode> list = new ArrayList<>(blocks.values());
		int i = 0;
		while (i < list.size()) {
			String type = typeOf(list.get(i));
			if (isListItem(type)) {
				List<String> items = new ArrayList<>();
				int number = 1;
				while (i < list.size() && type.equals(typeOf(list.get(i)))) {
					items.add(renderListItem(list.get(i), number++, baseUrl));
					i++;
				}
				out.add(new Part(String.join("\n", items), true));
			}
			else {
				renderBlock(list.get(i), baseUrl, out);
				i++;
			}
		}
	}

	private void renderBlock(JsonNode block, String baseUrl, List<Part> out) {
		String type = typeOf(block);
		JsonNode content = block.path("content");
		switch (type) {
			case "heading" -> {
				int level = Math.clamp(block.path("props").path("level").asInt(1), 1, 6);
				out.add(new Part("#".repeat(level) + " " + renderInline(content, baseUrl).replace("\n", " "), true));
			}
			case "codeBlock" -> out.add(new Part(renderCode(block), true));
			case "quote" -> out.add(new Part(prefixLines(hardBreaks(renderInline(content, baseUrl)), "> "), true));
			case "divider" -> out.add(new Part("---", true));
			case "image" -> out.add(new Part(renderImage(block.path("props"), baseUrl), false));
			case "file" -> out.add(new Part(renderAttachment("Bijlage", block.path("props"), baseUrl), false));
			case "video" -> out.add(new Part(renderAttachment("Video", block.path("props"), baseUrl), false));
			default -> {
				String text = renderInline(content, baseUrl);
				if (!text.isEmpty()) {
					out.add(new Part(hardBreaks(text), false));
				}
			}
		}
		// Nesting outside a list has no Markdown form: children follow the block at the same level.
		renderBlocks(block.path("children"), baseUrl, out);
	}

	private String renderListItem(JsonNode block, int number, String baseUrl) {
		String type = typeOf(block);
		String marker;
		if ("numberedListItem".equals(type)) {
			marker = number + ". ";
		}
		else if ("checkListItem".equals(type)) {
			marker = block.path("props").path("checked").asBoolean(false) ? "- [x] " : "- [ ] ";
		}
		else {
			marker = "- ";
		}
		String text = renderInline(block.path("content"), baseUrl);
		StringBuilder item = new StringBuilder(marker).append(indentContinuation(text));
		List<Part> children = new ArrayList<>();
		renderBlocks(block.path("children"), baseUrl, children);
		for (Part child : children) {
			// A paragraph-like child needs a blank line, or Markdown reads it as continuation of the line above.
			item.append(child.interruptsParagraph() ? "\n" : "\n\n").append(prefixLines(child.markdown(), INDENT));
		}
		return item.toString();
	}

	private String renderCode(JsonNode block) {
		String language = block.path("props").path("language").asString("");
		StringBuilder code = new StringBuilder();
		JsonNode content = block.path("content");
		if (content.isArray()) {
			for (JsonNode item : content.values()) {
				code.append(item.path("text").asString(""));
			}
		}
		String fence = "`".repeat(Math.max(3, longestRun(code, '`') + 1));
		StringBuilder md = new StringBuilder(fence);
		if (!language.isBlank() && language.matches("[A-Za-z0-9+#._-]{1,32}") && !"text".equals(language)) {
			md.append(language);
		}
		md.append('\n').append(code);
		if (!code.isEmpty() && code.charAt(code.length() - 1) != '\n') {
			md.append('\n');
		}
		return md.append(fence).toString();
	}

	/** An uploaded image: standard Markdown image syntax with the file name as alt text, caption below. */
	private String renderImage(JsonNode props, String baseUrl) {
		String url = props.path("url").asString("");
		if (!AttachmentService.isInternalUrl(url)) {
			return renderAttachment("Afbeelding", props, baseUrl);
		}
		String name = props.path("name").asString("").strip();
		StringBuilder md = new StringBuilder();
		md.append("![").append(escapeInline(name)).append("](").append(linkDestination(baseUrl + url)).append(')');
		appendCaption(props, md);
		return md.toString();
	}

	/** An uploaded PDF or video cannot travel inside a text file; the export keeps a link to it and the caption. */
	private String renderAttachment(String kind, JsonNode props, String baseUrl) {
		String url = props.path("url").asString("");
		String name = props.path("name").asString("").strip();
		String label = kind + (name.isEmpty() ? "" : ": " + escapeInline(name));
		StringBuilder md = new StringBuilder();
		if (AttachmentService.isInternalUrl(url)) {
			md.append('[').append(label).append("](").append(linkDestination(baseUrl + url)).append(')');
		}
		else {
			md.append(label);
		}
		appendCaption(props, md);
		return md.toString();
	}

	private static void appendCaption(JsonNode props, StringBuilder md) {
		String caption = props.path("caption").asString("").strip();
		if (!caption.isEmpty()) {
			md.append("\\\n*").append(escapeInline(caption)).append('*');
		}
	}

	/** Inline content as Markdown; a newline inside the text stays a newline (callers decide how to break). */
	private String renderInline(JsonNode content, String baseUrl) {
		return escapeLineStarts(renderInlineRaw(content, baseUrl));
	}

	private String renderInlineRaw(JsonNode content, String baseUrl) {
		if (!content.isArray()) {
			return "";
		}
		StringBuilder md = new StringBuilder();
		for (JsonNode item : mergeAdjacentText(content)) {
			String type = item.path("type").asString("");
			if ("link".equals(type)) {
				String href = LinkPolicy.sanitize(item.path("href").asString(""));
				if (href.startsWith("/")) {
					href = baseUrl + href;
				}
				md.append('[').append(renderInlineRaw(item.path("content"), baseUrl)).append("](")
					.append(linkDestination(href)).append(')');
			}
			else if ("text".equals(type)) {
				md.append(renderStyledText(item));
			}
		}
		return md.toString();
	}

	/** Neighbouring text runs with the same styles become one run, so "**a****b**" never occurs. */
	private static List<JsonNode> mergeAdjacentText(JsonNode content) {
		List<JsonNode> merged = new ArrayList<>();
		for (JsonNode item : content.values()) {
			if (!merged.isEmpty() && "text".equals(item.path("type").asString(""))) {
				JsonNode previous = merged.get(merged.size() - 1);
				if ("text".equals(previous.path("type").asString(""))
						&& previous.path("styles").equals(item.path("styles"))) {
					var combined = previous.deepCopy();
					((ObjectNode) combined).put("text",
						previous.path("text").asString("") + item.path("text").asString(""));
					merged.set(merged.size() - 1, combined);
					continue;
				}
			}
			merged.add(item);
		}
		return merged;
	}

	private String renderStyledText(JsonNode item) {
		String text = item.path("text").asString("");
		if (text.isEmpty()) {
			return "";
		}
		JsonNode styles = item.path("styles");
		// Emphasis markers do not work next to whitespace: keep leading/trailing whitespace outside them.
		int start = 0;
		while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		int end = text.length();
		while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		String leading = text.substring(0, start);
		String core = text.substring(start, end);
		String trailing = text.substring(end);
		if (core.isEmpty()) {
			return text;
		}
		String inner;
		if (styles.path("code").asBoolean(false)) {
			inner = codeSpan(core);
		}
		else {
			inner = escapeInline(core);
		}
		if (styles.path("bold").asBoolean(false)) {
			inner = "**" + inner + "**";
		}
		if (styles.path("italic").asBoolean(false)) {
			inner = "*" + inner + "*";
		}
		if (styles.path("strike").asBoolean(false)) {
			inner = "~~" + inner + "~~";
		}
		if (styles.path("underline").asBoolean(false)) {
			inner = "<u>" + inner + "</u>";
		}
		return leading + inner + trailing;
	}

	/** A code span whose fence is longer than any backtick run inside; padded when the code starts or ends with a backtick. */
	private static String codeSpan(String code) {
		String fence = "`".repeat(longestRun(code, '`') + 1);
		boolean pad = code.startsWith("`") || code.endsWith("`");
		return fence + (pad ? " " : "") + code.replace("\n", " ") + (pad ? " " : "") + fence;
	}

	private static int longestRun(CharSequence text, char c) {
		int longest = 0;
		int current = 0;
		for (int i = 0; i < text.length(); i++) {
			current = text.charAt(i) == c ? current + 1 : 0;
			longest = Math.max(longest, current);
		}
		return longest;
	}

	static String escapeInline(String text) {
		return INLINE_SPECIAL.matcher(text).replaceAll("\\\\$1");
	}

	/** A line that starts with "#", "-", "1." and so on would become a heading or list: escape the marker. */
	private static String escapeLineStarts(String text) {
		return LINE_START_SPECIAL.matcher(text).replaceAll(m -> {
			String marker = m.group(2);
			String escaped = Character.isDigit(marker.charAt(0))
				? marker.substring(0, marker.length() - 1) + "\\" + marker.charAt(marker.length() - 1)
				: "\\" + marker;
			return Matcher.quoteReplacement(m.group(1) + escaped);
		});
	}

	/** Destinations with spaces or parentheses go inside angle brackets so the link stays intact. */
	private static String linkDestination(String href) {
		if (href.chars().anyMatch(Character::isWhitespace) || href.contains("(") || href.contains(")")
				|| href.contains("<") || href.contains(">")) {
			return "<" + href.replace("<", "%3C").replace(">", "%3E").replace(" ", "%20") + ">";
		}
		return href;
	}

	/** A line break inside one block is a hard break (trailing backslash), never a new paragraph. */
	private static String hardBreaks(String text) {
		return text.replace("\n", "\\\n");
	}

	/** Hard breaks whose continuation lines are indented under the list marker. */
	private static String indentContinuation(String text) {
		return hardBreaks(text).replace("\n", "\n" + INDENT);
	}

	private static String prefixLines(String text, String prefix) {
		return prefix + text.replace("\n", "\n" + prefix);
	}

	private static String yamlString(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + '"';
	}

	private static String typeOf(JsonNode block) {
		return block.path("type").asString("paragraph");
	}

	private static boolean isListItem(String type) {
		return "bulletListItem".equals(type) || "numberedListItem".equals(type) || "checkListItem".equals(type);
	}

}
