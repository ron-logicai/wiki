package nl.logicai.wiki.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Server-side authorization for every route (spec N-01, N-02).
 * <ul>
 *   <li>HTML requests without a session are redirected to the login page.</li>
 *   <li>Editor API requests ({@code /api/**}) get 401 instead of login HTML, so the editor
 *       never mistakes a login page for a successful save.</li>
 *   <li>CSRF stays enabled, also for fetch requests; the token is rendered as a meta tag.</li>
 *   <li>Company login (Entra ID via OIDC) is enabled automatically when a client registration
 *       is configured (profile {@code entra}).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/editor/**", "/css/**", "/error").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.formLogin(Customizer.withDefaults())
			.logout(logout -> logout.logoutSuccessUrl("/login?logout"))
			.exceptionHandling(ex -> {
				RequestMatcher api = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
				// Pages go to the login form (also without an Accept header); the JSON API gets 401.
				ex.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), new NegatedRequestMatcher(api));
				ex.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api);
			});

		if (clientRegistrations.getIfAvailable() != null) {
			http.oauth2Login(Customizer.withDefaults());
		}
		return http.build();
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
