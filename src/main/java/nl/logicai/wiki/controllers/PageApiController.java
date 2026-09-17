package nl.logicai.wiki.controllers;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.services.PageService;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * JSON save endpoint for the editor (spec section 5, "HTTP-gedrag"). It stores content only;
 * it never controls screen routing. Responses: 200 with the new version, 400 invalid content,
 * 401 no session, 403 no rights, 404 unknown page, 409 stale base version.
 */
@RestController
@RequestMapping("/api/pages")
public class PageApiController {

	public record SaveRequest(
		@NotNull Long baseVersion,
		@NotBlank @Size(max = 200) String title,
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

}
