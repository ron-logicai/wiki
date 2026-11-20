package nl.logicai.wiki.config;

import nl.logicai.wiki.security.ApiKeyAuthenticationFilter;
import nl.logicai.wiki.services.ApiKeyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * The filter chain of the REST API on /api/v1 (docs/api.md), in front of the general chain of
 * {@link SecurityConfig} and after the MCP chains. An API key is the only way in: the chain is stateless
 * and has no login, so a session cookie of a logged-in browser does nothing here. The editor endpoints
 * under /api/pages, /api/templates and /api/attachments are not part of it and stay on session + CSRF.
 * Role checks are the {@code @PreAuthorize} annotations on the services, as for the web UI.
 */
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {

	@Bean
	@Order(3)
	SecurityFilterChain restApiFilterChain(HttpSecurity http, ApiKeyService keys) throws Exception {
		http
			.securityMatcher("/api/v1", "/api/v1/**")
			.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
			// A key in a header is not sent by a browser on its own, so there is nothing for CSRF to protect here.
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(cache -> cache.disable())
			.addFilterBefore(new ApiKeyAuthenticationFilter(keys), AnonymousAuthenticationFilter.class)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(ApiKeyAuthenticationFilter.entryPoint())
				.accessDeniedHandler(ApiKeyAuthenticationFilter.accessDeniedHandler()));
		return http.build();
	}

}
