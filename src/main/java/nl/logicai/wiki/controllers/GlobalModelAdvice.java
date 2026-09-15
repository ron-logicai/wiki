package nl.logicai.wiki.controllers;

import java.util.List;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.services.PageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Model attributes every server-rendered page needs: sidebar navigation and current user. */
@ControllerAdvice(assignableTypes = {WebController.class, PageController.class})
class GlobalModelAdvice {

	private final PageService pageService;

	GlobalModelAdvice(PageService pageService) {
		this.pageService = pageService;
	}

	@ModelAttribute("hoofdpaginas")
	List<Page> hoofdpaginas() {
		return pageService.rootPages();
	}

	@ModelAttribute("gebruiker")
	String gebruiker(Authentication auth) {
		return auth == null ? null : auth.getName();
	}

}
