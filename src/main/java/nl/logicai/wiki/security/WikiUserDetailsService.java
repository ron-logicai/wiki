package nl.logicai.wiki.security;

import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password login against the app_user table (spec F-01: only explicitly allowed users). Users without
 * a password (Entra only) cannot log in with the form; deactivated users are refused as disabled.
 */
@Service
@Transactional(readOnly = true)
public class WikiUserDetailsService implements UserDetailsService {

	private final WikiUserRepository users;

	public WikiUserDetailsService(WikiUserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		WikiUser user = users.findByUsernameIgnoreCase(username)
			.filter(WikiUser::hasPassword)
			.orElseThrow(() -> new UsernameNotFoundException("Onbekende gebruiker"));
		return User.withUsername(user.getUsername())
			.password(user.getPasswordHash())
			.authorities(user.getRole().authority())
			.disabled(!user.isActive())
			.build();
	}

}
