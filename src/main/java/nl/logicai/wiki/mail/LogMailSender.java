package nl.logicai.wiki.mail;

import java.time.Duration;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development fallback when neither {@code WIKI_MAIL_GRAPH_SENDER} nor {@code WIKI_MAIL_HOST} is set:
 * the reset link or the invitation link goes to the log instead of to the user. That deliberately puts
 * a secret in the log (spec N-04), so this is only for profile {@code local} and the tests; production
 * must configure Microsoft Graph or SMTP.
 */
public class LogMailSender implements WikiMailSender {

	private static final Logger log = LoggerFactory.getLogger(LogMailSender.class);

	@Override
	public void sendPasswordReset(WikiUser user, String link, Duration validFor) {
		log.warn("Geen mail ingesteld (WIKI_MAIL_GRAPH_SENDER en WIKI_MAIL_HOST zijn leeg); herstel-link voor {} ({} minuten geldig): {}",
				user.getUsername(), validFor.toMinutes(), link);
	}

	@Override
	public void sendInvitation(String email, Role role, String invitedBy, String link, Duration validFor) {
		log.warn("Geen mail ingesteld (WIKI_MAIL_GRAPH_SENDER en WIKI_MAIL_HOST zijn leeg); uitnodiging van {} voor {} als {} ({} minuten geldig): {}",
				invitedBy, email, role.getLabel(), validFor.toMinutes(), link);
	}

}
