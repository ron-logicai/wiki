package nl.logicai.wiki.config;

import java.time.Clock;
import java.util.Locale;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * First admin for a fresh installation: with {@code WIKI_BOOTSTRAP_ADMIN_USERNAME} set and an empty
 * app_user table, that user is created as Admin at startup. Give a password for form login, or an
 * e-mail address for Entra login. Later startups do nothing, so the account can be managed normally.
 */
@Configuration
class BootstrapAdminConfig {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

	@Bean
	ApplicationRunner bootstrapAdmin(WikiUserRepository users, PasswordEncoder encoder, Clock clock,
			@Value("${wiki.bootstrap-admin.username:}") String username,
			@Value("${wiki.bootstrap-admin.password:}") String password,
			@Value("${wiki.bootstrap-admin.email:}") String email) {
		return args -> {
			if (username.isBlank() || users.count() > 0) {
				return;
			}
			String name = username.trim().toLowerCase(Locale.ROOT);
			String hash = password.isBlank() ? null : encoder.encode(password);
			String mail = email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
			users.save(WikiUser.create(name, name, mail, hash, Role.ADMIN, "bootstrap", clock.instant()));
			log.info("Bootstrap admin '{}' created (login via {})", name, hash != null ? "password" : "Entra");
		};
	}

}
