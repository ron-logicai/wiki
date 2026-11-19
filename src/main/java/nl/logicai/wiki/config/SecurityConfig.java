package nl.logicai.wiki.config;

import java.net.URI;

import nl.logicai.wiki.repositories.WikiUserRepository;
import nl.logicai.wiki.security.ActiveUserFilter;
import nl.logicai.wiki.security.ApiAccessDeniedHandler;
import nl.logicai.wiki.security.WikiOidcUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Server-side authorization for every route (spec N-01, N-02).
 * <ul>
 *   <li>HTML requests without a session are redirected to the login page.</li>
 *   <li>Editor API requests ({@code /api/**}) get 401 instead of login HTML, so the editor
 *       never mistakes a login page for a successful save.</li>
 *   <li>CSRF stays enabled, also for fetch requests; the token is rendered as a meta tag and the editor
 *       sends it as a header. A refused API request gets a JSON body ({@link ApiAccessDeniedHandler}).</li>
 *   <li>The session cookie is HttpOnly, Secure and SameSite=Lax (application.yaml); Lax is what the
 *       OIDC callback needs, see the comment there.</li>
 *   <li>Company login (Entra ID via OIDC) is enabled automatically when a client registration
 *       is configured (profile {@code entra}); the account must match an active app_user.</li>
 *   <li>Password login is by e-mail address and checks app_user through {@code WikiUserDetailsService};
 *       the principal name stays the internal username. Profile {@code local} seeds the three test
 *       users ({@code admin@wiki.nl} etc.), and {@code WIKI_BOOTSTRAP_ADMIN_*} creates the first admin.</li>
 *   <li>"Wachtwoord vergeten" is open without a session: {@code /wachtwoord-vergeten} mails a single-use
 *       link to {@code /wachtwoord-herstellen} ({@code PasswordResetService}); a logged-in user changes
 *       their own password on {@code /account/wachtwoord}.</li>
 *   <li>New users are invited by an admin: the mailed single-use link to {@code /accountaanmaken}
 *       ({@code InvitationService}) is open without a session but useless without a valid token, so
 *       there is still no public registration (spec F-01).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/** The chain for everything; the MCP chains of {@link McpSecurityConfig}, when enabled, go first for their own paths. */
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	SecurityFilterChain securityFilterChain(HttpSecurity http, WikiUserRepository users,
			ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
		http
			// Spec F-02: deactivation and role changes apply on the next request, also with a session.
			.addFilterBefore(new ActiveUserFilter(users), AuthorizationFilter.class)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/editor/**", "/css/**", "/fonts/**", "/img/**", "/error").permitAll()
				// The login page with every query string (?error, ?logout, ?deactivated); formLogin().permitAll() would
				// only allow the exact URLs /login and /login?error, and send /login?logout back to /login.
				// "Wachtwoord vergeten" (request form), the reset link from the mail and the invitation link
				// (/accountaanmaken, unusable without a valid token, so still no public registration) are open as well.
				.requestMatchers("/login", "/wachtwoord-vergeten", "/wachtwoord-herstellen", "/accountaanmaken").permitAll()
				// /dev/** only exists under profile local (mail preview); keep it admin-only in case of a stray bean.
				.requestMatchers("/admin/**", "/dev/**").hasRole("ADMIN")
				.requestMatchers("/trash/**", "/trash").hasRole("EDITOR")
				.anyRequest().authenticated())
			.formLogin(form -> form.loginPage("/login").successHandler(loginSuccessHandler()))
			.logout(logout -> logout.logoutSuccessUrl("/login?logout"))
			.exceptionHandling(ex -> {
				RequestMatcher api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
				// Pages go to the login form (also without an Accept header); the JSON API gets 401.
				ex.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), new NegatedRequestMatcher(api));
				ex.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api);
				// Also used by the CSRF filter, so a save without a valid token gets JSON instead of an error page.
				// Both mappings are needed: with a single one Spring would apply it to every request.
				ex.defaultAccessDeniedHandlerFor(new AccessDeniedHandlerImpl(), new NegatedRequestMatcher(api));
				ex.defaultAccessDeniedHandlerFor(new ApiAccessDeniedHandler(), api);
			});

		if (clientRegistrations.getIfAvailable() != null) {
			// Only Entra accounts that match an active app_user get in (spec F-01).
			http.oauth2Login(oauth -> oauth.loginPage("/login").userInfoEndpoint(info -> info.oidcUserService(new WikiOidcUserService(users))));
		}
		return http.build();
	}

	/**
	 * After a password login the user lands on the home page, whatever page they asked for before. The one
	 * exception is a user who was connecting an AI agent (docs/mcp.md): that flow starts at
	 * {@code /oauth2/authorize}, sends the user here to log in and must continue there afterwards.
	 */
	private static AuthenticationSuccessHandler loginSuccessHandler() {
		var requests = new HttpSessionRequestCache();
		var backToAuthorization = new SavedRequestAwareAuthenticationSuccessHandler();
		var home = new SimpleUrlAuthenticationSuccessHandler("/");
		home.setAlwaysUseDefaultTargetUrl(true);
		return (request, response, authentication) -> {
			SavedRequest saved = requests.getRequest(request, response);
			if (saved != null && isAuthorizationRequest(saved.getRedirectUrl())) {
				backToAuthorization.onAuthenticationSuccess(request, response, authentication);
			}
			else {
				requests.removeRequest(request, response);
				home.onAuthenticationSuccess(request, response, authentication);
			}
		};
	}

	private static boolean isAuthorizationRequest(String url) {
		try {
			return "/oauth2/authorize".equals(URI.create(url).getPath());
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Admin implies Editor, Editor implies Viewer (spec section 1). */
	@Bean
	RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
			.role("ADMIN").implies("EDITOR")
			.role("EDITOR").implies("VIEWER")
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
