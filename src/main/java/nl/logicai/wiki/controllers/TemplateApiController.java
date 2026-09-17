package nl.logicai.wiki.controllers;

import java.util.UUID;

import jakarta.validation.Valid;
import nl.logicai.wiki.controllers.PageApiController.SaveRequest;
import nl.logicai.wiki.controllers.PageApiController.SaveResponse;
import nl.logicai.wiki.models.PageTemplate;
import nl.logicai.wiki.services.TemplateService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON save endpoint for the template editor; same request and error contract as pages. */
@RestController
@RequestMapping("/api/templates")
public class TemplateApiController {

	private final TemplateService templates;

	public TemplateApiController(TemplateService templates) {
		this.templates = templates;
	}

	@PutMapping("/{id}/content")
	public SaveResponse save(@PathVariable UUID id, @Valid @RequestBody SaveRequest request, Authentication auth) {
		PageTemplate template = templates.saveContent(id, request.baseVersion(), request.title(),
			request.document(), auth.getName());
		return new SaveResponse(template.getLockVersion(), 0, template.getUpdatedAt());
	}

}
