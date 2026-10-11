package nl.logicai.wiki.controllers;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.PageTemplate;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.TemplateService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Template overview and editing (spec F-15, routes /templates and /templates/{id}/edit).
 * <p>
 * Like the tags, the overview also lives at {@code /admin/templates} as the "Templates" tab of the admin
 * page (ADMIN only via SecurityConfig). The model attribute {@code basis} carries the prefix the template
 * uses for its links and form actions, so creating or deleting from the admin tab returns to that tab.
 */
@Controller
public class TemplateController {

	private final TemplateService templateService;

	public TemplateController(TemplateService templateService) {
		this.templateService = templateService;
	}

	@GetMapping({"/templates", "/admin/templates"})
	public String overzicht(HttpServletRequest request, Model model) {
		boolean beheer = TagController.inBeheer(request);
		model.addAttribute("templates", templateService.all());
		model.addAttribute("beheer", beheer);
		model.addAttribute("basis", basis(beheer));
		return "templates";
	}

	@PostMapping({"/templates", "/admin/templates"})
	public String aanmaken(@RequestParam String title, @RequestParam(defaultValue = "") String description,
			Authentication auth, HttpServletRequest request, RedirectAttributes redirect) {
		try {
			PageTemplate template = templateService.create(title, description, auth.getName());
			return "redirect:/templates/" + template.getId() + "/edit";
		}
		catch (InvalidContentException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
			return "redirect:" + basis(TagController.inBeheer(request));
		}
	}

	@GetMapping("/templates/{id}/edit")
	public String bewerken(@PathVariable UUID id, Model model) {
		PageTemplate template = templateService.get(id);
		model.addAttribute("template", template);
		model.addAttribute("documentJson", PageService.embeddableJson(template.getDocument()));
		return "template-bewerken";
	}

	@PostMapping({"/templates/{id}/delete", "/admin/templates/{id}/delete"})
	public String verwijderen(@PathVariable UUID id, HttpServletRequest request, RedirectAttributes redirect) {
		templateService.delete(id);
		redirect.addFlashAttribute("melding", "Template verwijderd. Pagina's die ermee zijn gemaakt, zijn niet gewijzigd.");
		return "redirect:" + basis(TagController.inBeheer(request));
	}

	private static String basis(boolean beheer) {
		return beheer ? "/admin/templates" : "/templates";
	}

}
