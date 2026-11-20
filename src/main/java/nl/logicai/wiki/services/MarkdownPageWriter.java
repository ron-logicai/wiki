package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.FormattingLossException;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.repositories.AuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Page changes that arrive as Markdown from outside the browser: the MCP server (docs/mcp.md) and the REST
 * API (docs/api.md). Each change runs in one transaction: the Markdown is turned into blocks
 * ({@link MarkdownImporter}) and saved through {@link PageService}, so the usual rules apply unchanged:
 * Editor role (spec F-02), the base version check (spec F-10), validation and a revision per change (spec
 * F-11). Every change additionally leaves an audit event that says where it came from ({@link Source}); the
 * revision itself carries the name of the user.
 */
@Service
@Transactional
public class MarkdownPageWriter {

	/** A changed page plus what the Markdown import could not take over as written. */
	public record Written(Page page, List<String> warnings) {
	}

	/**
	 * Where a change came from, for the audit log.
	 *
	 * @param details e.g. "via MCP (Claude)" or "via API (naam van de key)"
	 */
	public record Source(String createAction, String updateAction, String details) {

		/** An AI agent through the MCP server; {@code client} is the name the app registered with. */
		public static Source mcp(String client) {
			return new Source(AuditEvent.MCP_CREATE, AuditEvent.MCP_UPDATE, via("MCP", client));
		}

		/** The REST API; {@code keyName} is the name the user gave the API key. */
		public static Source api(String keyName) {
			return new Source(AuditEvent.API_CREATE, AuditEvent.API_UPDATE, via("API", keyName));
		}

		private static String via(String channel, String name) {
			String details = "via " + channel + (name == null || name.isBlank() ? "" : " (" + name.strip() + ")");
			return details.length() > 200 ? details.substring(0, 200) : details;
		}

	}

	private final PageService pages;
	private final TagService tags;
	private final MarkdownImporter importer;
	private final AuditEventRepository audit;
	private final ObjectMapper json;
	private final Clock clock;
	private final String baseUrl;

	public MarkdownPageWriter(PageService pages, TagService tags, MarkdownImporter importer, AuditEventRepository audit,
			ObjectMapper json, Clock clock, @Value("${wiki.base-url}") String baseUrl) {
		this.pages = pages;
		this.tags = tags;
		this.importer = importer;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	/** A new page that starts with the given Markdown, or with a copy of a template, or empty. */
	@PreAuthorize("hasRole('EDITOR')")
	public Written create(String title, UUID parentId, String markdown, UUID templateId, List<String> tagNames,
			String actor, Source source) {
		boolean hasMarkdown = markdown != null && !markdown.isBlank();
		if (hasMarkdown && templateId != null) {
			throw new InvalidContentException("Geef 'markdown' of 'templateId' mee, niet allebei.");
		}
		Page page = pages.create(title, parentId, actor, templateId);
		List<String> warnings = List.of();
		if (hasMarkdown) {
			var imported = importer.importDocument(markdown, baseUrl, page.getTitle());
			warnings = imported.warnings();
			page = pages.saveContent(page.getId(), page.getLockVersion(), page.getTitle(), imported.blocks(), actor);
		}
		if (tagNames != null) {
			for (String name : tagNames) {
				tags.attach(page.getId(), name, actor);
			}
		}
		audit.save(AuditEvent.of(source.createAction(), page.getId(), actor, clock.instant(), source.details()));
		return new Written(page, warnings);
	}

	/**
	 * Replaces the content with the given Markdown. Blocks the Markdown left unchanged are kept as stored;
	 * when a block that did change carried formatting Markdown cannot express, the update is refused with a
	 * {@link FormattingLossException} unless the caller accepts the loss.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Written update(UUID id, long baseVersion, String title, String markdown, boolean allowFormattingLoss,
			String actor, Source source) {
		Page page = current(id, baseVersion);
		String newTitle = title == null || title.isBlank() ? page.getTitle() : title;
		var merged = importer.merge(json.readTree(page.getDocument()), markdown, baseUrl, newTitle);
		if (!merged.losses().isEmpty() && !allowFormattingLoss) {
			throw new FormattingLossException(merged.losses());
		}
		return save(page, newTitle, merged.blocks(), merged.warnings(), actor, source);
	}

	/** Adds content below the existing content; what is already on the page is not touched. */
	@PreAuthorize("hasRole('EDITOR')")
	public Written append(UUID id, long baseVersion, String markdown, String actor, Source source) {
		Page page = current(id, baseVersion);
		var imported = importer.importDocument(markdown, baseUrl);
		ArrayNode blocks = json.createArrayNode();
		blocks.addAll((ArrayNode) json.readTree(page.getDocument()));
		blocks.addAll(imported.blocks());
		return save(page, page.getTitle(), blocks, imported.warnings(), actor, source);
	}

	/** The page, provided the caller saw its current version; checked first so a stale caller re-reads before anything else. */
	private Page current(UUID id, long baseVersion) {
		Page page = pages.getActive(id);
		if (page.getLockVersion() != baseVersion) {
			throw new PageConflictException(page.getLockVersion());
		}
		return page;
	}

	private Written save(Page page, String title, JsonNode blocks, List<String> warnings, String actor, Source source) {
		long before = page.getLockVersion();
		Page saved = pages.saveContent(page.getId(), before, title, blocks, actor);
		if (saved.getLockVersion() != before) {
			audit.save(AuditEvent.of(source.updateAction(), saved.getId(), actor, clock.instant(),
				source.details() + ", revisie " + saved.getCurrentRevision()));
		}
		return new Written(saved, warnings);
	}

}
