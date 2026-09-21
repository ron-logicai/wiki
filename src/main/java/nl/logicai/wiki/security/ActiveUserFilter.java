package nl.logicai.wiki.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spec F-02: a deactivated user is refused on the next request, also with an existing session, and a
 * role change applies on the next request. Runs before authorization; one indexed query per request.
 * Principals that are not in app_user (for example mock users in tests) pass through unchanged.
 */
public class ActiveUserFilter extends OncePerRequestFilter {

	private static final List<String> SKIP = List.of("/css/", "/editor/", "/actuator/", "/login", "/logout", "/error");

	private final WikiUserRepository users;

	public ActiveUserFilter(WikiUserRepository users) {
		this.users = users;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return SKIP.stream().anyMatch(path::startsWith);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			chain.doFilter(request, response);
			return;
		}
		Optional<WikiUser> found = users.findByUsernameIgnoreCase(auth.getName());
		if (found.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		WikiUser user = found.get();
		if (!user.isActive()) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			SecurityContextHolder.clearContext();
			String path = request.getRequestURI().substring(request.getContextPath().length());
			if (path.startsWith("/api/")) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			}
			else {
				response.sendRedirect(request.getContextPath() + "/login?deactivated");
			}
			return;
		}
		Set<String> roles = auth.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.filter(a -> a.startsWith("ROLE_"))
			.collect(Collectors.toSet());
		if (!roles.equals(Set.of(user.getRole().authority()))) {
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(withRole(auth, user));
			SecurityContextHolder.setContext(context);
		}
		chain.doFilter(request, response);
	}

	private static Authentication withRole(Authentication auth, WikiUser user) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		for (GrantedAuthority authority : auth.getAuthorities()) {
			if (!authority.getAuthority().startsWith("ROLE_")) {
				authorities.add(authority);
			}
		}
		authorities.add(new SimpleGrantedAuthority(user.getRole().authority()));
		if (auth instanceof OAuth2AuthenticationToken oauth) {
			return new OAuth2AuthenticationToken(oauth.getPrincipal(), authorities, oauth.getAuthorizedClientRegistrationId());
		}
		UsernamePasswordAuthenticationToken renewed =
			UsernamePasswordAuthenticationToken.authenticated(auth.getPrincipal(), auth.getCredentials(), authorities);
		renewed.setDetails(auth.getDetails());
		return renewed;
	}

}
