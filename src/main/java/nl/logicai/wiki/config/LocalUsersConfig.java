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
 */
@Configuration
@Profile("local")
class LocalUsersConfig {

	static final String PASSWORD = "wiki";

	@Bean
	ApplicationRunner localUsers(WikiUserRepository users, PasswordEncoder encoder, Clock clock) {
		return args -> {
			for (Role role : Role.values()) {
				String username = role.getLabel().toLowerCase();
				// An address per test user, so "wachtwoord vergeten" can be tried locally (the link is logged).
				String email = username + "@wiki.local";
				WikiUser existing = users.findByUsernameIgnoreCase(username).orElse(null);
				if (existing == null) {
					users.save(WikiUser.create(username, "Test " + role.getLabel(), email, encoder.encode(PASSWORD),
							role, "local-profile", clock.instant()));
				}
				else if (existing.getEmail() == null && users.findByEmailIgnoreCase(email).isEmpty()) {
					existing.setEmail(email);
					users.save(existing);
				}
			}
		};
	}

}
