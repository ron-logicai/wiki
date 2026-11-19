package nl.logicai.wiki.controllers;

import nl.logicai.wiki.services.McpConnectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gekoppelde AI-agents (docs/mcp.md). Iedere gebruiker ziet op /account/koppelingen welke apps namens hem of
 * haar met de wiki werken en trekt een koppeling in; een beheerder ziet ze allemaal op /admin/koppelingen
 * (admin-only via {@code SecurityConfig} en nogmaals via {@code McpConnectionService}). Bestaat alleen als de
 * MCP-server aan staat. Heeft de zijbalk, dus staat in {@code GlobalModelAdvice}.
 */
@Controller
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpConnectionsController {

	private final McpConnectionService connections;
	private final String mcpUrl;

	public McpConnectionsController(McpConnectionService connections, @Value("${wiki.base-url}") String baseUrl) {
		this.connections = connections;
		this.mcpUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/mcp";
	}

	@GetMapping("/account/koppelingen")
	public String eigen(Authentication auth, Model model) {
		model.addAttribute("koppelingen", connections.koppelingenVan(auth.getName()));
		model.addAttribute("mcpUrl", mcpUrl);
		return "account-koppelingen";
	}

	@PostMapping("/account/koppelingen/{id}/intrekken")
	public String eigenIntrekken(@PathVariable String id, Authentication auth, RedirectAttributes redirect) {
		boolean ingetrokken = connections.intrekken(id, auth.getName());
		redirect.addFlashAttribute(ingetrokken ? "melding" : "fout",
			ingetrokken ? "De koppeling is ingetrokken. De app heeft geen toegang meer." : "Deze koppeling bestaat niet meer.");
		return "redirect:/account/koppelingen";
	}

	@GetMapping("/admin/koppelingen")
	public String alle(Model model) {
		model.addAttribute("koppelingen", connections.alleKoppelingen());
		model.addAttribute("mcpUrl", mcpUrl);
		return "koppelingen";
	}

	@PostMapping("/admin/koppelingen/{id}/intrekken")
	public String alleIntrekken(@PathVariable String id, Authentication auth, RedirectAttributes redirect) {
		boolean ingetrokken = connections.intrekkenAlsBeheerder(id, auth.getName());
		redirect.addFlashAttribute(ingetrokken ? "melding" : "fout",
			ingetrokken ? "De koppeling is ingetrokken. De app heeft geen toegang meer." : "Deze koppeling bestaat niet meer.");
		return "redirect:/admin/koppelingen";
	}

}
