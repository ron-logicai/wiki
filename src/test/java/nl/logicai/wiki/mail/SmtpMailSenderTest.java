package nl.logicai.wiki.mail;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import nl.logicai.wiki.models.Role;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link SmtpMailSender} builds a multipart/alternative message: the HTML with the plain text as fallback. */
class SmtpMailSenderTest {

	private static final String LINK = "http://localhost:8080/accountaanmaken?token=abc123";

	/** A JavaMailSender that keeps the message instead of opening a transport. */
	static class CapturingSender extends JavaMailSenderImpl {
		final List<MimeMessage> sent = new ArrayList<>();

		@Override
		protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
			for (MimeMessage message : mimeMessages) {
				try {
					message.saveChanges();
				}
				catch (MessagingException ex) {
					throw new IllegalStateException(ex);
				}
				sent.add(message);
			}
		}
	}

	@Test
	void invitationGoesOutAsTextPlusHtml() throws Exception {
		CapturingSender transport = new CapturingSender();
		SmtpMailSender sender = new SmtpMailSender(transport, "info@logicai.nl", new MailTemplates(MailTemplatesTest.engine()));

		sender.sendInvitation("nieuw@example.com", Role.EDITOR, "Ellen", LINK, Duration.ofHours(1));

		assertThat(transport.sent).hasSize(1);
		MimeMessage message = transport.sent.getFirst();
		assertThat(message.getFrom()[0].toString()).isEqualTo("info@logicai.nl");
		assertThat(message.getAllRecipients()[0].toString()).isEqualTo("nieuw@example.com");
		assertThat(message.getSubject()).isEqualTo("LogicAI Wiki: uitnodiging voor een account");

		List<Part> parts = new ArrayList<>();
		collect(message, parts);
		Part alternative = parts.stream().filter(p -> isType(p, "multipart/alternative")).findFirst().orElseThrow();
		Part text = parts.stream().filter(p -> isType(p, "text/plain")).findFirst().orElseThrow();
		Part html = parts.stream().filter(p -> isType(p, "text/html")).findFirst().orElseThrow();
		assertThat(alternative).isNotNull();
		assertThat(text.getContent().toString()).contains("Ellen heeft een account als Editor").contains(LINK);
		assertThat(html.getContent().toString()).contains("href=\"" + LINK + "\"").contains("<strong>Editor</strong>");
	}

	private static boolean isType(Part part, String type) {
		try {
			return part.isMimeType(type);
		}
		catch (MessagingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void collect(Part part, List<Part> into) throws MessagingException, IOException {
		into.add(part);
		if (part.getContent() instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart child = multipart.getBodyPart(i);
				collect(child, into);
			}
		}
	}

}
