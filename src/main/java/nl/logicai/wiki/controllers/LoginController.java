package nl.logicai.wiki.controllers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Eigen loginpagina (spec F-01) in de huisstijl, in plaats van de standaardpagina van Spring Security.
 * Het formulier POST naar /login (Spring Security handelt dat af); de Entra-knop verschijnt alleen als
 * de client registration "entra" is geconfigureerd (profiel {@code entra}). Geen zijbalk, dus niet in
 * {@code GlobalModelAdvice}. "Wachtwoord vergeten?" gaat naar {@link PasswordResetController}.
 */
@Controller
public class LoginController {

	private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

	public LoginController(ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
		this.clientRegistrations = clientRegistrations;
	}

	@GetMapping("/login")
	public String login(Model model) {
		model.addAttribute("entra", entraConfigured());
		return "login";
	}

	/** Of de knop "Inloggen met Microsoft" getoond moet worden; ook gebruikt door de wachtwoordpagina's. */
	boolean entraConfigured() {
		ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
		return registrations != null && registrations.findByRegistrationId("entra") != null;
	}

}
