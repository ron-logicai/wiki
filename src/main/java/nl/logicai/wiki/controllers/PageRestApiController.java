package nl.logicai.wiki.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.security.ApiKeyAuthenticationFilter.ApiKeyDetails;
import nl.logicai.wiki.services.MarkdownExporter;
import nl.logicai.wiki.services.MarkdownLossDetector;
import nl.logicai.wiki.services.MarkdownPageWriter;
import nl.logicai.wiki.services.MarkdownPageWriter.Source;
import nl.logicai.wiki.services.MarkdownPageWriter.Written;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.TagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * The REST API for scripts and integrations (docs/api.md): create, read, change and delete pages, with the
 * content as Markdown. Only reachable with an API key ({@code ApiSecurityConfig}); the key acts as its user,
 * so the Editor role (spec F-02), the {@code baseVersion} check (spec F-10) and revisions plus audit (spec
 * F-11) apply through the same services as the web UI. {@code version} in an answer is the lock version, the
 * value to send back as {@code baseVersion}. Links are built from {@code wiki.base-url}, never from request
 * headers (spec N-02). Errors are JSON from {@link RestApiExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/pages")
public class PageRestApiController {

	/** Just enough to recognise a page and fetch it. */
	public record PageSummary(UUID id, String title, String url, long version) {
	}

	/**
	 * A page. {@code markdown} is only present when the page is read; {@code warnings} names formatting the
	 * Markdown does not show (read) or Markdown the import could not take over as written (write).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PageBody(UUID id, String title, String url, String path, UUID parentId, long version, int revision,
			String status, String owner, List<String> tags, Instant updatedAt, String updatedBy, String markdown,
			List<String> warnings) {
	}

	public record CreateRequest(
			@NotBlank(message = "Geef de pagina een titel.") @Size(max = 200, message = "De titel mag maximaal 200 tekens zijn.") String title,
			UUID parentId, String markdown, UUID templateId, List<String> tags) {
	}

	public record UpdateRequest(
			@NotNull(message = "baseVersion ontbreekt: stuur de 'version' mee die je bij het lezen kreeg.") Long baseVersion,
			@NotNull(message = "markdown ontbreekt.") String markdown,
			@Size(max = 200, message = "De titel mag maximaal 200 tekens zijn.") String title,
			Boolean allowFormattingLoss) {
	}

	public record AppendRequest(
			@NotNull(message = "baseVersion ontbreekt: stuur de 'version' mee die je bij het lezen kreeg.") Long baseVersion,
			@NotBlank(message = "markdown ontbreekt.") String markdown) {
	}

	private final PageService pages;
	private final TagService tags;
	private final MarkdownPageWriter writer;
	private final MarkdownExporter exporter;
	private final ObjectMapper json;
	private final String baseUrl;

	public PageRestApiController(PageService pages, TagService tags, MarkdownPageWriter writer, MarkdownExporter exporter,
			ObjectMapper json, @Value("${wiki.base-url}") String baseUrl) {
		this.pages = pages;
		this.tags = tags;
		this.writer = writer;
		this.exporter = exporter;
		this.json = json;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	/** The subpages of a page, or the top-level pages without {@code parentId}. */
	@GetMapping
	public List<PageSummary> list(@RequestParam(required = false) UUID parentId) {
		List<Page> found = parentId == null ? pages.rootPages() : pages.children(pages.getActive(parentId).getId());
		return found.stream().map(page -> new PageSummary(page.getId(), page.getTitle(), url(page), page.getLockVersion())).toList();
	}

	@GetMapping("/{id}")
	public PageBody get(@PathVariable UUID id) {
		Page page = pages.getActive(id);
		List<String> losses = MarkdownLossDetector.inspect(json.readTree(page.getDocument()));
		return body(page, exporter.exportDocument(page.getDocument(), baseUrl), losses);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PageBody create(@Valid @RequestBody CreateRequest request, Authentication auth) {
		return body(writer.create(request.title(), request.parentId(), request.markdown(), request.templateId(),
			request.tags(), auth.getName(), source(auth)));
	}

	/** Replaces the whole content, and the title when one is given. */
	@PutMapping("/{id}")
	public PageBody update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request, Authentication auth) {
		return body(writer.update(id, request.baseVersion(), request.title(), request.markdown(),
			Boolean.TRUE.equals(request.allowFormattingLoss()), auth.getName(), source(auth)));
	}

	/** Adds content below what is already on the page. */
	@PostMapping("/{id}/append")
	public PageBody append(@PathVariable UUID id, @Valid @RequestBody AppendRequest request, Authentication auth) {
		return body(writer.append(id, request.baseVersion(), request.markdown(), auth.getName(), source(auth)));
	}

	/** Moves the page to the trash (soft delete); refused while it has active subpages. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id, @RequestParam long baseVersion, Authentication auth) {
		pages.moveToTrash(id, baseVersion, auth.getName());
	}

	private PageBody body(Written written) {
		return body(written.page(), null, written.warnings());
	}

	private PageBody body(Page page, String markdown, List<String> warnings) {
		return new PageBody(page.getId(), page.getTitle(), url(page),
			pages.ancestors(page).stream().map(Page::getTitle).collect(Collectors.joining(" / ")),
			page.getParentId(), page.getLockVersion(), page.getCurrentRevision(),
			page.getStatus() == null ? null : page.getStatus().getLabel(), pages.displayNameOf(page.getOwner()),
			tags.tagsOf(page.getId()).stream().map(Tag::getName).toList(), page.getUpdatedAt(),
			pages.displayNameOf(page.getUpdatedBy()), markdown, warnings == null || warnings.isEmpty() ? null : warnings);
	}

	private String url(Page page) {
		return baseUrl + "/pages/" + page.getId();
	}

	/** The audit log names the key the change came in with. */
	private static Source source(Authentication auth) {
		return Source.api(auth.getDetails() instanceof ApiKeyDetails key ? key.keyName() : null);
	}

}
