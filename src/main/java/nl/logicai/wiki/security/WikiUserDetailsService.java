package nl.logicai.wiki.security;

import java.util.Locale;

import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password login against the app_user table (spec F-01: only explicitly allowed users). The login
 * form takes the e-mail address; the principal name is the internal username, so audit entries,
 * revision authors and favorites stay keyed the same way as for Entra login. Users without a
 * password (Entra only) cannot log in with the form; deactivated users are refused as disabled.
 */
@Service
@Transactional(readOnly = true)
public class WikiUserDetailsService implements UserDetailsService {

	private final WikiUserRepository users;

	public WikiUserDetailsService(WikiUserRepository users) {
		this.users = users;
	}

	/**
	 * {@code email} is what the user typed in the login form: an e-mail address, case-insensitive. An
	 * account from before the e-mail login that has no address yet may still type its username, so a
	 * bootstrap admin is never locked out; as soon as an address is set, only the address works.
	 */
	@Override
	public UserDetails loadUserByUsername(String email) {
		String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		WikiUser user = users.findByEmailIgnoreCase(normalized)
			.or(() -> users.findByUsernameIgnoreCase(normalized).filter(u -> u.getEmail() == null))
			.filter(WikiUser::hasPassword)
			.orElseThrow(() -> new UsernameNotFoundException("Onbekende gebruiker"));
		return User.withUsername(user.getUsername())
			.password(user.getPasswordHash())
			.authorities(user.getRole().authority())
			.disabled(!user.isActive())
			.build();
	}

}
