package nl.logicai.wiki.mcp;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import nl.logicai.wiki.services.MarkdownPageWriter.Written;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static nl.logicai.wiki.mcp.McpResults.map;
import static nl.logicai.wiki.mcp.McpResults.optionalUuid;
import static nl.logicai.wiki.mcp.McpResults.uuid;

/**
 * The MCP tools that change the wiki; all need the Editor role ({@link McpPageWriter}). Deliberately not
 * offered: moving, deleting and restoring pages, page properties and uploads; those stay with people.
 */
@Component
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class WikiWriteTools {

	private static final String MARKDOWN = "Page content as Markdown: headings, paragraphs, bullet/numbered/check "
		+ "lists, fenced code, quotes, rules, bold, italic, strikethrough, inline code and links. Link to another "
		+ "wiki page with its url. Tables and raw HTML are not supported and are kept as plain text. Do not repeat "
		+ "the page title as a heading.";

	private final McpPageWriter writer;
	private final McpPageMapper mapper;
	private final McpCaller caller;
	private final McpResults results;

	public WikiWriteTools(McpPageWriter writer, McpPageMapper mapper, McpCaller caller, McpResults results) {
		this.writer = writer;
		this.mapper = mapper;
		this.caller = caller;
		this.results = results;
	}

	@McpTool(name = "create_page", title = "Create a page",
		description = "Creates a new wiki page (needs the Editor role) and returns its id, url and version. The page "
			+ "starts with the given Markdown, or with a copy of a template, or empty. New pages get status "
			+ "'Concept' and the connected user as owner. Search first to avoid creating a duplicate.",
		annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public CallToolResult createPage(
			@McpToolParam(description = "Page title, at most 200 characters") String title,
			@McpToolParam(required = false, description = "Id of the page to create it under; omit for a top-level page") String parentId,
			@McpToolParam(required = false, description = MARKDOWN) String markdown,
			@McpToolParam(required = false, description = "Template id from list_templates; not together with markdown") String templateId,
			@McpToolParam(required = false, description = "Tag names to attach; unknown tags are created") List<String> tags) {
		return results.run(() -> written(writer.create(title, optionalUuid(parentId, "parentId"), markdown,
			optionalUuid(templateId, "templateId"), tags, caller.actor(), caller.client())));
	}

	@McpTool(name = "update_page", title = "Replace the content of a page",
		description = "Replaces the whole content (and optionally the title) of a page with the given Markdown "
			+ "(needs the Editor role). First call get_page, edit the Markdown it returned, and pass its 'version' "
			+ "as baseVersion; a CONFLICT error means someone else changed the page and you must re-read it. Parts "
			+ "you leave exactly as returned keep their formatting. If a part you changed has formatting Markdown "
			+ "cannot express (see 'warnings' of get_page) the call fails with FORMATTING_LOSS and changes nothing. "
			+ "The previous content stays available as a revision. To only add content, use append_to_page.",
		annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
	public CallToolResult updatePage(
			@McpToolParam(description = "Page id (UUID)") String id,
			@McpToolParam(description = "The 'version' get_page returned for this page") Long baseVersion,
			@McpToolParam(description = "The complete new content. " + MARKDOWN) String markdown,
			@McpToolParam(required = false, description = "New title; omit to keep the current title") String title,
			@McpToolParam(required = false, description = "true to accept losing formatting Markdown cannot express; default false") Boolean allowFormattingLoss) {
		return results.run(() -> written(writer.update(uuid(id, "id"), version(baseVersion), title, markdown,
			Boolean.TRUE.equals(allowFormattingLoss), caller.actor(), caller.client())));
	}

	@McpTool(name = "append_to_page", title = "Add content to the end of a page",
		description = "Adds the given Markdown below the existing content of a page, which stays untouched (needs "
			+ "the Editor role). Pass the 'version' from get_page as baseVersion. Prefer this over update_page "
			+ "whenever you only add something.",
		annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public CallToolResult appendToPage(
			@McpToolParam(description = "Page id (UUID)") String id,
			@McpToolParam(description = "The 'version' get_page returned for this page") Long baseVersion,
			@McpToolParam(description = "The content to add. " + MARKDOWN) String markdown) {
		return results.run(() -> written(writer.append(uuid(id, "id"), version(baseVersion), markdown,
			caller.actor(), caller.client())));
	}

	@McpTool(name = "add_tag", title = "Add a tag to a page",
		description = "Attaches a tag to a page (needs the Editor role); the tag is created when it does not exist "
			+ "yet. Check list_tags first and reuse an existing tag where one fits. Returns the page's tags.",
		annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult addTag(
			@McpToolParam(description = "Page id (UUID)") String id,
			@McpToolParam(description = "Tag name, at most 50 characters") String tag) {
		return results.run(() -> map("id", id, "tags", writer.addTag(uuid(id, "id"), tag, caller.actor())));
	}

	@McpTool(name = "remove_tag", title = "Remove a tag from a page",
		description = "Detaches a tag from a page (needs the Editor role). The tag itself and other pages are not "
			+ "affected. Returns the page's remaining tags.",
		annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public CallToolResult removeTag(
			@McpToolParam(description = "Page id (UUID)") String id,
			@McpToolParam(description = "Tag name") String tag) {
		return results.run(() -> map("id", id, "tags", writer.removeTag(uuid(id, "id"), tag)));
	}

	private Map<String, Object> written(Written written) {
		Map<String, Object> result = mapper.metadata(written.page());
		if (!written.warnings().isEmpty()) {
			result.put("warnings", written.warnings());
		}
		return result;
	}

	private static long version(Long baseVersion) {
		if (baseVersion == null) {
			throw new IllegalArgumentException("'baseVersion' is required: pass the 'version' get_page returned.");
		}
		return baseVersion;
	}

}
