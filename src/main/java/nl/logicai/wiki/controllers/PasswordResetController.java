package nl.logicai.wiki.controllers;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.services.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * "Wachtwoord vergeten" (spec F-01, uitbreiding), zonder sessie en zonder zijbalk (dus niet in
 * {@code GlobalModelAdvice}). /wachtwoord-vergeten vraagt een e-mailadres en stuurt de herstel-link;
 * /wachtwoord-herstellen is de pagina achter die link. Het token staat na de eerste GET alleen nog in
 * een hidden field, nooit meer in een URL.
 */
@Controller
public class PasswordResetController {

	private final PasswordResetService resetService;
	private final LoginController loginController;

	public PasswordResetController(PasswordResetService resetService, LoginController loginController) {
		this.resetService = resetService;
		this.loginController = loginController;
	}

	@GetMapping("/wachtwoord-vergeten")
	public String vergeten(Model model) {
		model.addAttribute("entra", loginController.entraConfigured());
		return "wachtwoord-vergeten";
	}

	@PostMapping("/wachtwoord-vergeten")
	public String aanvragen(@RequestParam(defaultValue = "") String email, Model model) {
		try {
			resetService.request(email);
			// Redirect after post, so a refresh does not request a second mail.
			return "redirect:/wachtwoord-vergeten?verzonden";
		}
		catch (InvalidUserException ex) {
			model.addAttribute("fout", ex.getMessage());
			model.addAttribute("email", email);
			model.addAttribute("entra", loginController.entraConfigured());
			return "wachtwoord-vergeten";
		}
	}

	@GetMapping("/wachtwoord-herstellen")
	public String herstellen(@RequestParam(required = false) String token, Model model) {
		if (resetService.userForToken(token).isEmpty()) {
			model.addAttribute("ongeldig", true);
		}
		else {
			model.addAttribute("token", token);
		}
		return "wachtwoord-herstellen";
	}

	@PostMapping("/wachtwoord-herstellen")
	public String opslaan(@RequestParam(defaultValue = "") String token, @RequestParam(defaultValue = "") String password,
			@RequestParam(defaultValue = "") String passwordRepeat, Model model) {
		try {
			resetService.reset(token, password, passwordRepeat);
			return "redirect:/login?reset";
		}
		catch (InvalidUserException ex) {
			if (resetService.userForToken(token).isEmpty()) {
				model.addAttribute("ongeldig", true);
			}
			else {
				model.addAttribute("token", token);
				model.addAttribute("fout", ex.getMessage());
			}
			return "wachtwoord-herstellen";
		}
	}

}
