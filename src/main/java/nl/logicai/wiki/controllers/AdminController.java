package nl.logicai.wiki.controllers;

import nl.logicai.wiki.services.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The admin page (spec section 1, role Admin; route /admin). Access is enforced in SecurityConfig. */
@Controller
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/admin")
	public String overzicht(Model model) {
		model.addAttribute("overzicht", adminService.overview());
		model.addAttribute("audit", adminService.recentAudit());
		return "beheer";
	}

}
