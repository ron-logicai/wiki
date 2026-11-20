package nl.logicai.wiki.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.FormattingLossException;
import nl.logicai.wiki.mcp.McpResults.ToolRefusal;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.services.MarkdownPageWriter;
import nl.logicai.wiki.services.MarkdownPageWriter.Source;
import nl.logicai.wiki.services.MarkdownPageWriter.Written;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.TagService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The changes an AI agent may make through MCP, each in one transaction. The work is done by
 * {@link MarkdownPageWriter}, which the REST API shares, so the usual rules apply unchanged: Editor role
 * (spec F-02), the base version check (spec F-10), validation and a revision per change (spec F-11). Every
 * change additionally leaves an audit event that says it came in through MCP; the revision itself carries
 * the name of the user the agent works for. This class adds the refusals in the words a model can act on.
 */
@Service
@Transactional
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpPageWriter {

	private final MarkdownPageWriter writer;
	private final PageService pages;
	private final TagService tags;

	public McpPageWriter(MarkdownPageWriter writer, PageService pages, TagService tags) {
		this.writer = writer;
		this.pages = pages;
		this.tags = tags;
	}

	@PreAuthorize("hasRole('EDITOR')")
	public Written create(String title, UUID parentId, String markdown, UUID templateId, List<String> tagNames,
			String actor, String client) {
		boolean hasMarkdown = markdown != null && !markdown.isBlank();
		if (hasMarkdown && templateId != null) {
			throw new ToolRefusal("INVALID", "Pass either 'markdown' or 'templateId', not both. To fill a template, "
				+ "create the page from the template first and then change it with update_page.");
		}
		return writer.create(title, parentId, markdown, templateId, tagNames, actor, Source.mcp(client));
	}

	/**
	 * Replaces the content with the given Markdown. Blocks the Markdown left unchanged are kept as stored;
	 * when a block that did change carried formatting Markdown cannot express, the update is refused unless
	 * the caller accepts the loss.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Written update(UUID id, long baseVersion, String title, String markdown, boolean allowFormattingLoss,
			String actor, String client) {
		try {
			return writer.update(id, baseVersion, title, markdown, allowFormattingLoss, actor, Source.mcp(client));
		}
		catch (FormattingLossException e) {
			throw new ToolRefusal("FORMATTING_LOSS", "This update would remove formatting that Markdown cannot express: "
				+ String.join("; ", e.getLosses()) + ". Nothing was changed. Leave those blocks exactly as get_page "
				+ "returned them, use append_to_page to only add content, or repeat the call with "
				+ "allowFormattingLoss=true if losing this formatting is acceptable.");
		}
	}

	/** Adds content below the existing content; what is already on the page is not touched. */
	@PreAuthorize("hasRole('EDITOR')")
	public Written append(UUID id, long baseVersion, String markdown, String actor, String client) {
		return writer.append(id, baseVersion, markdown, actor, Source.mcp(client));
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

}
