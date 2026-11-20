package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.services.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * API keys voor de REST API (docs/api.md). Een gebruiker met rol Editor of hoger maakt op /account/api-keys
 * eigen keys aan en trekt ze in; de key zelf staat alleen in de melding direct na het aanmaken. Een
 * beheerder ziet alle keys op /admin/api-keys (admin-only via {@code SecurityConfig} en nogmaals via
 * {@code ApiKeyService}). Heeft de zijbalk, dus staat in {@code GlobalModelAdvice}.
 */
@Controller
public class ApiKeyController {

	private static final String INGETROKKEN = "De API key is ingetrokken en werkt niet meer.";
	private static final String BESTAAT_NIET = "Deze API key bestaat niet meer.";

	private final ApiKeyService keys;
	private final String apiUrl;

	public ApiKeyController(ApiKeyService keys, @Value("${wiki.base-url}") String baseUrl) {
		this.keys = keys;
		this.apiUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/api/v1";
	}

	@GetMapping("/account/api-keys")
	public String eigen(Authentication auth, Model model) {
		model.addAttribute("sleutels", keys.keysOf(auth.getName()));
		model.addAttribute("apiUrl", apiUrl);
		return "account-api-keys";
	}

	@PostMapping("/account/api-keys")
	public String aanmaken(@RequestParam(required = false) String naam, Authentication auth, RedirectAttributes redirect) {
		try {
			var nieuw = keys.create(auth.getName(), naam);
			// Only the flash attribute ever holds the key; after this one page view it is gone.
			redirect.addFlashAttribute("nieuweKey", nieuw.token());
			redirect.addFlashAttribute("nieuweKeyNaam", nieuw.sleutel().naam());
		}
		catch (InvalidUserException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/account/api-keys";
	}

	@PostMapping("/account/api-keys/{id}/intrekken")
	public String eigenIntrekken(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
		boolean ingetrokken = keys.revoke(id, auth.getName());
		redirect.addFlashAttribute(ingetrokken ? "melding" : "fout", ingetrokken ? INGETROKKEN : BESTAAT_NIET);
		return "redirect:/account/api-keys";
	}

	@GetMapping("/admin/api-keys")
	public String alle(Model model) {
		model.addAttribute("sleutels", keys.allKeys());
		model.addAttribute("apiUrl", apiUrl);
		return "api-keys";
	}

	@PostMapping("/admin/api-keys/{id}/intrekken")
	public String alleIntrekken(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
		boolean ingetrokken = keys.revokeAsAdmin(id, auth.getName());
		redirect.addFlashAttribute(ingetrokken ? "melding" : "fout", ingetrokken ? INGETROKKEN : BESTAAT_NIET);
		return "redirect:/admin/api-keys";
	}

}
