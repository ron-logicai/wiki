package nl.logicai.wiki.controllers;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.services.PageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * JSON endpoints for the editor (spec section 5, "HTTP-gedrag"). The save endpoint stores content
 * only and never controls screen routing. Responses: 200 with the new version, 400 invalid content,
 * 401 no session, 403 no rights, 404 unknown page, 409 stale base version.
 * The suggest endpoint (spec U-06) is a read-only lookup for the link popover.
 */
@RestController
@RequestMapping("/api/pages")
public class PageApiController {

	/** One page offered while inserting a link; {@code url} is the fixed page URL (spec F-16: the id is leading). */
	public record Suggestion(UUID id, String title, String path, String url) {
	}

	public record SaveRequest(
		@NotNull Long baseVersion,
		@NotBlank(message = "Een titel is verplicht.")
		@Size(max = 200, message = "De titel mag maximaal 200 tekens bevatten.") String title,
		@NotNull JsonNode document) {
	}

	public record SaveResponse(long version, int revision, Instant savedAt) {
	}

	private final PageService pages;

	public PageApiController(PageService pages) {
		this.pages = pages;
	}

	@PutMapping("/{id}/content")
	public SaveResponse save(@PathVariable UUID id, @Valid @RequestBody SaveRequest request, Authentication auth) {
		Page page = pages.saveContent(id, request.baseVersion(), request.title(), request.document(), auth.getName());
		return new SaveResponse(page.getLockVersion(), page.getCurrentRevision(), page.getUpdatedAt());
	}

	/**
	 * Active pages whose title contains {@code q}, at most ten (spec U-06). Any logged-in role may
	 * read; an empty query returns the most recently changed pages.
	 */
	@GetMapping("/suggest")
	public List<Suggestion> suggest(@RequestParam(defaultValue = "") String q) {
		return pages.suggest(q).stream()
			.map(s -> new Suggestion(s.page().getId(), s.page().getTitle(), s.path(), "/pages/" + s.page().getId()))
			.toList();
	}

}
