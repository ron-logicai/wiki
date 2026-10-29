package nl.logicai.wiki.controllers;

import java.time.Duration;
import java.time.Instant;

import nl.logicai.wiki.mail.MailTemplates;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Voorbeeld van de mails in de browser, alleen onder profiel {@code local} (ontwerpen zonder te mailen):
 * {@code /dev/mail/uitnodiging} en {@code /dev/mail/wachtwoord-herstellen}, admin-only via {@code SecurityConfig}.
 * Toont de HTML zoals de mailclient hem krijgt; de Thymeleaf-cache staat lokaal uit, dus opslaan en verversen volstaat.
 */
@RestController
@Profile("local")
public class MailPreviewController {

	private final MailTemplates templates;
	private final String baseUrl;

	public MailPreviewController(MailTemplates templates, @Value("${wiki.base-url}") String baseUrl) {
		this.templates = templates;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	@GetMapping(value = "/dev/mail/uitnodiging", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> uitnodiging() {
		String link = baseUrl + "/accountaanmaken?token=voorbeeld-token-alleen-voor-de-preview";
		return html(templates.invitation(Role.EDITOR, "Ellen", link, Duration.ofHours(1)).html());
	}

	@GetMapping(value = "/dev/mail/wachtwoord-herstellen", produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> wachtwoordHerstellen() {
		WikiUser user = WikiUser.create("ron", "Ron", "ron@example.com", null, Role.ADMIN, "preview", Instant.now());
		String link = baseUrl + "/wachtwoord-herstellen?token=voorbeeld-token-alleen-voor-de-preview";
		return html(templates.passwordReset(user, link, Duration.ofHours(1)).html());
	}

	private static ResponseEntity<String> html(String body) {
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
	}

}
