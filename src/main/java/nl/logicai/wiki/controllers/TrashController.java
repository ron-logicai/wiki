package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.exceptions.PageStateException;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.services.PageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The trash for editors and admins (spec F-12, route /trash). Access is enforced in SecurityConfig. */
@Controller
public class TrashController {

	private final PageService pageService;

	public TrashController(PageService pageService) {
		this.pageService = pageService;
	}

	@GetMapping("/trash")
	public String overzicht(Model model) {
		model.addAttribute("items", pageService.trash());
		model.addAttribute("plekken", pageService.moveTargets(null));
		return "prullenbak";
	}

	/**
	 * Restore a page. When the old parent is gone the form sends parentChosen=true with the
	 * chosen active parent, or an empty parentId for the top level.
	 */
	@PostMapping("/trash/{id}/restore")
	public String herstellen(@PathVariable UUID id, @RequestParam(required = false) UUID parentId,
			@RequestParam(defaultValue = "false") boolean parentChosen, Authentication auth,
			RedirectAttributes redirect) {
		try {
			Page page = pageService.restore(id, parentId, parentChosen, auth.getName());
			redirect.addFlashAttribute("melding", "“" + page.getTitle() + "” is hersteld.");
			return "redirect:/pages/" + page.getId();
		}
		catch (PageStateException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
			return "redirect:/trash";
		}
	}

}
