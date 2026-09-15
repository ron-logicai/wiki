package nl.logicai.wiki.controllers;

import nl.logicai.wiki.services.PageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Startpagina: navigatie en recent gewijzigde pagina's (spec section 5, route "/"). */
@Controller
public class WebController {

	private final PageService pageService;

	public WebController(PageService pageService) {
		this.pageService = pageService;
	}

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("recentePaginas", pageService.recentlyChanged());
		return "index";
	}

}
