package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.PasswordResetTokenRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User and role management for admins (spec section 1: activate/deactivate, manage roles; route
 * /admin/users). New users come in through {@link InvitationService}, never directly. {@link #delete}
 * removes the app_user row (and its reset tokens); usernames on pages and audit entries are plain
 * strings and stay as they are. Guards keep the acting admin from locking themselves out and keep at
 * least one active admin. Every change is an AuditEvent without a page.
 */
@Service
@Transactional
public class UserService {

	/** An account was created: by the invitee through an invitation link ({@link InvitationService}). */
	public static final String USER_CREATE = "user.create";
	/** An admin sent an invitation link ({@link InvitationService}). */
	public static final String USER_INVITE = "user.invite";
	public static final String USER_ROLE = "user.role";
	public static final String USER_PASSWORD = "user.password";
	public static final String USER_ACTIVATE = "user.activate";
	public static final String USER_DEACTIVATE = "user.deactivate";
	/** An admin removed the account on /admin/users. */
	public static final String USER_DELETE = "user.delete";
	/** A user changed their own password on /account/wachtwoord. */
	public static final String USER_PASSWORD_CHANGE = "user.password.change";
	/** A reset link was created for "wachtwoord vergeten" ({@link PasswordResetService}). */
	public static final String USER_PASSWORD_RESET_REQUEST = "user.password.reset-request";
	/** A user set a new password through a reset link. */
	public static final String USER_PASSWORD_RESET = "user.password.reset";

	static final int PASSWORD_MIN = 8;
	private static final Pattern USERNAME = Pattern.compile("[a-z0-9._@-]+");
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private final WikiUserRepository users;
	private final AuditEventRepository audit;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder encoder;
	private final Clock clock;

	public UserService(WikiUserRepository users, AuditEventRepository audit, PasswordResetTokenRepository tokens,
			PasswordEncoder encoder, Clock clock) {
		this.users = users;
		this.audit = audit;
		this.tokens = tokens;
		this.encoder = encoder;
		this.clock = clock;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<WikiUser> all() {
		return users.findAllByOrderByUsernameAsc();
	}

	@PreAuthorize("hasRole('ADMIN')")
	public WikiUser changeRole(UUID id, Role role, String actor) {
		if (role == null) {
			throw new InvalidUserException("Kies een rol.");
		}
		WikiUser user = get(id);
		if (user.getRole() == role) {
			return user;
		}
		if (user.getRole() == Role.ADMIN) {
			if (user.getUsername().equalsIgnoreCase(actor)) {
				throw new InvalidUserException("Je kunt je eigen beheerdersrol niet afnemen.");
			}
			refuseIfLastActiveAdmin(user);
		}
		String was = user.getRole().getLabel();
		user.changeRole(role);
		audit.save(AuditEvent.of(USER_ROLE, null, actor, clock.instant(),
				user.getUsername() + ": " + was + " naar " + role.getLabel()));
		return user;
	}

	@PreAuthorize("hasRole('ADMIN')")
	public WikiUser setActive(UUID id, boolean active, String actor) {
		WikiUser user = get(id);
		if (user.isActive() == active) {
			return user;
		}
		if (!active) {
			if (user.getUsername().equalsIgnoreCase(actor)) {
				throw new InvalidUserException("Je kunt jezelf niet deactiveren.");
			}
			if (user.getRole() == Role.ADMIN) {
				refuseIfLastActiveAdmin(user);
			}
		}
		user.setActive(active);
		audit.save(AuditEvent.of(active ? USER_ACTIVATE : USER_DEACTIVATE, null, actor, clock.instant(), user.getUsername()));
		return user;
	}

	/**
	 * Removes the account for good: the app_user row and its reset tokens (the only foreign key). The
	 * username stays as a string on pages, favorites and audit entries. An open session of the user is
	 * ended on their next request by ActiveUserFilter. Same guards as deactivating.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public WikiUser delete(UUID id, String actor) {
		WikiUser user = get(id);
		if (user.getUsername().equalsIgnoreCase(actor)) {
			throw new InvalidUserException("Je kunt jezelf niet verwijderen.");
		}
		if (user.getRole() == Role.ADMIN) {
			refuseIfLastActiveAdmin(user);
		}
		tokens.deleteByUserId(user.getId());
		users.delete(user);
		audit.save(AuditEvent.of(USER_DELETE, null, actor, clock.instant(), user.getUsername()));
		return user;
	}

	/**
	 * Sets or replaces the password of a user, so an admin can repair a mistyped password or give an
	 * Entra-only account form login. The new password works on the next login (spec F-01).
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public WikiUser setPassword(UUID id, String password, String passwordRepeat, String actor) {
		WikiUser user = get(id);
		if (password == null || password.isEmpty()) {
			throw new InvalidUserException("Geef een nieuw wachtwoord.");
		}
		user.changePassword(hashPassword(password, passwordRepeat));
		audit.save(AuditEvent.of(USER_PASSWORD, null, actor, clock.instant(), user.getUsername()));
		return user;
	}

	/**
	 * A logged-in user changes their own password (route /account/wachtwoord). The username must come
	 * from the {@code Authentication}, never from the form; the current password is checked first.
	 */
	@PreAuthorize("isAuthenticated()")
	public WikiUser changeOwnPassword(String username, String currentPassword, String password, String passwordRepeat) {
		WikiUser user = users.findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new InvalidUserException("Gebruiker niet gevonden."));
		if (!user.hasPassword()) {
			throw new InvalidUserException("Je account logt in via Microsoft en heeft geen wiki-wachtwoord.");
		}
		if (currentPassword == null || currentPassword.isEmpty() || !encoder.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidUserException("Het huidige wachtwoord klopt niet.");
		}
		if (password == null || password.isEmpty()) {
			throw new InvalidUserException("Geef een nieuw wachtwoord.");
		}
		user.changePassword(hashPassword(password, passwordRepeat));
		audit.save(AuditEvent.of(USER_PASSWORD_CHANGE, null, user.getUsername(), clock.instant(), user.getUsername()));
		return user;
	}

	/** Records the login time for password and Entra logins. */
	@EventListener
	public void onLogin(AuthenticationSuccessEvent event) {
		users.findByUsernameIgnoreCase(event.getAuthentication().getName())
			.ifPresent(user -> user.recordLogin(clock.instant()));
	}

	/**
	 * Trimmed, lower-cased username, or {@link InvalidUserException} when empty, too long or outside
	 * the allowed characters. Package-private so {@link InvitationService} applies the same rules.
	 */
	static String validUsername(String raw) {
		String username = normalize(raw);
		if (username.isEmpty() || username.length() > WikiUser.USERNAME_MAX || !USERNAME.matcher(username).matches()) {
			throw new InvalidUserException("Gebruikersnaam: alleen letters, cijfers en . _ @ - (maximaal 100 tekens).");
		}
		return username;
	}

	/**
	 * Trimmed display name as the invitee typed it (case kept), or {@link InvalidUserException} when empty
	 * or longer than the {@code display_name} column. Package-private for {@link InvitationService}.
	 */
	static String validDisplayName(String raw) {
		String name = raw == null ? "" : raw.trim();
		if (name.isEmpty() || name.length() > WikiUser.DISPLAY_NAME_MAX) {
			throw new InvalidUserException("Naam: vul je voor- en achternaam in (maximaal " + WikiUser.DISPLAY_NAME_MAX + " tekens).");
		}
		return name;
	}

	/** Display name of a username; the username itself when it is not (or no longer) in app_user. */
	@Transactional(readOnly = true)
	public String displayNameOf(String username) {
		if (username == null) {
			return null;
		}
		return users.findByUsernameIgnoreCase(username).map(WikiUser::getDisplayName).orElse(username);
	}

	/** Trimmed, lower-cased e-mail address, or {@link InvalidUserException} when empty, too long or malformed. */
	static String validEmail(String raw) {
		String email = normalize(raw);
		if (email.isEmpty() || email.length() > WikiUser.EMAIL_MAX || !EMAIL.matcher(email).matches()) {
			throw new InvalidUserException("E-mailadres is ongeldig.");
		}
		return email;
	}

	/**
	 * Length check and the repeat field, so a typo at creation does not lock the new user out.
	 * Package-private so {@link PasswordResetService} and {@link InvitationService} apply the same rules.
	 */
	String hashPassword(String password, String passwordRepeat) {
		if (password.length() < PASSWORD_MIN) {
			throw new InvalidUserException("Wachtwoord: minimaal " + PASSWORD_MIN + " tekens.");
		}
		if (!password.equals(passwordRepeat)) {
			throw new InvalidUserException("De wachtwoorden zijn niet gelijk; typ twee keer hetzelfde wachtwoord.");
		}
		return encoder.encode(password);
	}

	private void refuseIfLastActiveAdmin(WikiUser user) {
		if (user.isActive() && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
			throw new InvalidUserException("Dit is de laatste actieve beheerder; maak eerst een andere beheerder.");
		}
	}

	private WikiUser get(UUID id) {
		return users.findById(id).orElseThrow(() -> new InvalidUserException("Gebruiker niet gevonden."));
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

}
