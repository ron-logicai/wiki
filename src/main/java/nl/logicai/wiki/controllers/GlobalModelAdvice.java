package nl.logicai.wiki.controllers;

import java.util.List;

import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.PageService.PageNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Model attributes every server-rendered page needs: the sidebar page tree and the current user. */
@ControllerAdvice(assignableTypes = {WebController.class, PageController.class, TagController.class, TemplateController.class})
class GlobalModelAdvice {

	private final PageService pageService;

	GlobalModelAdvice(PageService pageService) {
		this.pageService = pageService;
	}

	@ModelAttribute("boom")
	List<PageNode> boom() {
		return pageService.tree();
	}

	@ModelAttribute("gebruiker")
	String gebruiker(Authentication auth) {
		return auth == null ? null : auth.getName();
	}

}
