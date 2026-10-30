package nl.logicai.wiki.controllers;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.UserInvitation;
import nl.logicai.wiki.services.InvitationService;
import nl.logicai.wiki.services.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * De pagina achter de uitnodigingsmail (spec F-01: geen openbare registratie, alleen met een geldig
 * token). Zonder sessie en zonder zijbalk (dus niet in {@code GlobalModelAdvice}); de route staat
 * nergens in de navigatie. Het token staat na de eerste GET alleen nog in een hidden field, nooit
 * meer in een URL. De genodigde kiest naam en wachtwoord; e-mail en rol komen uit de uitnodiging en
 * met het e-mailadres logt de genodigde in (de interne gebruikersnaam wordt afgeleid).
 */
@Controller
public class InvitationController {

	private final InvitationService invitationService;
	private final UserService userService;

	public InvitationController(InvitationService invitationService, UserService userService) {
		this.invitationService = invitationService;
		this.userService = userService;
	}

	@GetMapping("/accountaanmaken")
	public String formulier(@RequestParam(required = false) String token, Model model) {
		invitationService.invitationForToken(token).ifPresentOrElse(
				invitation -> formulier(model, token, invitation),
				() -> model.addAttribute("ongeldig", true));
		return "accountaanmaken";
	}

	@PostMapping("/accountaanmaken")
	public String aanmaken(@RequestParam(defaultValue = "") String token, @RequestParam(defaultValue = "") String displayName,
			@RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "") String passwordRepeat,
			Model model) {
		try {
			invitationService.accept(token, displayName, password, passwordRepeat);
			return "redirect:/login?aangemaakt";
		}
		catch (InvalidUserException ex) {
			invitationService.invitationForToken(token).ifPresentOrElse(
					invitation -> {
						formulier(model, token, invitation);
						model.addAttribute("fout", ex.getMessage());
						model.addAttribute("displayName", displayName);
					},
					() -> model.addAttribute("ongeldig", true));
			return "accountaanmaken";
		}
	}

	private void formulier(Model model, String token, UserInvitation invitation) {
		model.addAttribute("token", token);
		model.addAttribute("uitnodiging", invitation);
		// De uitnodiger als weergavenaam ("Ellen"), niet als gebruikersnaam.
		model.addAttribute("uitgenodigdDoor", userService.displayNameOf(invitation.getInvitedBy()));
	}

}
