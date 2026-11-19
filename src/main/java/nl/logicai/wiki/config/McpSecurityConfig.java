package nl.logicai.wiki.config;

import java.time.Clock;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import nl.logicai.wiki.security.ActiveUserFilter;
import nl.logicai.wiki.security.McpClientRegistrationPolicy;
import nl.logicai.wiki.security.McpPublicClientRefresh;
import nl.logicai.wiki.security.McpSigningKey;
import nl.logicai.wiki.security.PlainPrincipalFilter;
import nl.logicai.wiki.security.WikiJwtAuthenticationConverter;
import nl.logicai.wiki.services.McpConnectionService;
import org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer;
import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.McpDefaultJwtCustomizer;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.mcp.token.ResourceIdentifierAudienceTokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Security for the MCP server (docs/mcp.md), only present when {@code wiki.mcp.enabled} is true. It adds two
 * filter chains in front of the existing one in {@link SecurityConfig}:
 * <ol>
 *   <li>the OAuth 2.1 authorization server ({@code /oauth2/**} and its metadata): the wiki itself. A user
 *       who connects Claude or ChatGPT is sent here, logs in with the normal login page and gives consent;
 *       the app then gets tokens that act for that user. Session-based, like the rest of the site.</li>
 *   <li>the MCP endpoint ({@code /mcp}): stateless, no session and no CSRF token, only a bearer token that
 *       {@link WikiJwtAuthenticationConverter} checks against {@code app_user} on every request (spec F-02).</li>
 * </ol>
 * Everything else still goes through the existing chain, unchanged. The issuer and the URLs in the metadata
 * come from {@code wiki.base-url}, never from request headers (spec N-02). Tokens are JWTs signed with
 * {@link McpSigningKey} and verified locally with the same key, so the app never calls itself over HTTP.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpSecurityConfig {

	public static final String CONSENT_PAGE = "/oauth2/consent";

	private final String baseUrl;
	private final String issuer;

	public McpSecurityConfig(McpProperties properties, @Value("${wiki.base-url}") String baseUrl) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String configured = properties.issuer() == null ? "" : properties.issuer().strip();
		this.issuer = configured.isEmpty() ? this.baseUrl
			: configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
	}

	@Bean
	@Order(1)
	SecurityFilterChain mcpAuthorizationServerFilterChain(HttpSecurity http, WikiUserRepository users,
			McpClientRegistrationPolicy policy, RegisteredClientRepository clients) throws Exception {
		var openIdConfiguration = PathPatternRequestMatcher.withDefaults().matcher("/.well-known/openid-configuration");
		http
			// Spec F-02: a deactivated user cannot connect an agent with a session that still exists. Only where
			// a person comes by: on the token endpoint the authenticated party is the client app, not a wiki user.
			.addFilterBefore(new ActiveUserFilter(users) {
				@Override
				protected boolean shouldNotFilter(HttpServletRequest request) {
					return !PlainPrincipalFilter.isUserEndpoint(request);
				}
			}, AuthorizationFilter.class)
			.addFilterBefore(new PlainPrincipalFilter(), AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
			.with(McpAuthorizationServerConfigurer.mcpAuthorizationServer(), mcp -> mcp
				.dynamicClientRegistrationValidator(policy.registrationValidator())
				.authorizationCodeRequestValidator(policy.authorizationRequestValidator())
				.authorizationServer(server -> {
					// Only the OAuth endpoints; clients probe the OpenID path too and must get 404, not a login page.
					http.securityMatcher(new OrRequestMatcher(server.getEndpointsMatcher(), openIdConfiguration));
					server.authorizationEndpoint(endpoint -> endpoint
						.consentPage(CONSENT_PAGE)
						.authorizationRequestConverters(converters -> converters.replaceAll(McpSecurityConfig::withWikiScope)));
					// A public client refreshes with its client_id and the refresh token (McpPublicClientRefresh).
					server.clientAuthentication(client -> client
						.authenticationConverter(new McpPublicClientRefresh.Converter())
						.authenticationProvider(new McpPublicClientRefresh.Provider(clients)));
					// Claude and ChatGPT are public clients (PKCE, no secret) and look for "none" before they register.
					server.authorizationServerMetadataEndpoint(endpoint -> endpoint.authorizationServerMetadataCustomizer(
						metadata -> metadata.tokenEndpointAuthenticationMethod("none").scope(McpClientRegistrationPolicy.SCOPE)));
					server.clientRegistrationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
						for (var provider : providers) {
							if (provider instanceof OAuth2ClientRegistrationAuthenticationProvider registration) {
								registration.setRegisteredClientConverter(policy.registeredClientConverter());
							}
						}
					}));
				}))
			// The user logs in on the normal login page and comes back to the authorization request.
			.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
				PathPatternRequestMatcher.withDefaults().matcher("/oauth2/authorize")));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain mcpEndpointFilterChain(HttpSecurity http, JwtDecoder mcpJwtDecoder,
			WikiJwtAuthenticationConverter converter) throws Exception {
		http
			.securityMatcher("/mcp", "/mcp/**", "/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**")
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/.well-known/**").permitAll()
				.anyRequest().authenticated())
			// A bearer token is not sent by a browser on its own, so there is nothing for CSRF to protect here.
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(cache -> cache.disable())
			.with(McpServerOAuth2Configurer.mcpServerOAuth2(), mcp -> mcp
				.authorizationServer(issuer)
				.jwtDecoder(mcpJwtDecoder)
				.resourcePath("/mcp")
				// Claude compares "resource" with the URL the user typed; it is the public URL, not what a proxy forwarded.
				.protectedResourceMetadataCustomizer(metadata -> metadata
					.resource(baseUrl + "/mcp")
					.authorizationServer(issuer)
					.scope(McpClientRegistrationPolicy.SCOPE)
					.resourceName("LogicAI Wiki"))
				.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))));
		return http.build();
	}

	/**
	 * Every authorization request is for the one scope the wiki knows. Clients ask for none, or for names of
	 * their own ("mcp", "offline_access"); without a scope there would be nothing to ask consent for.
	 */
	private static AuthenticationConverter withWikiScope(AuthenticationConverter converter) {
		return request -> {
			Authentication authentication = converter.convert(request);
			if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token
					&& token.getAuthorizationCode() == null) {
				var rewritten = new OAuth2AuthorizationCodeRequestAuthenticationToken(token.getAuthorizationUri(),
					token.getClientId(), (Authentication) token.getPrincipal(), token.getRedirectUri(), token.getState(),
					java.util.Set.of(McpClientRegistrationPolicy.SCOPE), token.getAdditionalParameters());
				rewritten.setDetails(token.getDetails());
				return rewritten;
			}
			return authentication;
		};
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder().issuer(issuer).build();
	}

	@Bean
	RSAKey mcpSigningKey(McpProperties properties) {
		return McpSigningKey.load(properties.signingKey());
	}

	@Bean
	JWKSource<SecurityContext> jwkSource(RSAKey mcpSigningKey) {
		return new ImmutableJWKSet<>(new JWKSet(mcpSigningKey));
	}

	@Bean
	JwtDecoder mcpJwtDecoder(RSAKey mcpSigningKey) throws Exception {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(mcpSigningKey.toRSAPublicKey()).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		return decoder;
	}

	@Bean
	WikiJwtAuthenticationConverter wikiJwtAuthenticationConverter(WikiUserRepository users, JdbcOperations jdbc) {
		return new WikiJwtAuthenticationConverter(users, jdbc);
	}

	@Bean
	McpClientRegistrationPolicy mcpClientRegistrationPolicy(McpProperties properties, JdbcOperations jdbc, Clock clock) {
		return new McpClientRegistrationPolicy(properties, jdbc, clock);
	}

	@Bean
	RegisteredClientRepository registeredClientRepository(JdbcOperations jdbc) {
		return new JdbcRegisteredClientRepository(jdbc);
	}

	@Bean
	OAuth2AuthorizationService authorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
		return new JdbcOAuth2AuthorizationService(jdbc, clients);
	}

	@Bean
	OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbc, RegisteredClientRepository clients) {
		return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
	}

	/**
	 * Access tokens are JWTs signed with the wiki's key; refresh tokens are opaque and also go to public
	 * clients ({@link McpPublicClientRefresh}). The JWT gets Spring's standard claims, the MCP resource as
	 * audience, and the wiki's own claims below.
	 */
	@Bean
	OAuth2TokenGenerator<?> mcpTokenGenerator(JWKSource<SecurityContext> jwkSource,
			OAuth2TokenCustomizer<JwtEncodingContext> mcpTokenCustomizer, Clock clock) {
		JwtGenerator jwt = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
		var audience = new ResourceIdentifierAudienceTokenCustomizer();
		jwt.setJwtCustomizer(context -> {
			McpDefaultJwtCustomizer.DEFAULT_JWT_CUSTOMIZER.customize(context);
			audience.customize(context);
			mcpTokenCustomizer.customize(context);
		});
		return new DelegatingOAuth2TokenGenerator(jwt, new McpPublicClientRefresh.Generator(clock));
	}

	/**
	 * What goes into an access token, at login and at every refresh: the wiki username as subject, the id of
	 * the connection (so it can be revoked) and the name of the app (for the audit trail). No role: that is
	 * read from app_user per request. A user who is no longer active gets no (new) token.
	 */
	@Bean
	OAuth2TokenCustomizer<JwtEncodingContext> mcpTokenCustomizer(WikiUserRepository users, AuditEventRepository audit,
			McpConnectionService connections, Clock clock) {
		return context -> {
			if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
				return;
			}
			WikiUser user = users.findByUsernameIgnoreCase(context.getPrincipal().getName())
				.filter(WikiUser::isActive)
				.orElseThrow(() -> new OAuth2AuthenticationException(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "The user is no longer active.", null)));
			String clientName = context.getRegisteredClient().getClientName();
			context.getClaims().subject(user.getUsername());
			context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
			context.getClaims().claim(WikiJwtAuthenticationConverter.CLIENT_NAME_CLAIM, clientName);
			if (context.getAuthorization() != null) {
				context.getClaims().claim(WikiJwtAuthenticationConverter.AUTHORIZATION_ID_CLAIM, context.getAuthorization().getId());
			}
			if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())) {
				audit.save(AuditEvent.of(AuditEvent.MCP_CONNECT, null, user.getUsername(), clock.instant(), clientName));
				if (context.getAuthorization() != null) {
					connections.onConnected(user.getUsername(), context.getAuthorization().getId());
				}
			}
		};
	}

}
