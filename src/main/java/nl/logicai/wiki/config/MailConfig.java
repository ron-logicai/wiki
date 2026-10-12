package nl.logicai.wiki.config;

import java.util.Properties;

import nl.logicai.wiki.mail.LogResetMailSender;
import nl.logicai.wiki.mail.ResetMailSender;
import nl.logicai.wiki.mail.SmtpResetMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Picks the mail delivery for "wachtwoord vergeten": SMTP when {@code WIKI_MAIL_HOST} is set, otherwise
 * the log fallback. The sender is built here instead of through {@code spring.mail.*}, see
 * {@link WikiMailProperties}.
 */
@Configuration
public class MailConfig {

	private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

	@Bean
	ResetMailSender resetMailSender(WikiMailProperties mail) {
		if (!mail.configured()) {
			log.warn("WIKI_MAIL_HOST is leeg: herstel-links voor 'wachtwoord vergeten' worden gelogd, niet gemaild. "
					+ "Zet WIKI_MAIL_HOST (en WIKI_MAIL_USERNAME/WIKI_MAIL_PASSWORD) voor productie.");
			return new LogResetMailSender();
		}
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(mail.host());
		sender.setPort(mail.port());
		boolean auth = mail.username() != null && !mail.username().isBlank();
		if (auth) {
			sender.setUsername(mail.username());
			sender.setPassword(mail.password());
		}
		Properties props = sender.getJavaMailProperties();
		props.put("mail.smtp.auth", String.valueOf(auth));
		props.put("mail.smtp.starttls.enable", String.valueOf(mail.starttls()));
		props.put("mail.smtp.connectiontimeout", "10000");
		props.put("mail.smtp.timeout", "10000");
		props.put("mail.smtp.writetimeout", "10000");
		return new SmtpResetMailSender(sender, mail.from());
	}

}
