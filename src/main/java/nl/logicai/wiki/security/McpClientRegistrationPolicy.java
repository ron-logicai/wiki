package nl.logicai.wiki.security;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import jakarta.servlet.http.HttpServletRequest;
import nl.logicai.wiki.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2ClientRegistration;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The limits on dynamic client registration. Claude and ChatGPT register themselves as an OAuth client
 * without anyone logging in first (that is how MCP connectors work), so {@code POST /oauth2/register} is
 * open to the internet. Registering gives no access to anything: a client is only useful after a wiki user
 * logs in and gives consent. These rules keep the open endpoint from being abused:
 * <ul>
 *   <li>a client may only send users back to an allowlisted redirect URI ({@code wiki.mcp.allowed-redirect-uris});
 *       a loopback address matches on any port, an entry ending in {@code /*} matches the paths below it,
 *       everything else exactly. So nobody can register a client that
 *       delivers an authorization code to their own site;</li>
 *   <li>every client gets the single scope {@code wiki}, must use PKCE and always needs the user's consent,
 *       whatever it asked for;</li>
 *   <li>at most {@code wiki.mcp.max-clients} clients exist, and a few registrations per address per hour;
 *       clients that were registered but never connected by anyone are removed after a day.</li>
 * </ul>
 */
public class McpClientRegistrationPolicy {

	public static final String SCOPE = "wiki";

	private static final Logger log = LoggerFactory.getLogger(McpClientRegistrationPolicy.class);

	private static final Duration UNUSED_CLIENT_TTL = Duration.ofHours(24);
	private static final int MAX_NAME_LENGTH = 100;
	private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

	private final McpProperties properties;
	private final JdbcOperations jdbc;
	private final Clock clock;
	private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

	public McpClientRegistrationPolicy(McpProperties properties, JdbcOperations jdbc, Clock clock) {
		this.properties = properties;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Checks a registration request before a client is stored. */
	public Consumer<OAuth2ClientRegistrationAuthenticationContext> registrationValidator() {
		// The standard checks on the URIs, without the standard scope check: that one refuses every registration
		// that names a scope, which Claude and ChatGPT do. What they ask for is ignored anyway (see the converter).
		Consumer<OAuth2ClientRegistrationAuthenticationContext> standard = OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
			.andThen(OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_JWK_SET_URI_VALIDATOR);
		return standard.andThen(context -> {
			OAuth2ClientRegistrationAuthenticationToken request = context.getAuthentication();
			List<String> redirectUris = request.getClientRegistration().getRedirectUris();
			if (redirectUris == null || redirectUris.isEmpty()) {
				throw refused("invalid_redirect_uri", "At least one redirect URI is required.");
			}
			for (String redirectUri : redirectUris) {
				if (!isAllowedRedirectUri(redirectUri)) {
					log.info("MCP client registration refused: redirect URI {} is not on the allowlist", redirectUri);
					throw refused("invalid_redirect_uri", "This redirect URI is not allowed by this wiki. "
						+ "An administrator can add it to wiki.mcp.allowed-redirect-uris.");
				}
			}
			throttle();
		});
	}

	/** What a registration becomes, whatever the client asked for. */
	public Converter<OAuth2ClientRegistration, RegisteredClient> registeredClientConverter() {
		var standard = new OAuth2ClientRegistrationRegisteredClientConverter();
		standard.setTokenSettingsCustomizer(tokens -> tokens
			.authorizationCodeTimeToLive(Duration.ofMinutes(5))
			.accessTokenTimeToLive(properties.accessTokenTtl())
			.refreshTokenTimeToLive(properties.refreshTokenTtl())
			// A used refresh token is replaced, so a stolen one stops working as soon as the real client refreshes.
			.reuseRefreshTokens(false));
		return registration -> {
			RegisteredClient client = standard.convert(registration);
			String name = client.getClientName() == null ? "" : client.getClientName().strip().replaceAll("\\s+", " ");
			if (name.isEmpty() || name.equals(client.getId())) {
				name = "Onbekende app";
			}
			return RegisteredClient.from(client)
				.clientName(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name)
				.scopes(scopes -> {
					scopes.clear();
					scopes.add(SCOPE);
				})
				.authorizationGrantTypes(grants -> {
					grants.clear();
					grants.add(AuthorizationGrantType.AUTHORIZATION_CODE);
					grants.add(AuthorizationGrantType.REFRESH_TOKEN);
				})
				.clientSettings(ClientSettings.withSettings(client.getClientSettings().getSettings())
					.requireProofKey(true)
					.requireAuthorizationConsent(true)
					.build())
				.build();
		};
	}

	/**
	 * Checks the redirect URI of an authorization request. A loopback redirect matches the registered one on
	 * any port (RFC 8252: a desktop app such as Claude Code listens on a port it picks at that moment).
	 */
	public Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> authorizationRequestValidator() {
		return context -> {
			OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
			String requested = request.getRedirectUri();
			boolean loopbackMatch = requested != null && isLoopback(requested) && context.getRegisteredClient()
				.getRedirectUris().stream().anyMatch(registered -> sameExceptPort(requested, registered));
			if (!loopbackMatch) {
				OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR.accept(context);
			}
			OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR.accept(context);
		};
	}

	boolean isAllowedRedirectUri(String redirectUri) {
		if (redirectUri == null) {
			return false;
		}
		List<String> allowed = properties.allowedRedirectUris() == null ? List.of() : properties.allowedRedirectUris();
		if (isLoopback(redirectUri)) {
			String host = URI.create(redirectUri).getHost().toLowerCase(Locale.ROOT);
			return allowed.stream().anyMatch(entry -> isLoopback(entry)
				&& URI.create(entry).getHost().toLowerCase(Locale.ROOT).equals(host));
		}
		return allowed.stream().anyMatch(entry -> entry.endsWith("/*")
			? isBelow(redirectUri, entry.substring(0, entry.length() - 1))
			: entry.equals(redirectUri));
	}

	/** {@code prefix} ends in a slash, so the scheme and host are fixed; no tricks with dots, queries or fragments. */
	private static boolean isBelow(String redirectUri, String prefix) {
		return redirectUri.startsWith(prefix) && redirectUri.length() > prefix.length()
			&& redirectUri.substring(prefix.length()).matches("[A-Za-z0-9_\\-~]+(/[A-Za-z0-9_\\-~]+)*");
	}

	/** {@code http} on localhost, 127.0.0.1 or [::1], without user info. */
	private static boolean isLoopback(String uri) {
		try {
			URI parsed = URI.create(uri);
			return "http".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null
				&& LOOPBACK_HOSTS.contains(parsed.getHost().toLowerCase(Locale.ROOT)) && parsed.getUserInfo() == null;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static boolean sameExceptPort(String requested, String registered) {
		try {
			URI a = URI.create(requested);
			URI b = URI.create(registered);
			return isLoopback(registered) && a.getHost().equalsIgnoreCase(b.getHost())
				&& java.util.Objects.equals(emptyToSlash(a.getPath()), emptyToSlash(b.getPath()))
				&& java.util.Objects.equals(a.getQuery(), b.getQuery()) && a.getFragment() == null;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static String emptyToSlash(String path) {
		return path == null || path.isEmpty() ? "/" : path;
	}

	/** Refuses when this address registered too often in the last hour or the wiki holds its maximum of clients. */
	void throttle() {
		Instant now = clock.instant();
		String address = remoteAddress();
		Deque<Instant> times = recent.computeIfAbsent(address, key -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
				times.pollFirst();
			}
			if (times.size() >= properties.maxRegistrationsPerHour()) {
				log.warn("MCP client registration refused: too many registrations from {}", address);
				throw refused("invalid_client_metadata", "Too many client registrations from this address; try again later.");
			}
			times.addLast(now);
		}
		if (recent.size() > 10_000) {
			recent.clear();
		}

		// Clients nobody ever connected are clutter (and the cheapest way to fill the table): drop them after a day.
		jdbc.update("""
			delete from oauth2_registered_client
			where client_id_issued_at < ?
			  and not exists (select 1 from oauth2_authorization a where a.registered_client_id = oauth2_registered_client.id)
			  and not exists (select 1 from oauth2_authorization_consent c where c.registered_client_id = oauth2_registered_client.id)
			""", java.sql.Timestamp.from(now.minus(UNUSED_CLIENT_TTL)));
		Integer clients = jdbc.queryForObject("select count(*) from oauth2_registered_client", Integer.class);
		if (clients != null && clients >= properties.maxClients()) {
			log.warn("MCP client registration refused: the maximum of {} clients is reached", properties.maxClients());
			throw refused("invalid_client_metadata", "This wiki accepts no new client registrations at the moment.");
		}
	}

	private static String remoteAddress() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			HttpServletRequest request = attributes.getRequest();
			return request.getRemoteAddr();
		}
		return "unknown";
	}

	private static OAuth2AuthenticationException refused(String code, String description) {
		return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
	}

}
