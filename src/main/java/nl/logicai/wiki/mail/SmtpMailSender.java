package nl.logicai.wiki.mail;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Sends the reset link and the invitation through the configured SMTP server as {@code multipart/alternative}:
 * the HTML from {@link MailTemplates} with its plain text as fallback.
 */
public class SmtpMailSender implements WikiMailSender {

	private final JavaMailSender sender;
	private final String from;
	private final MailTemplates templates;

	public SmtpMailSender(JavaMailSender sender, String from, MailTemplates templates) {
		this.sender = sender;
		this.from = from;
		this.templates = templates;
	}

	@Override
	public void sendPasswordReset(WikiUser user, String link, Duration validFor) {
		send(user.getEmail(), templates.passwordReset(user, link, validFor));
	}

	@Override
	public void sendInvitation(String email, Role role, String invitedBy, String link, Duration validFor) {
		send(email, templates.invitation(role, invitedBy, link, validFor));
	}

	private void send(String to, MailTemplates.Rendered mail) {
		MimeMessage message = sender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED,
					StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(mail.subject());
			helper.setText(mail.text(), mail.html());
		}
		catch (MessagingException ex) {
			throw new MailPreparationException("Mail kon niet worden opgebouwd", ex);
		}
		sender.send(message);
	}

}
