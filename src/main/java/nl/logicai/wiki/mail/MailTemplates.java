package nl.logicai.wiki.mail;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * The subject, plain text and HTML of the two mails with a single-use link. The HTML comes from the Thymeleaf
 * templates under {@code templates/mail/} (design of 28-10-2026; same engine as {@code PdfExporter}), the plain
 * text is the fallback for clients without HTML and is what the log sender shows. Every value from outside
 * (names, addresses, the link) goes through {@code th:text}/{@code th:href}, so it is escaped.
 */
@Component
public class MailTemplates {

	public static final String INVITATION_SUBJECT = "LogicAI Wiki: uitnodiging voor een account";
	public static final String RESET_SUBJECT = "LogicAI Wiki: nieuw wachtwoord instellen";

	private static final Locale NL = Locale.forLanguageTag("nl-NL");

	/** One mail ready to send: both bodies say the same, in plain text and in HTML. */
	public record Rendered(String subject, String text, String html) {
	}

	private final SpringTemplateEngine templateEngine;

	public MailTemplates(SpringTemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	/** Invitation from {@code invitedBy} (display name of the admin) for an account with {@code role}. */
	public Rendered invitation(Role role, String invitedBy, String link, Duration validFor) {
		String validity = validity(validFor);
		String text = "Hoi,\n\n"
				+ invitedBy + " heeft een account als " + role.getLabel() + " voor je klaargezet in de interne wiki van LogicAI. "
				+ "Daar staan al onze werkwijzen, afspraken en handleidingen op één plek.\n"
				+ "Kies via deze link je naam en wachtwoord (inloggen doe je daarna met dit e-mailadres); de link is " + validity
				+ " geldig en werkt één keer:\n\n"
				+ link + "\n\n"
				+ "Verwachtte je deze e-mail niet? Dan kun je hem negeren; er wordt geen account aangemaakt zonder jouw actie.\n\n"
				+ "Groet,\n" + invitedBy + "\n";
		String html = render("mail/uitnodiging", Map.of(
				"uitgenodigdDoor", invitedBy,
				"rol", role.getLabel(),
				"link", link,
				"geldigheid", validity));
		return new Rendered(INVITATION_SUBJECT, text, html);
	}

	/** "Wachtwoord vergeten" for an existing user. */
	public Rendered passwordReset(WikiUser user, String link, Duration validFor) {
		String validity = validity(validFor);
		String text = "Hoi " + user.getDisplayName() + ",\n\n"
				+ "Er is gevraagd om een nieuw wachtwoord voor je account “" + user.getEmail() + "” op de LogicAI wiki.\n"
				+ "Kies via deze link een nieuw wachtwoord; de link is " + validity + " geldig en werkt één keer:\n\n"
				+ link + "\n\n"
				+ "Heb je dit niet aangevraagd? Negeer deze mail; je wachtwoord blijft ongewijzigd.\n";
		String html = render("mail/wachtwoord-herstellen", Map.of(
				"naam", user.getDisplayName(),
				"email", user.getEmail(),
				"link", link,
				"geldigheid", validity));
		return new Rendered(RESET_SUBJECT, text, html);
	}

	private String render(String template, Map<String, Object> variables) {
		Context context = new Context(NL);
		context.setVariables(variables);
		return templateEngine.process(template, context);
	}

	/** "1 uur", "2 uur", "1 dag", "7 dagen", otherwise minutes ("45 minuten"). */
	static String validity(Duration validFor) {
		long minutes = Math.max(1, validFor.toMinutes());
		if (minutes % (24 * 60) == 0) {
			long days = minutes / (24 * 60);
			return days == 1 ? "1 dag" : days + " dagen";
		}
		if (minutes % 60 == 0) {
			return minutes / 60 + " uur";
		}
		return minutes == 1 ? "1 minuut" : minutes + " minuten";
	}

}
