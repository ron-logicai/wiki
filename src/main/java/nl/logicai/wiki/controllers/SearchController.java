package nl.logicai.wiki.controllers;

import nl.logicai.wiki.services.SearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Shareable search results at /search?q=&tag=&page= (spec F-13, section 5). */
@Controller
public class SearchController {

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping("/search")
	public String zoeken(@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) String tag,
			@RequestParam(defaultValue = "1") int page, Model model) {
		model.addAttribute("resultaat", searchService.search(q, tag, page));
		return "zoeken";
	}

}
