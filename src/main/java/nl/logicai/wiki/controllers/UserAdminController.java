package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.NewUserForm;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.services.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** User and role management for admins (spec section 1, route /admin/users). Access is enforced in SecurityConfig. */
@Controller
public class UserAdminController {

	private final UserService userService;

	public UserAdminController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/admin/users")
	public String overzicht(@ModelAttribute("form") NewUserForm form, Model model) {
		return lijst(model);
	}

	@PostMapping("/admin/users")
	public String aanmaken(@ModelAttribute("form") NewUserForm form, Authentication auth, Model model,
			RedirectAttributes redirect) {
		try {
			userService.create(form, auth.getName());
			redirect.addFlashAttribute("melding", "Gebruiker “" + form.getUsername().trim() + "” is aangemaakt.");
			return "redirect:/admin/users";
		}
		catch (InvalidUserException ex) {
			model.addAttribute("fout", ex.getMessage());
			form.setPassword("");
			return lijst(model);
		}
	}

	@PostMapping("/admin/users/{id}/role")
	public String rol(@PathVariable UUID id, @RequestParam Role role, Authentication auth, RedirectAttributes redirect) {
		try {
			var user = userService.changeRole(id, role, auth.getName());
			redirect.addFlashAttribute("melding", "Rol van “" + user.getUsername() + "” is nu " + role.getLabel() + ".");
		}
		catch (InvalidUserException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/admin/users";
	}

	@PostMapping("/admin/users/{id}/active")
	public String actief(@PathVariable UUID id, @RequestParam boolean active, Authentication auth,
			RedirectAttributes redirect) {
		try {
			var user = userService.setActive(id, active, auth.getName());
			redirect.addFlashAttribute("melding", "“" + user.getUsername() + "” is " + (active ? "geactiveerd." : "gedeactiveerd."));
		}
		catch (InvalidUserException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/admin/users";
	}

	private String lijst(Model model) {
		model.addAttribute("gebruikers", userService.all());
		model.addAttribute("rollen", Role.values());
		return "gebruikers";
	}

}
