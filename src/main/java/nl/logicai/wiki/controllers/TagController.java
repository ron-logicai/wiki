package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.exceptions.InvalidTagException;
import nl.logicai.wiki.services.TagService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tags on pages and the tag overview (spec F-14). Forms POST and redirect to a GET page. */
@Controller
public class TagController {

	private final TagService tagService;

	public TagController(TagService tagService) {
		this.tagService = tagService;
	}

	@PostMapping("/pages/{id}/tags")
	public String toevoegen(@PathVariable UUID id, @RequestParam String name, Authentication auth,
			RedirectAttributes redirect) {
		try {
			tagService.attach(id, name, auth.getName());
		}
		catch (InvalidTagException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/pages/" + id;
	}

	@PostMapping("/pages/{id}/tags/{tagId}/remove")
	public String verwijderen(@PathVariable UUID id, @PathVariable UUID tagId) {
		tagService.detach(id, tagId);
		return "redirect:/pages/" + id;
	}

	@GetMapping("/tags")
	public String overzicht(Model model) {
		model.addAttribute("tags", tagService.overview());
		return "tags";
	}

	@PostMapping("/tags/{id}/rename")
	public String hernoemen(@PathVariable UUID id, @RequestParam String name, RedirectAttributes redirect) {
		try {
			tagService.rename(id, name);
			redirect.addFlashAttribute("melding", "Tag hernoemd.");
		}
		catch (InvalidTagException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/tags";
	}

	@PostMapping("/tags/{id}/delete")
	public String verwijderen(@PathVariable UUID id, RedirectAttributes redirect) {
		tagService.delete(id);
		redirect.addFlashAttribute("melding", "Tag verwijderd. De pagina's zelf zijn niet gewijzigd.");
		return "redirect:/tags";
	}

}
