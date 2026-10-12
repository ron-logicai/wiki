package nl.logicai.wiki.mail;

import java.time.Duration;

import nl.logicai.wiki.models.WikiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development fallback when {@code WIKI_MAIL_HOST} is empty: the reset link goes to the log instead of
 * to the user. That deliberately puts a secret in the log (spec N-04), so this is only for profile
 * {@code local} and the tests; production must configure SMTP.
 */
public class LogResetMailSender implements ResetMailSender {

	private static final Logger log = LoggerFactory.getLogger(LogResetMailSender.class);

	@Override
	public void sendPasswordReset(WikiUser user, String link, Duration validFor) {
		log.warn("Geen SMTP (WIKI_MAIL_HOST is leeg); herstel-link voor {} ({} minuten geldig): {}",
				user.getUsername(), validFor.toMinutes(), link);
	}

}
