package nl.logicai.wiki.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import nl.logicai.wiki.mail.GraphMailSender;
import nl.logicai.wiki.mail.LogMailSender;
import nl.logicai.wiki.mail.SmtpMailSender;
import nl.logicai.wiki.mail.WikiMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

/**
 * Picks the mail delivery for "wachtwoord vergeten" and invitations: Microsoft Graph when {@code WIKI_MAIL_GRAPH_SENDER}
 * is set (no mailbox password), otherwise SMTP when {@code WIKI_MAIL_HOST} is set, otherwise the log fallback. The
 * senders are built here instead of through {@code spring.mail.*}, see {@link WikiMailProperties}.
 */
@Configuration
public class MailConfig {

	private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Bean
	WikiMailSender wikiMailSender(WikiMailProperties mail, Clock clock) {
		if (mail.graphConfigured()) {
			WikiMailProperties.Graph graph = mail.graph();
			List<String> missing = graph.missing();
			if (!missing.isEmpty()) {
				throw new IllegalStateException("WIKI_MAIL_GRAPH_SENDER vereist " + String.join(", ", missing)
						+ ": de wiki mailt via Microsoft Graph met de Entra-appregistratie.");
			}
			log.info("Mail via Microsoft Graph als {}", graph.sender());
			return new GraphMailSender(restClientBuilder(), graph, clock);
		}
		if (!mail.smtpConfigured()) {
			log.warn("WIKI_MAIL_GRAPH_SENDER en WIKI_MAIL_HOST zijn leeg: herstel-links en uitnodigingen worden gelogd, "
					+ "niet gemaild. Zet WIKI_MAIL_GRAPH_SENDER (Microsoft 365) of WIKI_MAIL_HOST (SMTP) voor productie.");
			return new LogMailSender();
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
		// With STARTTLS on, never fall back to a plain connection: the password must not travel unencrypted.
		props.put("mail.smtp.starttls.required", String.valueOf(mail.starttls()));
		props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT.toMillis()));
		props.put("mail.smtp.timeout", String.valueOf(TIMEOUT.toMillis()));
		props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT.toMillis()));
		return new SmtpMailSender(sender, mail.from());
	}

	/** Same timeouts as SMTP: a hanging Entra or Graph must not hang the form of the admin. */
	private static RestClient.Builder restClientBuilder() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
		factory.setReadTimeout(TIMEOUT);
		return RestClient.builder().requestFactory(factory);
	}

}
