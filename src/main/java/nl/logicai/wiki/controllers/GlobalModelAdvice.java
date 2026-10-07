package nl.logicai.wiki.controllers;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.services.FavoriteService;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.PageService.PageNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;



/** Model attributes every server-rendered page needs: the sidebar page tree and the current user. */
@ControllerAdvice(assignableTypes = {WebController.class, PageController.class, TagController.class, TemplateController.class, SearchController.class, TrashController.class, AdminController.class, UserAdminController.class})
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

	List<Page> favorieten(Authentication auth){
		return auth == null ? List.of() : favoriteService.favoritesOf(auth.getName());
	}
	Set<UUID>favorietIds(@ModelAttribute("favorieten")List<Page>favorieten){
		return favorieten.stream().map(Page::getId).collect(Collectors.toSet());
	}
	@ModelAttribute("huidigePad")
    String huidigePad(HttpServletRequest request){
		String query = request.getQueryString();
		return request.getRequestURI() + (query == null ? "" : "? + query");
	}

