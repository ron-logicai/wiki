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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;



/** Model attributes every server-rendered page needs: the sidebar page tree and the current user. */
@ControllerAdvice(assignableTypes = {WebController.class, PageController.class, TagController.class, TemplateController.class, SearchController.class, TrashController.class, AdminController.class, UserAdminController.class, AccountController.class})
class GlobalModelAdvice {

	private final PageService pageService;
	private final FavoriteService favoriteService;
	private final Namen namen;

	GlobalModelAdvice(PageService pageService, FavoriteService favoriteService, Namen namen) {
		this.pageService = pageService;
		this.favoriteService = favoriteService;
		this.namen = namen;
	}

	@ModelAttribute("boom")
	List<PageNode> boom() {
		return pageService.tree();
	}

	/** De interne gebruikersnaam (sleutel): voor vergelijkingen zoals "(jij)" op /admin/users, niet om te tonen. */
	@ModelAttribute("gebruiker")
	String gebruiker(Authentication auth) {
		return auth == null ? null : auth.getName();
	}

	/** De weergavenaam voor de zijbalk; gebruikers zien geen gebruikersnaam meer. */
	@ModelAttribute("gebruikerNaam")
	String gebruikerNaam(Authentication auth) {
		return auth == null ? null : namen.weergave(auth.getName());
	}

	/** Rolnaam onder de gebruikersnaam in de zijbalk (ontwerp): Beheerder, Redacteur of Lezer. */
	@ModelAttribute("rolLabel")
	String rolLabel(Authentication auth) {
		if (auth == null) {
			return null;
		}
		Set<String> rollen = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
		if (rollen.contains("ROLE_ADMIN")) {
			return "Beheerder";
		}
		return rollen.contains("ROLE_EDITOR") ? "Redacteur" : "Lezer";
	}

	/** The user's favorites for the sidebar, plus their ids so the tree can mark a starred page (spec U-03). */
	@ModelAttribute
	void favorieten(Authentication auth, Model model) {
		List<Page> favorieten = auth == null ? List.of() : favoriteService.favoritesOf(auth.getName());
		Set<UUID> favorietIds = favorieten.stream().map(Page::getId).collect(Collectors.toSet());
		model.addAttribute("favorieten", favorieten);
		model.addAttribute("favorietIds", favorietIds);
	}

	/** The current path, so the star form can return to it. */
	@ModelAttribute("huidigePad")
	String huidigePad(HttpServletRequest request) {
		String query = request.getQueryString();
		return request.getRequestURI() + (query == null ? "" : "?" + query);
	}

}

