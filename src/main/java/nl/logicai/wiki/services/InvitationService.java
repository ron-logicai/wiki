package nl.logicai.wiki.services;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import nl.logicai.wiki.config.InvitationProperties;
import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.mail.WikiMailSender;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.UserInvitation;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.UserInvitationRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * New users come in by invitation (spec F-01: no public registration). An admin enters an e-mail
 * address and a role on /admin/users; the invitee gets a single-use link to /accountaanmaken that is
 * valid for {@code wiki.invitation.ttl} and chooses a username and password there. The
 * {@link WikiUser} row is created only on acceptance. Only the SHA-256 hash of the token is stored
 * ({@link LinkTokens}); the link is built from {@code wiki.base-url}, never from request headers.
 * Accepting runs without a session, so those methods have no {@code @PreAuthorize}.
 */
@Service
@Transactional
public class InvitationService {

	public static final String INVALID_LINK =
			"Deze uitnodiging is ongeldig of verlopen. Vraag de beheerder om een nieuwe uitnodiging.";

	private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

	private final UserInvitationRepository invitations;
	private final WikiUserRepository users;
	private final AuditEventRepository audit;
	private final UserService userService;
	private final WikiMailSender mail;
	private final InvitationProperties settings;
	private final String baseUrl;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public InvitationService(UserInvitationRepository invitations, WikiUserRepository users, AuditEventRepository audit,
			UserService userService, WikiMailSender mail, InvitationProperties settings,
			@Value("${wiki.base-url}") String baseUrl, Clock clock) {
		this.invitations = invitations;
		this.users = users;
		this.audit = audit;
		this.userService = userService;
		this.mail = mail;
		this.settings = settings;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.clock = clock;
	}

	/**
	 * Creates and mails an invitation. An address that already belongs to a user is refused; an older
	 * invitation for the same address is replaced, so only the newest link works. When the mail cannot
	 * be sent the whole invitation rolls back and the admin sees the server's reason: an invitation
	 * nobody received must not look like a sent one (unlike "wachtwoord vergeten", this page may say so).
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public UserInvitation invite(String rawEmail, Role role, String actor) {
		String email = UserService.validEmail(rawEmail);
		if (role == null) {
			throw new InvalidUserException("Kies een rol.");
		}
		if (users.findByEmailIgnoreCase(email).isPresent()) {
			throw new InvalidUserException("E-mailadres “" + email + "” is al in gebruik.");
		}
		Instant now = clock.instant();
		invitations.deleteByExpiresAtBefore(now);
		invitations.deleteByEmail(email);
		String token = LinkTokens.generate(random);
		UserInvitation invitation = invitations.save(
				UserInvitation.create(email, role, LinkTokens.hash(token), actor, now, settings.ttl()));
		audit.save(AuditEvent.of(UserService.USER_INVITE, null, actor, now, email + " als " + role.getLabel()));
		String link = baseUrl + "/accountaanmaken?token=" + token;
		try {
			mail.sendInvitation(email, role, actor, link, settings.ttl());
		}
		catch (MailException ex) {
			log.error("Uitnodiging voor {} kon niet worden verstuurd", email, ex);
			// RuntimeException, so @Transactional rolls the invitation and its audit event back.
			String reason = ex.getMostSpecificCause().getMessage();
			throw new InvalidUserException("De uitnodiging is niet verstuurd: " + (reason == null ? ex.getMessage() : reason)
					+ " Controleer de WIKI_MAIL_*-instellingen.");
		}
		return invitation;
	}

	/** The invitation behind a usable token: known, not yet accepted and not expired. */
	@Transactional(readOnly = true)
	public Optional<UserInvitation> invitationForToken(String token) {
		return usableInvitation(token);
	}

	/**
	 * Creates the account for a usable invitation with the username and password the invitee chose;
	 * e-mail and role come from the invitation. The invitation is burned, also on the second attempt
	 * with the same link.
	 */
	public WikiUser accept(String token, String rawUsername, String password, String passwordRepeat) {
		UserInvitation invitation = usableInvitation(token).orElseThrow(() -> new InvalidUserException(INVALID_LINK));
		String username = UserService.validUsername(rawUsername);
		if (users.findByUsernameIgnoreCase(username).isPresent()) {
			throw new InvalidUserException("Gebruikersnaam “" + username + "” bestaat al.");
		}
		if (users.findByEmailIgnoreCase(invitation.getEmail()).isPresent()) {
			throw new InvalidUserException("E-mailadres is inmiddels al in gebruik. Neem contact op met de beheerder.");
		}
		if (password == null || password.isEmpty()) {
			throw new InvalidUserException("Geef een wachtwoord.");
		}
		String hash = userService.hashPassword(password, passwordRepeat);
		Instant now = clock.instant();
		// The form has no separate name field: the username doubles as display name (as on /admin/users before).
		WikiUser user = users.save(WikiUser.create(username, username, invitation.getEmail(), hash,
				invitation.getRole(), invitation.getInvitedBy(), now));
		invitation.markAccepted(now);
		audit.save(AuditEvent.of(UserService.USER_CREATE, null, username, now,
				username + " als " + invitation.getRole().getLabel() + " via uitnodiging van " + invitation.getInvitedBy()));
		return user;
	}

	private Optional<UserInvitation> usableInvitation(String token) {
		if (!LinkTokens.plausible(token)) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return invitations.findByTokenHash(LinkTokens.hash(token)).filter(i -> i.isUsable(now));
	}

}
