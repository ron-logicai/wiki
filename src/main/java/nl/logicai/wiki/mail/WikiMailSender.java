package nl.logicai.wiki.mail;

import java.time.Duration;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;

/**
 * Delivers the mails with a single-use link: the "wachtwoord vergeten" link and the invitation for a
 * new account. By SMTP, or to the log when no SMTP host is configured.
 */
public interface WikiMailSender {

	void sendPasswordReset(WikiUser user, String link, Duration validFor);

	/** Invitation from admin {@code invitedBy} to the address {@code email}; the account does not exist yet. */
	void sendInvitation(String email, Role role, String invitedBy, String link, Duration validFor);

}
