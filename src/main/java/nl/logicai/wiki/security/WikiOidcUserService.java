package nl.logicai.wiki.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Entra login (spec F-01): the Entra account must match an active app_user on username or e-mail
 * (claims {@code preferred_username} and {@code email}); everyone else is refused. The wiki role
 * comes from app_user, and the principal name is the wiki username so audit entries and revision
 * authors are the same for both login methods.
 */
public class WikiOidcUserService extends OidcUserService {

	private final WikiUserRepository users;

	public WikiOidcUserService(WikiUserRepository users) {
		this.users = users;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest request) {
		OidcUser oidc = super.loadUser(request);
		WikiUser user = lookup(oidc.getPreferredUsername()).or(() -> lookup(oidc.getEmail()))
			.orElseThrow(() -> refused("not_allowed", "Dit account is niet toegelaten tot de wiki."));
		if (!user.isActive()) {
			throw refused("deactivated", "Dit account is gedeactiveerd.");
		}
		List<GrantedAuthority> authorities = new ArrayList<>();
		for (GrantedAuthority authority : oidc.getAuthorities()) {
			if (!authority.getAuthority().startsWith("ROLE_")) {
				authorities.add(authority);
			}
		}
		authorities.add(new SimpleGrantedAuthority(user.getRole().authority()));
		return new WikiOidcUser(user.getUsername(), authorities, oidc.getIdToken(), oidc.getUserInfo());
	}

	private Optional<WikiUser> lookup(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		return users.findByUsernameIgnoreCase(value).or(() -> users.findByEmailIgnoreCase(value));
	}

	private static OAuth2AuthenticationException refused(String code, String message) {
		return new OAuth2AuthenticationException(new OAuth2Error(code, message, null), message);
	}

	/** An OIDC principal whose name is the wiki username instead of the Entra subject. */
	public static class WikiOidcUser extends DefaultOidcUser {

		private final String username;

		WikiOidcUser(String username, Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken,
				OidcUserInfo userInfo) {
			super(authorities, idToken, userInfo);
			this.username = username;
		}

		@Override
		public String getName() {
			return username;
		}

	}

}
