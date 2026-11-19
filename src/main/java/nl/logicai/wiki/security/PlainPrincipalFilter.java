package nl.logicai.wiki.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * On the OAuth endpoints, replaces the logged-in user's authentication by a plain one (username and role)
 * for the duration of the request. The authorization server stores the authentication with every
 * connection it grants; without this an Entra login would put the user's ID token and claims in the
 * database (spec N-04) and the two kinds of login would be stored differently. The session itself is not
 * changed: the replacement is never saved.
 */
public class PlainPrincipalFilter extends OncePerRequestFilter {

	/** The one OAuth endpoint a person visits; on the others the authenticated party is the client app. */
	public static final String USER_ENDPOINT = "/oauth2/authorize";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !isUserEndpoint(request);
	}

	public static boolean isUserEndpoint(HttpServletRequest request) {
		return USER_ENDPOINT.equals(request.getRequestURI().substring(request.getContextPath().length()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)) {
			var authorities = authentication.getAuthorities();
			var plain = UsernamePasswordAuthenticationToken.authenticated(
				new User(authentication.getName(), "", authorities), null, authorities);
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(plain);
			SecurityContextHolder.setContext(context);
		}
		chain.doFilter(request, response);
	}

}
