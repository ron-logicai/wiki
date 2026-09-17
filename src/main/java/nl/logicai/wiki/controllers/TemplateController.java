package nl.logicai.wiki.controllers;

import java.util.UUID;

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

/** Template overview and editing (spec F-15, routes /templates and /templates/{id}/edit). */
@Controller
public class TemplateController {

	private final TemplateService templateService;

	public TemplateController(TemplateService templateService) {
		this.templateService = templateService;
	}

	@GetMapping("/templates")
	public String overzicht(Model model) {
		model.addAttribute("templates", templateService.all());
		return "templates";
	}

	@PostMapping("/templates")
	public String aanmaken(@RequestParam String title, @RequestParam(defaultValue = "") String description,
			Authentication auth, RedirectAttributes redirect) {
		try {
			PageTemplate template = templateService.create(title, description, auth.getName());
			return "redirect:/templates/" + template.getId() + "/edit";
		}
		catch (InvalidContentException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
			return "redirect:/templates";
		}
	}

	@GetMapping("/templates/{id}/edit")
	public String bewerken(@PathVariable UUID id, Model model) {
		PageTemplate template = templateService.get(id);
		model.addAttribute("template", template);
		model.addAttribute("documentJson", PageService.embeddableJson(template.getDocument()));
		return "template-bewerken";
	}

	@PostMapping("/templates/{id}/delete")
	public String verwijderen(@PathVariable UUID id, RedirectAttributes redirect) {
		templateService.delete(id);
		redirect.addFlashAttribute("melding", "Template verwijderd. Pagina's die ermee zijn gemaakt, zijn niet gewijzigd.");
		return "redirect:/templates";
	}

}
