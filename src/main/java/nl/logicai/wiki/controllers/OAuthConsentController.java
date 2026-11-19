package nl.logicai.wiki.controllers;

import java.net.URI;
import java.util.List;

import nl.logicai.wiki.config.McpSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * De toestemmingspagina bij het koppelen van een AI-agent (docs/mcp.md): na het inloggen ziet de gebruiker
 * welke app toegang vraagt en kiest "Toestaan" of "Weigeren". Het formulier gaat naar het
 * autorisatie-endpoint van Spring Security ({@code POST /oauth2/authorize}); deze controller toont alleen
 * de pagina. De naam van de app komt uit de registratie van die app zelf en is dus niet te vertrouwen;
 * daarom staat het adres waar de app de gebruiker naar terugstuurt erbij, en dat adres komt van de
 * toegestane lijst ({@code wiki.mcp.allowed-redirect-uris}).
 */
@Controller
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class OAuthConsentController {

	private final RegisteredClientRepository clients;

	public OAuthConsentController(RegisteredClientRepository clients) {
		this.clients = clients;
	}

	@GetMapping(McpSecurityConfig.CONSENT_PAGE)
	public String toestemming(@RequestParam("client_id") String clientId, @RequestParam String state, Model model) {
		RegisteredClient client = clients.findByClientId(clientId);
		if (client == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		model.addAttribute("appNaam", client.getClientName());
		model.addAttribute("terugNaar", hosts(client));
		model.addAttribute("clientId", client.getClientId());
		model.addAttribute("state", state);
		return "koppeling-toestaan";
	}

	/** De hosts waar de app de gebruiker naar terugstuurt, bijvoorbeeld "claude.ai" of "localhost". */
	static List<String> hosts(RegisteredClient client) {
		return client.getRedirectUris().stream()
			.map(uri -> {
				try {
					String host = URI.create(uri).getHost();
					return host == null ? uri : host;
				}
				catch (IllegalArgumentException e) {
					return uri;
				}
			})
			.distinct()
			.sorted()
			.toList();
	}

}
