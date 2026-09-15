package nl.logicai.wiki.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Test users for local development and automated tests only (spec section 1:
 * "aparte testgebruikers ... mogen niet actief zijn in productie").
 * Active exclusively under the {@code local} profile; production uses Entra ID.
 */
@Configuration
@Profile("local")
class LocalUsersConfig {

	static final String PASSWORD = "wiki";

	@Bean
	UserDetailsService localUsers(PasswordEncoder encoder) {
		String password = encoder.encode(PASSWORD);
		return new InMemoryUserDetailsManager(
			User.withUsername("viewer").password(password).roles("VIEWER").build(),
			User.withUsername("editor").password(password).roles("EDITOR").build(),
			User.withUsername("admin").password(password).roles("ADMIN").build());
	}

}
