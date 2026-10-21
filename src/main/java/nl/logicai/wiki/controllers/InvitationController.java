package nl.logicai.wiki.controllers;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.UserInvitation;
import nl.logicai.wiki.services.InvitationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * De pagina achter de uitnodigingsmail (spec F-01: geen openbare registratie, alleen met een geldig
 * token). Zonder sessie en zonder zijbalk (dus niet in {@code GlobalModelAdvice}); de route staat
 * nergens in de navigatie. Het token staat na de eerste GET alleen nog in een hidden field, nooit
 * meer in een URL.
 */
@Controller
public class InvitationController {

	private final InvitationService invitationService;

	public InvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@GetMapping("/accountaanmaken")
	public String formulier(@RequestParam(required = false) String token, Model model) {
		invitationService.invitationForToken(token).ifPresentOrElse(
				invitation -> formulier(model, token, invitation),
				() -> model.addAttribute("ongeldig", true));
		return "accountaanmaken";
	}

	@PostMapping("/accountaanmaken")
	public String aanmaken(@RequestParam(defaultValue = "") String token, @RequestParam(defaultValue = "") String username,
			@RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "") String passwordRepeat,
			Model model) {
		try {
			invitationService.accept(token, username, password, passwordRepeat);
			return "redirect:/login?aangemaakt";
		}
		catch (InvalidUserException ex) {
			invitationService.invitationForToken(token).ifPresentOrElse(
					invitation -> {
						formulier(model, token, invitation);
						model.addAttribute("fout", ex.getMessage());
						model.addAttribute("username", username);
					},
					() -> model.addAttribute("ongeldig", true));
			return "accountaanmaken";
		}
	}

	private static void formulier(Model model, String token, UserInvitation invitation) {
		model.addAttribute("token", token);
		model.addAttribute("uitnodiging", invitation);
	}

}
