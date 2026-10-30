package nl.logicai.wiki.config;

import java.time.Clock;
import java.util.Locale;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import nl.logicai.wiki.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * First admin for a fresh installation: with {@code WIKI_BOOTSTRAP_ADMIN_EMAIL} set and an empty
 * app_user table, that account is created as Admin at startup. Give a password for form login (the
 * e-mail address is the login); without one the account can only log in through Entra. The internal
 * username is derived from the address; the display name comes from {@code WIKI_BOOTSTRAP_ADMIN_NAME}
 * or defaults to that username. Later startups do nothing, so the account can be managed normally.
 */
@Configuration
class BootstrapAdminConfig {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

	@Bean
	ApplicationRunner bootstrapAdmin(WikiUserRepository users, PasswordEncoder encoder, Clock clock,
			@Value("${wiki.bootstrap-admin.email:}") String email,
			@Value("${wiki.bootstrap-admin.password:}") String password,
			@Value("${wiki.bootstrap-admin.name:}") String displayName) {
		return args -> {
			if (email.isBlank() || users.count() > 0) {
				return;
			}
			String mail = email.trim().toLowerCase(Locale.ROOT);
			String name = UserService.usernameBase(mail);
			String shown = displayName.isBlank() ? name : displayName.trim();
			String hash = password.isBlank() ? null : encoder.encode(password);
			users.save(WikiUser.create(name, shown, mail, hash, Role.ADMIN, "bootstrap", clock.instant()));
			log.info("Bootstrap admin '{}' created (login via {})", mail, hash != null ? "password" : "Entra");
		};
	}

}
