package nl.logicai.wiki.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mail delivery for "wachtwoord vergeten" and invitations ({@code wiki.mail.*}, filled from {@code WIKI_MAIL_*} and
 * {@code WIKI_ENTRA_*}). Two ways to send: as an application through Microsoft Graph ({@link Graph}, no mailbox
 * password, the way for Microsoft 365) or through SMTP. Kept apart from {@code spring.mail.*} on purpose: an empty
 * {@code WIKI_MAIL_HOST} must mean "no SMTP", while Spring Boot's auto-configuration would treat an empty host as
 * configured and register a mail sender plus a health indicator that can never connect. {@code toString} hides the
 * secrets, so the record can never leak them into a log (spec N-04).
 */
@ConfigurationProperties(prefix = "wiki.mail")
public record WikiMailProperties(String host, int port, String username, String password, boolean starttls, String from,
		Graph graph) {

	/** True when mail can be sent at all (Graph or SMTP); otherwise the links are only logged. */
	public boolean configured() {
		return graphConfigured() || smtpConfigured();
	}

	public boolean smtpConfigured() {
		return !blank(host);
	}

	public boolean graphConfigured() {
		return graph != null && graph.configured();
	}

	@Override
	public String toString() {
		return "WikiMailProperties[host=" + host + ", port=" + port + ", username=" + username + ", password=<verborgen>"
				+ ", starttls=" + starttls + ", from=" + from + ", graph=" + graph + "]";
	}

	static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Sending as an application through Microsoft Graph: client credentials of the Entra app registration with the
	 * application permission {@code Mail.Send}, from the mailbox {@code sender} ({@code WIKI_MAIL_GRAPH_SENDER}).
	 * Active when {@code sender} is set; takes precedence over SMTP. {@code loginBase} and {@code apiBase} exist so
	 * the tests can point at a fake Entra and Graph on localhost.
	 */
	public record Graph(String sender, String tenantId, String clientId, String clientSecret, String loginBase, String apiBase) {

		public boolean configured() {
			return !blank(sender);
		}

		/** The environment variables still missing for a configured sender, for a clear start-up error. */
		public List<String> missing() {
			List<String> missing = new ArrayList<>();
			if (blank(tenantId)) {
				missing.add("WIKI_ENTRA_TENANT_ID");
			}
			if (blank(clientId)) {
				missing.add("WIKI_ENTRA_CLIENT_ID");
			}
			if (blank(clientSecret)) {
				missing.add("WIKI_ENTRA_CLIENT_SECRET");
			}
			return missing;
		}

		@Override
		public String toString() {
			return "Graph[sender=" + sender + ", tenantId=" + tenantId + ", clientId=" + clientId
					+ ", clientSecret=<verborgen>, loginBase=" + loginBase + ", apiBase=" + apiBase + "]";
		}

	}

}
