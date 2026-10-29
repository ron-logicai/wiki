package nl.logicai.wiki.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.logicai.wiki.config.WikiMailProperties;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends the reset link and the invitation through the Microsoft Graph API as the application itself: client
 * credentials of the Entra app registration (application permission {@code Mail.Send}), from the mailbox in
 * {@code WIKI_MAIL_GRAPH_SENDER}. No mailbox password anywhere, and it keeps working after Microsoft switches off
 * password login on SMTP (MC786329, December 2026). An Exchange application access policy should limit the app to
 * that one mailbox; see the README. Every failure becomes a {@link MailSendException} carrying the reason given by
 * Entra or Graph, so {@code InvitationService} and {@code PasswordResetService} handle it exactly like a refusing
 * SMTP server. The body is the HTML from {@link MailTemplates}; Graph has no text+HTML alternative.
 */
public class GraphMailSender implements WikiMailSender {

	/** A token is renewed this long before Entra says it expires, so no mail goes out on the last second of a token. */
	static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

	private static final String SCOPE = "https://graph.microsoft.com/.default";

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	private final RestClient login;
	private final RestClient graph;
	private final WikiMailProperties.Graph settings;
	private final Clock clock;
	private final MailTemplates templates;

	private volatile Token token;

	private record Token(String value, Instant expiresAt) {
	}

	public GraphMailSender(RestClient.Builder builder, WikiMailProperties.Graph settings, Clock clock, MailTemplates templates) {
		this.login = builder.clone().baseUrl(withoutSlash(settings.loginBase())).build();
		this.graph = builder.clone().baseUrl(withoutSlash(settings.apiBase())).build();
		this.settings = settings;
		this.clock = clock;
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
		Map<String, Object> body = Map.of(
				"message", Map.of(
						"subject", mail.subject(),
						"body", Map.of("contentType", "HTML", "content", mail.html()),
						"toRecipients", List.of(Map.of("emailAddress", Map.of("address", to)))),
				"saveToSentItems", false);
		try {
			try {
				post(body, accessToken(false));
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() != 401) {
					throw ex;
				}
				// Token revoked or expired early: one retry with a fresh token.
				post(body, accessToken(true));
			}
		}
		catch (RestClientResponseException ex) {
			throw new MailSendException("Microsoft Graph weigerde de mail (" + ex.getStatusCode().value() + "): "
					+ reasonOf(ex.getResponseBodyAsString()));
		}
		catch (RestClientException ex) {
			throw new MailSendException("Microsoft Graph is niet bereikbaar: " + ex.getMessage(), ex);
		}
	}

	private void post(Map<String, Object> body, String bearer) {
		graph.post()
			.uri("/users/{sender}/sendMail", settings.sender())
			.headers(headers -> headers.setBearerAuth(bearer))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.toBodilessEntity();
	}

	/** The cached token while it is good for at least {@link #REFRESH_MARGIN}, otherwise a new one from Entra. */
	private synchronized String accessToken(boolean fresh) {
		Token current = token;
		if (!fresh && current != null && clock.instant().isBefore(current.expiresAt().minus(REFRESH_MARGIN))) {
			return current.value();
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", settings.clientId());
		form.add("client_secret", settings.clientSecret());
		form.add("scope", SCOPE);
		try {
			Map<String, Object> response = login.post()
				.uri("/{tenant}/oauth2/v2.0/token", settings.tenantId())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(JSON_OBJECT);
			String value = response == null ? null : (String) response.get("access_token");
			if (value == null || value.isBlank()) {
				throw new MailSendException("Entra gaf geen access_token terug.");
			}
			long expiresIn = response.get("expires_in") instanceof Number seconds ? seconds.longValue() : 3600;
			token = new Token(value, clock.instant().plusSeconds(expiresIn));
			return value;
		}
		catch (RestClientResponseException ex) {
			throw new MailSendException("Entra gaf geen token voor Microsoft Graph (" + ex.getStatusCode().value() + "): "
					+ reasonOf(ex.getResponseBodyAsString()));
		}
		catch (RestClientException ex) {
			throw new MailSendException("Entra is niet bereikbaar: " + ex.getMessage(), ex);
		}
	}

	/**
	 * The code and message out of an error body, without depending on a JSON library: Graph answers
	 * {@code {"error":{"code":..,"message":..}}}, Entra {@code {"error":..,"error_description":..}}.
	 */
	static String reasonOf(String body) {
		if (body == null || body.isBlank()) {
			return "geen foutmelding.";
		}
		String code = field(body, "code");
		String message = field(body, "message");
		if (code == null) {
			code = field(body, "error");
			message = field(body, "error_description");
		}
		if (code == null) {
			return body.length() > 300 ? body.substring(0, 300) : body;
		}
		String reason = message == null ? code : code + ": " + message;
		return reason.endsWith(".") ? reason : reason + ".";
	}

	private static String field(String json, String name) {
		Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
		return matcher.find() ? matcher.group(1).replace("\\r\\n", " ").replace("\\n", " ").replace("\\\"", "\"") : null;
	}

	private static String withoutSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
