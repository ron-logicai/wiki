package nl.logicai.wiki.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.logicai.wiki.services.ApiKeyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request on /api/v1 by its API key ({@code Authorization: Bearer wiki_...}, docs/api.md),
 * the only way in on that chain. The user is read from {@code app_user} on every request
 * ({@link ApiKeyService#authenticate}), so a deactivated user, a changed role and a revoked key apply at
 * once (spec F-02). The principal name is the username, as everywhere else. A request without a valid key
 * stays unauthenticated and gets the 401 of {@link #entryPoint()}.
 *
 * <p>Not a bean on purpose: as a bean Spring Boot would also run it for every other request.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	/** Which key a request came in with; the name is the one the user gave it, for the audit log. */
	public record ApiKeyDetails(UUID keyId, String keyName) {
	}

	static final String NO_KEY = "Geen geldige API key. Stuur de key mee als Authorization: Bearer <key>.";
	static final String NO_PERMISSION = "Je rol staat deze bewerking niet toe. Pagina's wijzigen vraagt de rol Editor.";

	private static final String SCHEME = "Bearer ";

	private final ApiKeyService keys;

	public ApiKeyAuthenticationFilter(ApiKeyService keys) {
		this.keys = keys;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
			keys.authenticate(header.substring(SCHEME.length()).strip()).ifPresent(found -> {
				var authentication = UsernamePasswordAuthenticationToken.authenticated(found.user().getUsername(), null,
					List.of(new SimpleGrantedAuthority(found.user().getRole().authority())));
				authentication.setDetails(new ApiKeyDetails(found.key().getId(), found.key().getName()));
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			});
		}
		chain.doFilter(request, response);
	}

	/** 401 with a JSON body for a request without a (valid) key. */
	public static AuthenticationEntryPoint entryPoint() {
		return (request, response, exception) -> {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", NO_KEY);
		};
	}

	/** 403 with a JSON body when the role of the key's user does not allow the change. */
	public static AccessDeniedHandler accessDeniedHandler() {
		return (request, response, exception) -> write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", NO_PERMISSION);
	}

	private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		// The messages are fixed strings without quotes or backslashes, so no JSON escaping is needed.
		response.getWriter().write("{\"error\":\"" + message + "\",\"code\":\"" + code + "\"}");
	}

}
