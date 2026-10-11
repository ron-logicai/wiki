package nl.logicai.wiki.controllers;

import java.util.Locale;
import java.util.UUID;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.NewUserForm;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.services.AdminService;
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
	private final AdminService adminService;

	public UserAdminController(UserService userService, AdminService adminService) {
		this.userService = userService;
		this.adminService = adminService;
	}

	/** De gebruikerslijst, optioneel gefilterd op naam/e-mail ('q') en rol ('rol') zoals in het ontwerp. */
	@GetMapping("/admin/users")
	public String overzicht(@ModelAttribute("form") NewUserForm form, @RequestParam(required = false) String q,
			@RequestParam(required = false) Role rol, Model model) {
		model.addAttribute("q", q == null ? "" : q.strip());
		model.addAttribute("rolFilter", rol);
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
			form.setPasswordRepeat("");
			return lijst(model);
		}
	}

	@PostMapping("/admin/users/{id}/password")
	public String wachtwoord(@PathVariable UUID id, @RequestParam String password,
			@RequestParam(defaultValue = "") String passwordRepeat, Authentication auth, RedirectAttributes redirect) {
		try {
			var user = userService.setPassword(id, password, passwordRepeat, auth.getName());
			redirect.addFlashAttribute("melding", "Wachtwoord van “" + user.getUsername() + "” is ingesteld; inloggen kan direct.");
		}
		catch (InvalidUserException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/admin/users";
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
		String q = model.getAttribute("q") instanceof String s ? s.toLowerCase(Locale.ROOT) : "";
		Role rol = model.getAttribute("rolFilter") instanceof Role r ? r : null;
		model.addAttribute("gebruikers", userService.all().stream()
			.filter(u -> rol == null || u.getRole() == rol)
			.filter(u -> q.isEmpty() || matches(u, q))
			.toList());
		model.addAttribute("rollen", Role.values());
		model.addAttribute("overzicht", adminService.overview());
		return "gebruikers";
	}

	private static boolean matches(WikiUser u, String q) {
		return u.getUsername().toLowerCase(Locale.ROOT).contains(q)
				|| (u.getDisplayName() != null && u.getDisplayName().toLowerCase(Locale.ROOT).contains(q))
				|| (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(q));
	}

}
