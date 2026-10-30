package nl.logicai.wiki.config;

import java.time.Clock;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test users for local development and automated tests only (spec section 1:
 * "aparte testgebruikers ... mogen niet actief zijn in productie").
 * Seeded into app_user exclusively under the {@code local} profile, so they only exist in a
 * database that was ever started with that profile; production uses Entra ID and admin-created users.
 * Login is by e-mail address: {@code viewer@wiki.nl}, {@code editor@wiki.nl}, {@code admin@wiki.nl},
 * password {@link #PASSWORD}; the internal usernames stay {@code viewer}/{@code editor}/{@code admin}.
 */
@Configuration
@Profile("local")
class LocalUsersConfig {

	static final String PASSWORD = "wiki";
	static final String DOMAIN = "@wiki.nl";

	@Bean
	ApplicationRunner localUsers(WikiUserRepository users, PasswordEncoder encoder, Clock clock) {
		return args -> {
			for (Role role : Role.values()) {
				String username = role.getLabel().toLowerCase();
				String email = username + DOMAIN;
				WikiUser existing = users.findByUsernameIgnoreCase(username).orElse(null);
				if (existing == null) {
					users.save(WikiUser.create(username, "Test " + role.getLabel(), email, encoder.encode(PASSWORD),
							role, "local-profile", clock.instant()));
				}
				else if (!email.equalsIgnoreCase(existing.getEmail()) && users.findByEmailIgnoreCase(email).isEmpty()) {
					// Older local databases hold another address (e.g. @wiki.local); move to the documented login.
					existing.setEmail(email);
					users.save(existing);
				}
			}
		};
	}

}
