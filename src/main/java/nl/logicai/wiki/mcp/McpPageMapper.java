package nl.logicai.wiki.mcp;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.services.MarkdownExporter;
import nl.logicai.wiki.services.MarkdownLossDetector;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.RelationService;
import nl.logicai.wiki.services.TagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import static nl.logicai.wiki.mcp.McpResults.map;

/**
 * What a page looks like to an MCP client. Links are built from {@code wiki.base-url}, never from request
 * headers (spec N-02). {@code version} is the lock version, the value a client passes back as
 * {@code baseVersion} when it changes the page; {@code revision} is the number shown in the page history.
 */
@Component
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpPageMapper {

	private final PageService pages;
	private final TagService tags;
	private final RelationService relations;
	private final MarkdownExporter exporter;
	private final ObjectMapper json;
	private final String baseUrl;

	public McpPageMapper(PageService pages, TagService tags, RelationService relations, MarkdownExporter exporter,
			ObjectMapper json, @Value("${wiki.base-url}") String baseUrl) {
		this.json = json;
		this.pages = pages;
		this.tags = tags;
		this.relations = relations;
		this.exporter = exporter;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public String baseUrl() {
		return baseUrl;
	}

	public String url(Page page) {
		return baseUrl + "/pages/" + page.getId();
	}

	/** The titles of the ancestors, root first ("Ruimte / Ouder"); empty for a top-level page. */
	public String path(Page page) {
		return pages.ancestors(page).stream().map(Page::getTitle).collect(Collectors.joining(" / "));
	}

	public List<String> tagNames(Page page) {
		return tags.tagsOf(page.getId()).stream().map(Tag::getName).toList();
	}

	public String markdown(Page page) {
		return exporter.exportDocument(page.getDocument(), baseUrl);
	}

	/** Just enough to recognise a page and open it. */
	public Map<String, Object> reference(Page page) {
		return map("id", page.getId().toString(), "title", page.getTitle(), "url", url(page));
	}

	/** Everything about a page except its content. */
	public Map<String, Object> metadata(Page page) {
		return map(
			"id", page.getId().toString(),
			"title", page.getTitle(),
			"url", url(page),
			"path", path(page),
			"parentId", page.getParentId() == null ? null : page.getParentId().toString(),
			"version", page.getLockVersion(),
			"revision", page.getCurrentRevision(),
			"status", page.getStatus() == null ? null : page.getStatus().getLabel(),
			"owner", pages.displayNameOf(page.getOwner()),
			"tags", tagNames(page),
			"updatedAt", page.getUpdatedAt() == null ? null : page.getUpdatedAt().toString(),
			"updatedBy", pages.displayNameOf(page.getUpdatedBy()));
	}

	/** Metadata, the subpages and related pages, and the content as Markdown. */
	public Map<String, Object> full(Page page) {
		Map<String, Object> result = metadata(page);
		result.put("children", pages.children(page.getId()).stream().map(this::reference).toList());
		result.put("related", relations.relatedOf(page.getId()).stream().map(this::reference).toList());
		result.put("markdown", markdown(page));
		// Formatting the Markdown above does not show; update_page refuses to drop it unasked.
		List<String> losses = MarkdownLossDetector.inspect(json.readTree(page.getDocument()));
		if (!losses.isEmpty()) {
			result.put("warnings", losses.stream()
				.map(loss -> loss + " (not visible in the Markdown; kept as long as you leave this block unchanged)")
				.toList());
		}
		return result;
	}

}
