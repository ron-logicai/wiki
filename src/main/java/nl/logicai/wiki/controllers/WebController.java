package nl.logicai.wiki.controllers;

import java.util.List;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.services.PageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Startpagina: ruimtes en recent bijgewerkte pagina's (spec section 5, route "/"). */
@Controller
public class WebController {

	private final PageService pageService;

	public WebController(PageService pageService) {
		this.pageService = pageService;
	}

	/** A recently changed page with the space (root page) it belongs to, for the home list. */
	public record RecentePagina(Page pagina, String ruimte) {
	}

	@GetMapping("/")
	public String home(Model model) {
		List<RecentePagina> recent = pageService.recentlyChanged().stream()
			.map(p -> new RecentePagina(p, ruimteVan(p)))
			.toList();
		model.addAttribute("recentePaginas", recent);
		return "index";
	}

	private String ruimteVan(Page page) {
		List<Page> ouders = pageService.ancestors(page);
		return ouders == null || ouders.isEmpty() ? null : ouders.getFirst().getTitle();
	}

}
