package nl.logicai.wiki.controllers;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import nl.logicai.wiki.services.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Het eigen account van de ingelogde gebruiker: wachtwoord wijzigen op /account/wachtwoord (bereikbaar via
 * de naam in de zijbalk). Heeft de zijbalk, dus staat in {@code GlobalModelAdvice}.
 */
@Controller
public class AccountController {

	private final UserService userService;
	private final WikiUserRepository users;

	public AccountController(UserService userService, WikiUserRepository users) {
		this.userService = userService;
		this.users = users;
	}

	@GetMapping("/account/wachtwoord")
	public String wachtwoord(Authentication auth, Model model) {
		return formulier(auth, model);
	}

	@PostMapping("/account/wachtwoord")
	public String wijzigen(@RequestParam(defaultValue = "") String currentPassword,
			@RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "") String passwordRepeat,
			Authentication auth, Model model, RedirectAttributes redirect) {
		try {
			userService.changeOwnPassword(auth.getName(), currentPassword, password, passwordRepeat);
			redirect.addFlashAttribute("melding", "Je wachtwoord is gewijzigd.");
			return "redirect:/account/wachtwoord";
		}
		catch (InvalidUserException ex) {
			model.addAttribute("fout", ex.getMessage());
			return formulier(auth, model);
		}
	}

	private String formulier(Authentication auth, Model model) {
		boolean heeftWachtwoord = users.findByUsernameIgnoreCase(auth.getName()).map(WikiUser::hasPassword).orElse(false);
		model.addAttribute("heeftWachtwoord", heeftWachtwoord);
		return "account-wachtwoord";
	}

}
