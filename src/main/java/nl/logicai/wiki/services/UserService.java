package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.NewUserForm;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User and role management for admins (spec section 1: activate/deactivate, manage roles; route
 * /admin/users). Users are never deleted. Guards keep the acting admin from locking themselves out
 * and keep at least one active admin. Every change is an AuditEvent without a page.
 */
@Service
@Transactional
public class UserService {

	public static final String USER_CREATE = "user.create";
	public static final String USER_ROLE = "user.role";
	public static final String USER_ACTIVATE = "user.activate";
	public static final String USER_DEACTIVATE = "user.deactivate";

	static final int PASSWORD_MIN = 8;
	private static final Pattern USERNAME = Pattern.compile("[a-z0-9._@-]+");
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private final WikiUserRepository users;
	private final AuditEventRepository audit;
	private final PasswordEncoder encoder;
	private final Clock clock;

	public UserService(WikiUserRepository users, AuditEventRepository audit, PasswordEncoder encoder, Clock clock) {
		this.users = users;
		this.audit = audit;
		this.encoder = encoder;
		this.clock = clock;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<WikiUser> all() {
		return users.findAllByOrderByUsernameAsc();
	}

	@PreAuthorize("hasRole('ADMIN')")
	public WikiUser create(NewUserForm form, String actor) {
		String username = normalize(form.getUsername());
		if (username.isEmpty() || username.length() > WikiUser.USERNAME_MAX || !USERNAME.matcher(username).matches()) {
			throw new InvalidUserException("Gebruikersnaam: alleen letters, cijfers en . _ @ - (maximaal 100 tekens).");
		}
		String displayName = form.getDisplayName() == null ? "" : form.getDisplayName().trim();
		if (displayName.isEmpty() || displayName.length() > WikiUser.DISPLAY_NAME_MAX) {
			throw new InvalidUserException("Naam is verplicht (maximaal 200 tekens).");
		}
		String email = normalize(form.getEmail());
		if (email.isEmpty()) {
			email = null;
		}
		else if (email.length() > WikiUser.EMAIL_MAX || !EMAIL.matcher(email).matches()) {
			throw new InvalidUserException("E-mailadres is ongeldig.");
		}
		String password = form.getPassword() == null ? "" : form.getPassword();
		String hash = null;
		if (!password.isEmpty()) {
			if (password.length() < PASSWORD_MIN) {
				throw new InvalidUserException("Wachtwoord: minimaal " + PASSWORD_MIN + " tekens.");
			}
			hash = encoder.encode(password);
		}
		else if (email == null) {
			throw new InvalidUserException("Geef een wachtwoord, of een e-mailadres voor login via Entra.");
		}
		if (form.getRole() == null) {
			throw new InvalidUserException("Kies een rol.");
		}
		if (users.findByUsernameIgnoreCase(username).isPresent()) {
			throw new InvalidUserException("Gebruikersnaam “" + username + "” bestaat al.");
		}
		if (email != null && users.findByEmailIgnoreCase(email).isPresent()) {
			throw new InvalidUserException("E-mailadres “" + email + "” is al in gebruik.");
		}
		WikiUser user = users.save(WikiUser.create(username, displayName, email, hash, form.getRole(), actor, clock.instant()));
		audit.save(AuditEvent.of(USER_CREATE, null, actor, clock.instant(), username + " als " + form.getRole().getLabel()));
		return user;
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

	/** Records the login time for password and Entra logins. */
	@EventListener
	public void onLogin(AuthenticationSuccessEvent event) {
		users.findByUsernameIgnoreCase(event.getAuthentication().getName())
			.ifPresent(user -> user.recordLogin(clock.instant()));
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
