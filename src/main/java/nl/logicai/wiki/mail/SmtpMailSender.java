package nl.logicai.wiki.mail;

import java.time.Duration;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends the reset link and the invitation as plain-text Dutch e-mails through the configured SMTP server. */
public class SmtpMailSender implements WikiMailSender {

	private final JavaMailSender sender;
	private final String from;

	public SmtpMailSender(JavaMailSender sender, String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public void sendPasswordReset(WikiUser user, String link, Duration validFor) {
		send(user.getEmail(), "LogicAI Wiki: nieuw wachtwoord instellen", resetBody(user, link, validFor));
	}

	@Override
	public void sendInvitation(String email, Role role, String invitedBy, String link, Duration validFor) {
		send(email, "LogicAI Wiki: uitnodiging voor een account", invitationBody(role, invitedBy, link, validFor));
	}

	private void send(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		sender.send(message);
	}

	static String resetBody(WikiUser user, String link, Duration validFor) {
		return "Hallo " + user.getDisplayName() + ",\n\n"
				+ "Er is gevraagd om een nieuw wachtwoord voor je account “" + user.getUsername()
				+ "” op de LogicAI Wiki.\n"
				+ "Kies via deze link een nieuw wachtwoord; de link is " + validFor.toMinutes()
				+ " minuten geldig en werkt één keer:\n\n"
				+ link + "\n\n"
				+ "Heb je dit niet aangevraagd? Negeer deze mail; je wachtwoord blijft ongewijzigd.\n";
	}

	static String invitationBody(Role role, String invitedBy, String link, Duration validFor) {
		return "Hallo,\n\n"
				+ invitedBy + " heeft je uitgenodigd voor de LogicAI Wiki, de interne kenniswiki van LogicAI, "
				+ "met de rol " + role.getLabel() + ".\n"
				+ "Kies via deze link je gebruikersnaam en wachtwoord; de link is " + validFor.toMinutes()
				+ " minuten geldig en werkt één keer:\n\n"
				+ link + "\n\n"
				+ "Verwacht je deze uitnodiging niet? Negeer deze mail; er wordt dan geen account aangemaakt.\n";
	}

}
