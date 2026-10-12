package nl.logicai.wiki.mail;

import java.time.Duration;

import nl.logicai.wiki.models.WikiUser;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends the reset link as a plain-text Dutch e-mail through the configured SMTP server. */
public class SmtpResetMailSender implements ResetMailSender {

	private final JavaMailSender sender;
	private final String from;

	public SmtpResetMailSender(JavaMailSender sender, String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public void sendPasswordReset(WikiUser user, String link, Duration validFor) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(user.getEmail());
		message.setSubject("LogicAI Wiki: nieuw wachtwoord instellen");
		message.setText(body(user, link, validFor));
		sender.send(message);
	}

	static String body(WikiUser user, String link, Duration validFor) {
		return "Hallo " + user.getDisplayName() + ",\n\n"
				+ "Er is gevraagd om een nieuw wachtwoord voor je account “" + user.getUsername()
				+ "” op de LogicAI Wiki.\n"
				+ "Kies via deze link een nieuw wachtwoord; de link is " + validFor.toMinutes()
				+ " minuten geldig en werkt één keer:\n\n"
				+ link + "\n\n"
				+ "Heb je dit niet aangevraagd? Negeer deze mail; je wachtwoord blijft ongewijzigd.\n";
	}

}
