package nl.logicai.wiki.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.logicai.wiki.exceptions.InvalidContentException;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Markdown to BlockNote blocks: the inverse of {@link MarkdownExporter} for the subset
 * {@link DocumentValidator} accepts. It exists for the MCP write tools (docs/mcp.md), where an AI agent
 * hands over page content as Markdown; the web editor never uses it.
 *
 * <p>The mapping mirrors the exporter, so {@code export(import(export(doc)))} equals {@code export(doc)}:
 * headings, paragraphs (a hard break is a line break inside the block, a soft break a space), bullet,
 * numbered and check lists with nested blocks, fenced code, quotes, rules, bold, italic, strike, code,
 * {@code <u>}, and links. An image, {@code [Bijlage: …]} or {@code [Video: …]} link to an uploaded file that
 * stands alone in a paragraph becomes the image, file or video block again, with its caption; links that
 * start with the wiki's base URL become application-relative, as they are stored.
 *
 * <p>Markdown the wiki has no block for never fails the import but degrades to text, with a warning: a
 * table is kept as preformatted text, raw HTML as literal text, an external image as a link, a link the
 * {@link LinkPolicy} refuses as its text. What Markdown cannot say at all (colours, image width, nesting
 * under a non-list block) is not here to import; {@link #merge} keeps it for every block the Markdown left
 * unchanged, and {@link MarkdownLossDetector} names it for the blocks it did change.
 */
@Component
public class MarkdownImporter {

	/** The validator's limit (depth 0 is the top level); deeper blocks are flattened instead of refused. */
	private static final int MAX_DEPTH = 10;

	/** Larger input cannot become a valid page anyway (wiki.limits.document-max-bytes is 2 MB). */
	private static final int MAX_MARKDOWN_LENGTH = 2_000_000;

	private static final Pattern FRONT_MATTER = Pattern.compile("\\A---[ \\t]*\\r?\\n(.*?\\r?\\n)?---[ \\t]*(\\r?\\n|\\z)", Pattern.DOTALL);
	private static final Pattern YAML_LINE = Pattern.compile("^([A-Za-z_][\\w-]*:.*|\\s+-\\s.*|\\s*)$");
	private static final Pattern ATTACHMENT_LABEL = Pattern.compile("^(Bijlage|Video)(?:: (.*))?$", Pattern.DOTALL);
	private static final Pattern BARE_TASK = Pattern.compile("^\\[([ xX])]$");

	private final ObjectMapper mapper;
	private final MarkdownExporter exporter;
	private final Parser parser;

	public MarkdownImporter(ObjectMapper mapper, MarkdownExporter exporter) {
		this.mapper = mapper;
		this.exporter = exporter;
		this.parser = Parser.builder()
			.extensions(List.of(StrikethroughExtension.create(), TaskListItemsExtension.create(), TablesExtension.create()))
			.includeSourceSpans(IncludeSourceSpans.BLOCKS)
			.build();
	}

	/** The blocks plus what could not be imported as written (English: the reader is an AI agent). */
	public record ImportResult(ArrayNode blocks, List<String> warnings) {
	}

	/**
	 * The outcome of applying Markdown to an existing page.
	 *
	 * @param blocks   the new document; blocks the Markdown did not change are the stored ones, untouched
	 * @param warnings what could not be imported as written
	 * @param losses   formatting of changed or removed blocks that Markdown cannot express and is now gone
	 */
	public record MergeResult(ArrayNode blocks, List<String> warnings, List<String> losses) {
	}

	public ImportResult importDocument(String markdown, String baseUrl) {
		return importDocument(markdown, baseUrl, null);
	}

	/**
	 * @param baseUrl the wiki root without trailing slash; links below it are stored application-relative
	 * @param title   the page title; a leading level-1 heading that repeats it is dropped, the page shows it already
	 */
	public ImportResult importDocument(String markdown, String baseUrl, String title) {
		String source = markdown == null ? "" : markdown;
		if (source.length() > MAX_MARKDOWN_LENGTH) {
			throw new InvalidContentException("De Markdown-tekst is te groot (maximaal " + MAX_MARKDOWN_LENGTH + " tekens).");
		}
		source = stripFrontMatter(source);
		Run run = new Run(baseUrl == null ? "" : baseUrl, source);
		ArrayNode blocks = mapper.createArrayNode();
		for (Node node = parser.parse(source).getFirstChild(); node != null; node = node.getNext()) {
			run.block(node, blocks, 0);
		}
		if (title != null && !blocks.isEmpty()) {
			JsonNode first = blocks.get(0);
			if ("heading".equals(first.path("type").asString("")) && first.path("props").path("level").asInt(0) == 1
					&& title.strip().equals(plainText(first.path("content")).strip())) {
				blocks.remove(0);
			}
		}
		return new ImportResult(blocks, List.copyOf(run.warnings));
	}

	/**
	 * Applies Markdown to an existing document without rewriting what did not change. Both sides are compared
	 * in the form they have after the (lossy) export: a stored block whose Markdown is still present, in the
	 * same order, is kept exactly as stored, so its colours, image width, nesting and block id survive. Only
	 * blocks the Markdown really changed, added or removed are replaced by imported ones.
	 */
	public MergeResult merge(JsonNode existing, String markdown, String baseUrl, String title) {
		ImportResult imported = importDocument(markdown, baseUrl, title);
		List<JsonNode> oldBlocks = existing == null || !existing.isArray() ? List.of() : new ArrayList<>(existing.values());
		List<JsonNode> newBlocks = new ArrayList<>(imported.blocks().values());

		// Each stored block as the Markdown lines it turns into; a nested paragraph turns into several.
		List<List<String>> oldKeys = new ArrayList<>();
		List<String> oldFlat = new ArrayList<>();
		List<Integer> owner = new ArrayList<>();
		for (int i = 0; i < oldBlocks.size(); i++) {
			List<String> keys = new ArrayList<>();
			String md = exporter.exportDocument(single(oldBlocks.get(i)), baseUrl);
			for (JsonNode roundTripped : importDocument(md, baseUrl).blocks().values()) {
				keys.add(exporter.exportDocument(single(roundTripped), baseUrl));
			}
			oldKeys.add(keys);
			for (String key : keys) {
				oldFlat.add(key);
				owner.add(i);
			}
		}
		List<String> newFlat = new ArrayList<>();
		for (JsonNode block : newBlocks) {
			newFlat.add(exporter.exportDocument(single(block), baseUrl));
		}

		int[] matchOfOld = longestCommonSubsequence(oldFlat, newFlat);

		// A stored block is kept when all its lines are matched, to consecutive new blocks.
		boolean[] kept = new boolean[oldBlocks.size()];
		int[] firstNew = new int[oldBlocks.size()];
		int flat = 0;
		for (int i = 0; i < oldBlocks.size(); i++) {
			int count = oldKeys.get(i).size();
			boolean all = count > 0;
			for (int k = 0; k < count && all; k++) {
				all = matchOfOld[flat + k] >= 0 && (k == 0 || matchOfOld[flat + k] == matchOfOld[flat + k - 1] + 1);
			}
			kept[i] = all;
			firstNew[i] = all ? matchOfOld[flat] : -1;
			flat += count;
		}

		// Walk the new blocks; where a kept stored block starts, emit the stored block instead. A stored block
		// without any Markdown (an empty paragraph) stays next to the kept block it followed.
		JsonNode[] storedAt = new JsonNode[newBlocks.size()];
		int[] span = new int[newBlocks.size()];
		List<List<JsonNode>> emptiesAfter = new ArrayList<>();
		for (int j = 0; j <= newBlocks.size(); j++) {
			emptiesAfter.add(new ArrayList<>());
		}
		int anchor = 0;
		Boolean previousKept = null;
		for (int i = 0; i < oldBlocks.size(); i++) {
			if (oldKeys.get(i).isEmpty()) {
				// Leading empty blocks stay only in front of a block that stays; a page that was just an empty
				// paragraph does not keep it above its new content.
				boolean stays = previousKept != null ? previousKept : nextWithMarkdownIsKept(i, oldKeys, kept);
				if (stays) {
					emptiesAfter.get(anchor).add(oldBlocks.get(i));
					kept[i] = true;
				}
			}
			else if (kept[i]) {
				storedAt[firstNew[i]] = oldBlocks.get(i);
				span[firstNew[i]] = oldKeys.get(i).size();
				anchor = firstNew[i] + oldKeys.get(i).size();
				previousKept = true;
			}
			else {
				previousKept = false;
			}
		}

		ArrayNode merged = mapper.createArrayNode();
		emptiesAfter.get(0).forEach(merged::add);
		for (int j = 0; j < newBlocks.size();) {
			int next;
			if (storedAt[j] != null) {
				merged.add(storedAt[j]);
				next = j + span[j];
			}
			else {
				merged.add(newBlocks.get(j));
				next = j + 1;
			}
			for (int between = j + 1; between <= next; between++) {
				emptiesAfter.get(between).forEach(merged::add);
			}
			j = next;
		}

		List<String> losses = new ArrayList<>();
		for (int i = 0; i < oldBlocks.size(); i++) {
			if (!kept[i]) {
				losses.addAll(MarkdownLossDetector.describe(oldBlocks.get(i), i + 1));
			}
		}
		return new MergeResult(merged, imported.warnings(), losses);
	}

	private static boolean nextWithMarkdownIsKept(int from, List<List<String>> keys, boolean[] kept) {
		for (int i = from + 1; i < keys.size(); i++) {
			if (!keys.get(i).isEmpty()) {
				return kept[i];
			}
		}
		return false;
	}

	private ArrayNode single(JsonNode block) {
		ArrayNode array = mapper.createArrayNode();
		array.add(block);
		return array;
	}

	/** For every element of {@code a} the index of its partner in {@code b}, or -1. */
	private static int[] longestCommonSubsequence(List<String> a, List<String> b) {
		int[] match = new int[a.size()];
		java.util.Arrays.fill(match, -1);
		int start = 0;
		while (start < a.size() && start < b.size() && a.get(start).equals(b.get(start))) {
			match[start] = start;
			start++;
		}
		int endA = a.size();
		int endB = b.size();
		while (endA > start && endB > start && a.get(endA - 1).equals(b.get(endB - 1))) {
			endA--;
			endB--;
			match[endA] = endB;
		}
		int n = endA - start;
		int m = endB - start;
		if (n == 0 || m == 0 || (long) n * m > 4_000_000L) {
			// Nothing left to align, or too much for a table: the middle then counts as rewritten.
			return match;
		}
		int[][] length = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				length[i][j] = a.get(start + i).equals(b.get(start + j))
					? length[i + 1][j + 1] + 1
					: Math.max(length[i + 1][j], length[i][j + 1]);
			}
		}
		for (int i = 0, j = 0; i < n && j < m;) {
			if (a.get(start + i).equals(b.get(start + j))) {
				match[start + i] = start + j;
				i++;
				j++;
			}
			else if (length[i + 1][j] >= length[i][j + 1]) {
				i++;
			}
			else {
				j++;
			}
		}
		return match;
	}

	/** The YAML block the wiki's own .md export starts with; the title and tags in it are not content. */
	private static String stripFrontMatter(String markdown) {
		Matcher matcher = FRONT_MATTER.matcher(markdown);
		if (!matcher.find()) {
			return markdown;
		}
		String body = matcher.group(1) == null ? "" : matcher.group(1);
		for (String line : body.split("\\r?\\n")) {
			if (!YAML_LINE.matcher(line).matches()) {
				return markdown;
			}
		}
		return markdown.substring(matcher.end());
	}

	static String plainText(JsonNode content) {
		StringBuilder text = new StringBuilder();
		if (content.isArray()) {
			for (JsonNode item : content.values()) {
				if ("link".equals(item.path("type").asString(""))) {
					text.append(plainText(item.path("content")));
				}
				else {
					text.append(item.path("text").asString(""));
				}
			}
		}
		return text.toString();
	}

	private record Styles(boolean bold, boolean italic, boolean strike, boolean code) {

		static final Styles NONE = new Styles(false, false, false, false);

	}

	/** One import: the warnings and the underline state of the inline content being read. */
	private final class Run {

		private final String baseUrl;
		private final String[] lines;
		private final Set<String> warnings = new LinkedHashSet<>();
		private int underline;

		Run(String baseUrl, String source) {
			this.baseUrl = baseUrl;
			this.lines = source.split("\\r?\\n|\\r", -1);
		}

		void block(Node node, ArrayNode out, int depth) {
			switch (node) {
				case Heading heading -> {
					ObjectNode block = newBlock("heading");
					((ObjectNode) block.get("props")).put("level", Math.clamp(heading.getLevel(), 1, 6));
					ArrayNode content = inline(heading);
					for (JsonNode item : content.values()) {
						if (item.has("text")) {
							((ObjectNode) item).put("text", item.path("text").asString("").replace('\n', ' '));
						}
					}
					block.set("content", content);
					out.add(block);
				}
				case Paragraph paragraph -> paragraph(paragraph, out);
				case BulletList list -> listItems(list, "bulletListItem", out, depth);
				case OrderedList list -> listItems(list, "numberedListItem", out, depth);
				case FencedCodeBlock code -> out.add(codeBlock(code.getLiteral(), code.getInfo()));
				case IndentedCodeBlock code -> out.add(codeBlock(code.getLiteral(), null));
				case BlockQuote quote -> quote(quote, out, depth);
				case ThematicBreak rule -> out.add(newBlock("divider"));
				case HtmlBlock html -> {
					warnings.add("Raw HTML is not supported and was kept as literal text.");
					out.add(textBlock("paragraph", html.getLiteral().strip()));
				}
				case TableBlock table -> {
					warnings.add("Tables are not supported by the wiki; the table was kept as preformatted text. "
						+ "Use a list or headings instead.");
					out.add(codeBlock(sourceOf(table), null));
				}
				case LinkReferenceDefinition definition -> {
					// Already resolved into the links that use it.
				}
				default -> {
					for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
						block(child, out, depth);
					}
				}
			}
		}

		private void paragraph(Paragraph paragraph, ArrayNode out) {
			ObjectNode fileBlock = fileBlock(paragraph);
			if (fileBlock != null) {
				out.add(fileBlock);
				return;
			}
			ArrayNode content = inline(paragraph);
			if (!content.isEmpty()) {
				ObjectNode block = newBlock("paragraph");
				block.set("content", content);
				out.add(block);
			}
		}

		/**
		 * An uploaded image, PDF or video as the exporter writes it: alone in its paragraph, optionally followed
		 * by a hard break and the caption in italics.
		 */
		private ObjectNode fileBlock(Paragraph paragraph) {
			Node first = paragraph.getFirstChild();
			if (first == null) {
				return null;
			}
			String caption = null;
			Node second = first.getNext();
			if (second != null) {
				Node third = second.getNext();
				if (!(second instanceof HardLineBreak) || !(third instanceof Emphasis) || third.getNext() != null) {
					return null;
				}
				caption = literalText(third);
			}
			String type;
			String name;
			String url;
			if (first instanceof Image image) {
				type = "image";
				name = literalText(image);
				url = relative(image.getDestination());
			}
			else if (first instanceof Link link) {
				Matcher label = ATTACHMENT_LABEL.matcher(literalText(link));
				if (!label.matches()) {
					return null;
				}
				type = "Video".equals(label.group(1)) ? "video" : "file";
				name = label.group(2) == null ? "" : label.group(2);
				url = relative(link.getDestination());
			}
			else {
				return null;
			}
			if (!AttachmentService.isInternalUrl(url)) {
				return null;
			}
			ObjectNode block = newBlock(type);
			ObjectNode props = (ObjectNode) block.get("props");
			props.put("url", url);
			props.put("name", name);
			if (caption != null && !caption.isBlank()) {
				props.put("caption", caption);
			}
			return block;
		}

		private void listItems(Node list, String type, ArrayNode out, int depth) {
			for (Node node = list.getFirstChild(); node != null; node = node.getNext()) {
				if (!(node instanceof ListItem)) {
					continue;
				}
				Node child = node.getFirstChild();
				Boolean checked = null;
				if (child instanceof TaskListItemMarker marker) {
					checked = marker.isChecked();
					child = child.getNext();
				}
				ArrayNode content = mapper.createArrayNode();
				if (child instanceof Paragraph paragraph) {
					content = inline(paragraph);
					// "- [ ]" without text: the task list extension only recognises a marker that is followed by text.
					if (checked == null && content.size() == 1) {
						Matcher bare = BARE_TASK.matcher(content.get(0).path("text").asString(""));
						if (bare.matches() && content.get(0).path("styles").isEmpty()) {
							checked = !" ".equals(bare.group(1));
							content = mapper.createArrayNode();
						}
					}
					child = child.getNext();
				}
				ObjectNode item = newBlock(checked == null ? type : "checkListItem");
				if (checked != null) {
					((ObjectNode) item.get("props")).put("checked", checked);
				}
				item.set("content", content);
				out.add(item);
				// Deeper than the validator allows: keep the text, give up the nesting.
				ArrayNode below = depth + 1 > MAX_DEPTH ? out : (ArrayNode) item.get("children");
				for (; child != null; child = child.getNext()) {
					block(child, below, depth + 1 > MAX_DEPTH ? depth : depth + 1);
				}
			}
		}

		/** A quote block holds text only: its paragraphs become lines, anything else follows the quote. */
		private void quote(BlockQuote quote, ArrayNode out, int depth) {
			ArrayNode content = mapper.createArrayNode();
			for (Node child = quote.getFirstChild(); child != null; child = child.getNext()) {
				if (child instanceof Paragraph paragraph) {
					ArrayNode line = inline(paragraph);
					if (!content.isEmpty() && !line.isEmpty()) {
						addText(content, "\n", Styles.NONE, false);
					}
					for (JsonNode item : line.values()) {
						appendInline(content, item);
					}
				}
				else {
					flushQuote(content, out);
					content = mapper.createArrayNode();
					block(child, out, depth);
				}
			}
			flushQuote(content, out);
		}

		private void flushQuote(ArrayNode content, ArrayNode out) {
			if (!content.isEmpty()) {
				ObjectNode block = newBlock("quote");
				block.set("content", content);
				out.add(block);
			}
		}

		private ObjectNode codeBlock(String literal, String info) {
			String code = literal == null ? "" : literal;
			if (code.endsWith("\n")) {
				code = code.substring(0, code.length() - 1);
			}
			String language = info == null ? "" : info.strip().split("\\s+")[0];
			ObjectNode block = textBlock("codeBlock", code);
			((ObjectNode) block.get("props")).put("language", language.isEmpty() ? "text" : language);
			return block;
		}

		private ObjectNode textBlock(String type, String text) {
			ObjectNode block = newBlock(type);
			ArrayNode content = mapper.createArrayNode();
			if (!text.isEmpty()) {
				addText(content, text, Styles.NONE, false);
			}
			block.set("content", content);
			return block;
		}

		private ObjectNode newBlock(String type) {
			ObjectNode block = mapper.createObjectNode();
			block.put("id", UUID.randomUUID().toString());
			block.put("type", type);
			block.set("props", mapper.createObjectNode());
			if (!"divider".equals(type) && !"image".equals(type) && !"file".equals(type) && !"video".equals(type)) {
				block.set("content", mapper.createArrayNode());
			}
			block.set("children", mapper.createArrayNode());
			return block;
		}

		/** The block's source lines as written, for Markdown that is kept as preformatted text. */
		private String sourceOf(Node node) {
			List<String> text = new ArrayList<>();
			for (SourceSpan span : node.getSourceSpans()) {
				if (span.getLineIndex() < lines.length) {
					String line = lines[span.getLineIndex()];
					int from = Math.min(span.getColumnIndex(), line.length());
					text.add(line.substring(from, Math.min(from + span.getLength(), line.length())));
				}
			}
			return String.join("\n", text);
		}

		private ArrayNode inline(Node parent) {
			underline = 0;
			ArrayNode content = mapper.createArrayNode();
			inline(parent, content, Styles.NONE, false);
			return content;
		}

		private void inline(Node parent, ArrayNode out, Styles styles, boolean plainOnly) {
			for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
				switch (node) {
					case Text text -> addText(out, text.getLiteral(), styles, underline > 0);
					case SoftLineBreak soft -> addText(out, " ", styles, underline > 0);
					case HardLineBreak hard -> addText(out, "\n", styles, underline > 0);
					case Code code -> addText(out, code.getLiteral(),
						new Styles(styles.bold(), styles.italic(), styles.strike(), true), underline > 0);
					case Emphasis emphasis -> inline(emphasis, out,
						new Styles(styles.bold(), true, styles.strike(), styles.code()), plainOnly);
					case StrongEmphasis strong -> inline(strong, out,
						new Styles(true, styles.italic(), styles.strike(), styles.code()), plainOnly);
					case Strikethrough strike -> inline(strike, out,
						new Styles(styles.bold(), styles.italic(), true, styles.code()), plainOnly);
					case Link link -> link(link, link.getDestination(), out, styles, plainOnly);
					case Image image -> {
						warnings.add("An image can only be shown when it was uploaded to the wiki and stands alone in "
							+ "its paragraph; other images were kept as links.");
						link(image, image.getDestination(), out, styles, plainOnly);
					}
					case HtmlInline html -> html(html.getLiteral(), out, styles);
					default -> inline(node, out, styles, plainOnly);
				}
			}
		}

		private void link(Node node, String destination, ArrayNode out, Styles styles, boolean plainOnly) {
			String href = relative(destination);
			if (plainOnly || !LinkPolicy.isAllowed(href)) {
				if (!plainOnly) {
					warnings.add("A link with a destination the wiki does not allow was kept as plain text: " + abbreviate(href));
				}
				inline(node, out, styles, true);
				return;
			}
			ArrayNode content = mapper.createArrayNode();
			inline(node, content, styles, true);
			if (content.isEmpty()) {
				addText(content, href, styles, underline > 0);
			}
			ObjectNode item = mapper.createObjectNode();
			item.put("type", "link");
			item.put("href", href);
			item.set("content", content);
			out.add(item);
		}

		private void html(String literal, ArrayNode out, Styles styles) {
			String tag = literal.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
			switch (tag) {
				case "<u>" -> underline++;
				case "</u>" -> underline = Math.max(0, underline - 1);
				case "<br>", "<br/>" -> addText(out, "\n", styles, underline > 0);
				default -> {
					warnings.add("Raw HTML is not supported and was kept as literal text.");
					addText(out, literal, styles, underline > 0);
				}
			}
		}

		/** Adds text, merging it into the previous run when the styles are the same. */
		private void addText(ArrayNode out, String text, Styles styles, boolean underlined) {
			if (text == null || text.isEmpty()) {
				return;
			}
			ObjectNode style = mapper.createObjectNode();
			if (styles.bold()) {
				style.put("bold", true);
			}
			if (styles.italic()) {
				style.put("italic", true);
			}
			if (underlined) {
				style.put("underline", true);
			}
			if (styles.strike()) {
				style.put("strike", true);
			}
			if (styles.code()) {
				style.put("code", true);
			}
			ObjectNode item = mapper.createObjectNode();
			item.put("type", "text");
			item.put("text", text);
			item.set("styles", style);
			appendInline(out, item);
		}

		private void appendInline(ArrayNode out, JsonNode item) {
			if (!out.isEmpty() && "text".equals(item.path("type").asString(""))) {
				JsonNode previous = out.get(out.size() - 1);
				if ("text".equals(previous.path("type").asString("")) && previous.path("styles").equals(item.path("styles"))) {
					((ObjectNode) previous).put("text", previous.path("text").asString("") + item.path("text").asString(""));
					return;
				}
			}
			out.add(item);
		}

		/** The text of a node without any formatting, as used for names and captions. */
		private String literalText(Node parent) {
			StringBuilder text = new StringBuilder();
			for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
				switch (node) {
					case Text t -> text.append(t.getLiteral());
					case Code c -> text.append(c.getLiteral());
					case SoftLineBreak s -> text.append(' ');
					case HardLineBreak h -> text.append(' ');
					default -> text.append(literalText(node));
				}
			}
			return text.toString();
		}

		private String relative(String destination) {
			String href = destination == null ? "" : destination;
			if (!baseUrl.isEmpty() && href.startsWith(baseUrl + "/")) {
				return href.substring(baseUrl.length());
			}
			return href;
		}

		private static String abbreviate(String text) {
			return text.length() > 80 ? text.substring(0, 80) + "…" : text;
		}

	}

}
