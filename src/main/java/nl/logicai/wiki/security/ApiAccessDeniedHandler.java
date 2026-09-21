package nl.logicai.wiki.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * JSON answers for refused editor requests on {@code /api/**} (spec N-02, section 8). CSRF stays
 * enforced for fetch requests; this handler only makes the refusal readable for the editor, which
 * keeps its content in every case:
 * <ul>
 *   <li>401 when the request has no logged-in session. A missing CSRF token is the first thing
 *       Spring Security notices after a session expired, so without this the editor would see a 403.</li>
 *   <li>403 with a Dutch message when the token is missing or stale (reload the page), or when the
 *       role does not allow the change.</li>
 * </ul>
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	static final String SESSION_EXPIRED = "Je sessie is verlopen. Log opnieuw in.";
	static final String CSRF_INVALID = "Het beveiligingstoken van deze pagina is ongeldig. Herlaad de pagina en probeer het opnieuw.";
	static final String NO_PERMISSION = "Je hebt geen rechten voor deze bewerking.";

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
		if (!loggedIn) {
			write(response, HttpServletResponse.SC_UNAUTHORIZED, SESSION_EXPIRED);
		}
		else if (exception instanceof CsrfException) {
			write(response, HttpServletResponse.SC_FORBIDDEN, CSRF_INVALID);
		}
		else {
			write(response, HttpServletResponse.SC_FORBIDDEN, NO_PERMISSION);
		}
	}

	private static void write(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		// The messages are fixed strings without quotes or backslashes, so no JSON escaping is needed.
		response.getWriter().write("{\"error\":\"" + message + "\"}");
	}

}
