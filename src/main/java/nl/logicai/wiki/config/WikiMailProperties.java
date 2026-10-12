package nl.logicai.wiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMTP settings for the reset mail ({@code wiki.mail.*}, filled from {@code WIKI_MAIL_*}). Kept apart
 * from {@code spring.mail.*} on purpose: an empty {@code WIKI_MAIL_HOST} must mean "no SMTP", while
 * Spring Boot's auto-configuration would treat an empty host as configured and register a mail sender
 * plus a health indicator that can never connect.
 */
@ConfigurationProperties(prefix = "wiki.mail")
public record WikiMailProperties(String host, int port, String username, String password, boolean starttls, String from) {

	public boolean configured() {
		return host != null && !host.isBlank();
	}

}
