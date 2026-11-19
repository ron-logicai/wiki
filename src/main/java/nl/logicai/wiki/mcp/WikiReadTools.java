package nl.logicai.wiki.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.PageService.PageNode;
import nl.logicai.wiki.services.SearchService;
import nl.logicai.wiki.services.TagService;
import nl.logicai.wiki.services.TemplateService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static nl.logicai.wiki.mcp.McpResults.map;
import static nl.logicai.wiki.mcp.McpResults.optionalUuid;
import static nl.logicai.wiki.mcp.McpResults.uuid;

/**
 * The read-only MCP tools. Every active wiki user may read every active page (spec section 1), so these
 * tools need no role beyond being logged in; pages in the trash are never returned.
 *
 * <p>{@code search} and {@code fetch} have the exact shape ChatGPT expects for deep research and company
 * knowledge; {@code search_pages} and {@code get_page} are the richer variants for everything else.
 */
@Component
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class WikiReadTools {

	private static final int MAX_TREE_DEPTH = 5;

	private final PageService pages;
	private final SearchService search;
	private final TagService tags;
	private final TemplateService templates;
	private final McpPageMapper mapper;
	private final McpResults results;

	public WikiReadTools(PageService pages, SearchService search, TagService tags, TemplateService templates,
			McpPageMapper mapper, McpResults results) {
		this.pages = pages;
		this.search = search;
		this.tags = tags;
		this.templates = templates;
		this.mapper = mapper;
		this.results = results;
	}

	@McpTool(name = "search", title = "Search the wiki",
		description = "Full-text search over the titles and content of all wiki pages. Returns the best matching "
			+ "pages with id, title and url. Every word of the query must occur in the page (whole words, no "
			+ "stemming), so prefer a few distinctive keywords over a sentence. Use fetch to read a result.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult search(@McpToolParam(description = "Keywords to search for") String query) {
		return results.run(() -> {
			var found = search.search(query, null, 1);
			return map("results", found.hits().stream().map(hit -> mapper.reference(hit.page())).toList());
		});
	}

	@McpTool(name = "fetch", title = "Read a wiki page",
		description = "Returns one wiki page by id: title, url, the full content as Markdown in 'text', and metadata.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult fetch(@McpToolParam(description = "Page id (UUID) from search") String id) {
		return results.run(() -> {
			Page page = pages.getActive(uuid(id, "id"));
			return map(
				"id", page.getId().toString(),
				"title", page.getTitle(),
				"text", mapper.markdown(page),
				"url", mapper.url(page),
				"metadata", map(
					"path", mapper.path(page),
					"version", String.valueOf(page.getLockVersion()),
					"status", page.getStatus() == null ? null : page.getStatus().getLabel(),
					"tags", String.join(", ", mapper.tagNames(page)),
					"updatedAt", page.getUpdatedAt() == null ? null : page.getUpdatedAt().toString()));
		});
	}

	@McpTool(name = "search_pages", title = "Search pages (detailed)",
		description = "Full-text search with more detail than 'search': each result has its place in the page tree "
			+ "(path), a text fragment around the match, and tags. Optionally restricted to one tag. Returns 20 "
			+ "results per page; 'totalPages' tells whether there are more. Every word must occur in the page.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult searchPages(
			@McpToolParam(description = "Keywords to search for") String query,
			@McpToolParam(required = false, description = "Only pages with this tag (exact tag name)") String tag,
			@McpToolParam(required = false, description = "Result page, starting at 1") Integer page) {
		return results.run(() -> {
			var found = search.search(query, tag, page == null ? 1 : page);
			List<Map<String, Object>> hits = new ArrayList<>();
			for (SearchService.Hit hit : found.hits()) {
				Map<String, Object> item = mapper.reference(hit.page());
				item.put("path", hit.path());
				item.put("snippet", hit.snippet().stream().map(SearchService.Segment::text).collect(Collectors.joining()));
				item.put("tags", hit.tags().stream().map(Tag::getName).toList());
				hits.add(item);
			}
			return map("query", found.query(), "tag", found.tag(), "page", found.page(),
				"totalPages", found.totalPages(), "total", found.total(), "results", hits);
		});
	}

	@McpTool(name = "get_page", title = "Get a page with metadata",
		description = "Returns one page: the content as Markdown plus title, url, path, status, owner, tags, "
			+ "subpages (children), related pages and 'version'. Call this before update_page or append_to_page "
			+ "and pass 'version' as baseVersion. 'warnings' lists formatting that Markdown cannot express.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult getPage(@McpToolParam(description = "Page id (UUID)") String id) {
		return results.run(() -> mapper.full(pages.getActive(uuid(id, "id"))));
	}

	@McpTool(name = "list_pages", title = "Browse the page tree",
		description = "Lists pages as a tree: the top-level pages, or the subpages of 'parentId'. Each page has "
			+ "id, title, url and childCount (all pages below it); nested pages are included down to 'depth'.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult listPages(
			@McpToolParam(required = false, description = "Page id whose subpages to list; omit for the top level") String parentId,
			@McpToolParam(required = false, description = "Levels to include, 1 to 5; default 2") Integer depth) {
		return results.run(() -> {
			UUID parent = optionalUuid(parentId, "parentId");
			int levels = Math.clamp(depth == null ? 2 : depth, 1, MAX_TREE_DEPTH);
			List<PageNode> nodes = pages.tree();
			if (parent != null) {
				PageNode start = find(nodes, parent);
				if (start == null) {
					throw new PageNotFoundException(parent);
				}
				nodes = start.children();
			}
			return map("pages", branches(nodes, levels));
		});
	}

	@McpTool(name = "list_recent_changes", title = "Recently changed pages",
		description = "The most recently changed pages, newest first, with who changed them and when.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult listRecentChanges() {
		return results.run(() -> map("pages", pages.recentlyChanged().stream().map(page -> {
			Map<String, Object> item = mapper.reference(page);
			item.put("updatedAt", page.getUpdatedAt().toString());
			item.put("updatedBy", pages.displayNameOf(page.getUpdatedBy()));
			return item;
		}).toList()));
	}

	@McpTool(name = "list_tags", title = "List tags",
		description = "All tags with the number of pages that carry each. Use a tag name with search_pages.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult listTags() {
		return results.run(() -> map("tags", tags.overview().stream()
			.map(count -> map("name", count.tag().getName(), "pages", count.paginas()))
			.toList()));
	}

	@McpTool(name = "list_templates", title = "List page templates",
		description = "The page templates (fixed section layouts such as a project or customer page). Pass a "
			+ "template id to create_page to start a new page from it.",
		annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public CallToolResult listTemplates() {
		return results.run(() -> map("templates", templates.all().stream()
			.map(template -> map("id", template.getId().toString(), "title", template.getTitle(),
				"description", template.getDescription()))
			.toList()));
	}

	private List<Map<String, Object>> branches(List<PageNode> nodes, int levels) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (PageNode node : nodes) {
			Map<String, Object> item = mapper.reference(node.page());
			item.put("childCount", node.aantalNakomelingen());
			if (levels > 1 && !node.children().isEmpty()) {
				item.put("children", branches(node.children(), levels - 1));
			}
			out.add(item);
		}
		return out;
	}

	private static PageNode find(List<PageNode> nodes, UUID id) {
		for (PageNode node : nodes) {
			if (node.page().getId().equals(id)) {
				return node;
			}
			PageNode below = find(node.children(), id);
			if (below != null) {
				return below;
			}
		}
		return null;
	}

}
