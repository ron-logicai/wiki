package nl.logicai.wiki.mail;

import java.time.Duration;

import nl.logicai.wiki.models.WikiUser;

/** Delivers the "wachtwoord vergeten" link: by SMTP, or to the log when no SMTP host is configured. */
public interface ResetMailSender {

	void sendPasswordReset(WikiUser user, String link, Duration validFor);

}
