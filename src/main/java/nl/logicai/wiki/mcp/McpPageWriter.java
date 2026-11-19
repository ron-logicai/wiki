package nl.logicai.wiki.mcp;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.mcp.McpResults.ToolRefusal;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.services.MarkdownImporter;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.TagService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * The changes an AI agent may make through MCP, each in one transaction: the Markdown is turned into
 * blocks ({@link MarkdownImporter}) and saved through {@link PageService}, so the usual rules apply
 * unchanged: Editor role (spec F-02), the base version check (spec F-10), validation and a revision per
 * change (spec F-11). Every change additionally leaves an audit event that says it came in through MCP;
 * the revision itself carries the name of the user the agent works for.
 */
@Service
@Transactional
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpPageWriter {

	/** A changed page plus what the Markdown import could not take over as written. */
	public record Written(Page page, List<String> warnings) {
	}

	private final PageService pages;
	private final TagService tags;
	private final MarkdownImporter importer;
	private final McpPageMapper mapper;
	private final AuditEventRepository audit;
	private final ObjectMapper json;
	private final Clock clock;

	public McpPageWriter(PageService pages, TagService tags, MarkdownImporter importer, McpPageMapper mapper,
			AuditEventRepository audit, ObjectMapper json, Clock clock) {
		this.pages = pages;
		this.tags = tags;
		this.importer = importer;
		this.mapper = mapper;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	@PreAuthorize("hasRole('EDITOR')")
	public Written create(String title, UUID parentId, String markdown, UUID templateId, List<String> tagNames,
			String actor, String client) {
		boolean hasMarkdown = markdown != null && !markdown.isBlank();
		if (hasMarkdown && templateId != null) {
			throw new ToolRefusal("INVALID", "Pass either 'markdown' or 'templateId', not both. To fill a template, "
				+ "create the page from the template first and then change it with update_page.");
		}
		Page page = pages.create(title, parentId, actor, templateId);
		List<String> warnings = List.of();
		if (hasMarkdown) {
			var imported = importer.importDocument(markdown, mapper.baseUrl(), page.getTitle());
			warnings = imported.warnings();
			page = pages.saveContent(page.getId(), page.getLockVersion(), page.getTitle(), imported.blocks(), actor);
		}
		if (tagNames != null) {
			for (String name : tagNames) {
				tags.attach(page.getId(), name, actor);
			}
		}
		audit.save(AuditEvent.of(AuditEvent.MCP_CREATE, page.getId(), actor, clock.instant(), via(client)));
		return new Written(page, warnings);
	}

	/**
	 * Replaces the content with the given Markdown. Blocks the Markdown left unchanged are kept as stored;
	 * when a block that did change carried formatting Markdown cannot express, the update is refused unless
	 * the caller accepts the loss.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Written update(UUID id, long baseVersion, String title, String markdown, boolean allowFormattingLoss,
			String actor, String client) {
		Page page = current(id, baseVersion);
		String newTitle = title == null || title.isBlank() ? page.getTitle() : title;
		var merged = importer.merge(json.readTree(page.getDocument()), markdown, mapper.baseUrl(), newTitle);
		if (!merged.losses().isEmpty() && !allowFormattingLoss) {
			throw new ToolRefusal("FORMATTING_LOSS", "This update would remove formatting that Markdown cannot express: "
				+ String.join("; ", merged.losses()) + ". Nothing was changed. Leave those blocks exactly as get_page "
				+ "returned them, use append_to_page to only add content, or repeat the call with "
				+ "allowFormattingLoss=true if losing this formatting is acceptable.");
		}
		return save(page, newTitle, merged.blocks(), merged.warnings(), actor, client);
	}

	/** Adds content below the existing content; what is already on the page is not touched. */
	@PreAuthorize("hasRole('EDITOR')")
	public Written append(UUID id, long baseVersion, String markdown, String actor, String client) {
		Page page = current(id, baseVersion);
		var imported = importer.importDocument(markdown, mapper.baseUrl());
		ArrayNode blocks = json.createArrayNode();
		blocks.addAll((ArrayNode) json.readTree(page.getDocument()));
		blocks.addAll(imported.blocks());
		return save(page, page.getTitle(), blocks, imported.warnings(), actor, client);
	}

	@PreAuthorize("hasRole('EDITOR')")
	public List<String> addTag(UUID id, String name, String actor) {
		tags.attach(id, name, actor);
		return tagNames(id);
	}

	@PreAuthorize("hasRole('EDITOR')")
	public List<String> removeTag(UUID id, String name) {
		pages.getActive(id);
		String wanted = name == null ? "" : name.strip();
		for (Tag tag : tags.tagsOf(id)) {
			if (tag.getName().equalsIgnoreCase(wanted)) {
				tags.detach(id, tag.getId());
			}
		}
		return tagNames(id);
	}

	private List<String> tagNames(UUID id) {
		return new ArrayList<>(tags.tagsOf(id).stream().map(Tag::getName).toList());
	}

	/** The page, provided the caller saw its current version; checked first so a stale caller re-reads before anything else. */
	private Page current(UUID id, long baseVersion) {
		Page page = pages.getActive(id);
		if (page.getLockVersion() != baseVersion) {
			throw new PageConflictException(page.getLockVersion());
		}
		return page;
	}

	private Written save(Page page, String title, JsonNode blocks, List<String> warnings, String actor, String client) {
		long before = page.getLockVersion();
		Page saved = pages.saveContent(page.getId(), before, title, blocks, actor);
		if (saved.getLockVersion() != before) {
			audit.save(AuditEvent.of(AuditEvent.MCP_UPDATE, saved.getId(), actor, clock.instant(),
				via(client) + ", revisie " + saved.getCurrentRevision()));
		}
		return new Written(saved, warnings);
	}

	private static String via(String client) {
		String name = client == null || client.isBlank() ? "" : " (" + client.strip() + ")";
		String details = "via MCP" + name;
		return details.length() > 200 ? details.substring(0, 200) : details;
	}

}
