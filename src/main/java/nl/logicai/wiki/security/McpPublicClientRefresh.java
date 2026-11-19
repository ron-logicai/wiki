package nl.logicai.wiki.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Refresh tokens for public clients. Claude, ChatGPT and Claude Code are public OAuth clients: they have no
 * client secret and prove themselves with PKCE. Spring's authorization server gives such clients no refresh
 * token and does not let them use one, so a user would have to reconnect every time the access token
 * expires (half an hour). OAuth 2.1 allows refresh tokens for public clients when each one can be used only
 * once; that is what these three small pieces add:
 * <ul>
 *   <li>{@link Generator}: also issues a refresh token when the client is public;</li>
 *   <li>{@link Converter} and {@link Provider}: let a public client present a refresh token with just its
 *       {@code client_id}. The refresh token itself is the credential: Spring then checks that it belongs to
 *       this client and is still valid.</li>
 * </ul>
 * Rotation is switched on per client ({@code reuseRefreshTokens(false)} in {@link McpClientRegistrationPolicy}):
 * every refresh returns a new refresh token and the used one stops working, so a copied token is useless as
 * soon as the real client refreshes, and the reverse shows up as the real client being logged out.
 */
public final class McpPublicClientRefresh {

	private McpPublicClientRefresh() {
	}

	/** Reads a refresh request without client credentials as "public client {@code client_id}". */
	public static final class Converter implements AuthenticationConverter {

		@Override
		public Authentication convert(HttpServletRequest request) {
			if (!"POST".equals(request.getMethod())
					|| !AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
					|| request.getHeader("Authorization") != null
					|| request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) {
				return null;
			}
			String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
			if (clientIds == null || clientIds.length != 1 || clientIds[0].isBlank()) {
				return null;
			}
			return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null,
				Map.of(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue()));
		}

	}

	/** Accepts that request when the client exists and really is a public client. */
	public static final class Provider implements AuthenticationProvider {

		private final RegisteredClientRepository clients;

		public Provider(RegisteredClientRepository clients) {
			this.clients = clients;
		}

		@Override
		public Authentication authenticate(Authentication authentication) {
			OAuth2ClientAuthenticationToken request = (OAuth2ClientAuthenticationToken) authentication;
			if (!ClientAuthenticationMethod.NONE.equals(request.getClientAuthenticationMethod())
					|| !AuthorizationGrantType.REFRESH_TOKEN.getValue()
						.equals(request.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE))) {
				// Not a refresh by a public client: the standard providers decide (PKCE for the code exchange).
				return null;
			}
			RegisteredClient client = clients.findByClientId(request.getPrincipal().toString());
			if (client == null || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
				throw new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Client authentication failed", null));
			}
			return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
		}

		@Override
		public boolean supports(Class<?> authentication) {
			return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
		}

	}

	/** Spring's refresh token generator without its exception for public clients. */
	public static final class Generator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

		private final StringKeyGenerator keys = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);
		private final Clock clock;

		public Generator(Clock clock) {
			this.clock = clock;
		}

		@Override
		public OAuth2RefreshToken generate(OAuth2TokenContext context) {
			if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
				return null;
			}
			Instant issuedAt = clock.instant();
			Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
			return new OAuth2RefreshToken(keys.generateKey(), issuedAt, expiresAt);
		}

	}

}
