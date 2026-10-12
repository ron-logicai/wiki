package nl.logicai.wiki.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import nl.logicai.wiki.config.PasswordResetProperties;
import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.mail.ResetMailSender;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.PasswordResetToken;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.PasswordResetTokenRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Wachtwoord vergeten" (spec F-01, extension): a user asks for a reset link by e-mail address, gets a
 * single-use link that is valid for {@code wiki.password-reset.ttl}, and chooses a new password there.
 * Only the SHA-256 hash of the token is stored; the link is built from {@code wiki.base-url}, never
 * from request headers. Runs without a session, so no {@code @PreAuthorize}.
 */
@Service
@Transactional
public class PasswordResetService {

	public static final String INVALID_LINK = "Deze link is ongeldig of verlopen. Vraag een nieuwe aan.";
	public static final String UNKNOWN_ACCOUNT = "Uw account bestaat niet. Neem contact op met de beheerder.";

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
	private static final int TOKEN_BYTES = 32;

	private final WikiUserRepository users;
	private final PasswordResetTokenRepository tokens;
	private final AuditEventRepository audit;
	private final UserService userService;
	private final ResetMailSender mail;
	private final PasswordResetProperties settings;
	private final String baseUrl;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PasswordResetService(WikiUserRepository users, PasswordResetTokenRepository tokens, AuditEventRepository audit,
			UserService userService, ResetMailSender mail, PasswordResetProperties settings,
			@Value("${wiki.base-url}") String baseUrl, Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.audit = audit;
		this.userService = userService;
		this.mail = mail;
		this.settings = settings;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.clock = clock;
	}

	/**
	 * Creates and mails a reset link for the active, password-login account with this e-mail address.
	 * Unknown address, deactivated or Entra-only account: {@link InvalidUserException} with
	 * {@link #UNKNOWN_ACCOUNT}. Within the cooldown after a previous request nothing new is sent, but
	 * the caller shows the same "mail sent" message. A failing SMTP server is logged, never shown.
	 */
	public void request(String email) {
		String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		Instant now = clock.instant();
		tokens.deleteByExpiresAtBefore(now);
		WikiUser user = normalized.isEmpty() ? null : users.findByEmailIgnoreCase(normalized).orElse(null);
		if (user == null || !user.isActive() || !user.hasPassword()) {
			throw new InvalidUserException(UNKNOWN_ACCOUNT);
		}
		if (tokens.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(settings.cooldown()))) {
			log.debug("Herstel-link voor {} niet opnieuw gestuurd: binnen de wachttijd", user.getUsername());
			return;
		}
		tokens.deleteByUserId(user.getId());
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tokens.save(PasswordResetToken.create(user.getId(), hash(token), now, settings.ttl()));
		audit.save(AuditEvent.of(UserService.USER_PASSWORD_RESET_REQUEST, null, user.getUsername(), now, user.getUsername()));
		String link = baseUrl + "/wachtwoord-herstellen?token=" + token;
		try {
			mail.sendPasswordReset(user, link, settings.ttl());
		}
		catch (MailException ex) {
			log.error("Herstelmail voor {} kon niet worden verstuurd", user.getUsername(), ex);
		}
	}

	/** The user behind a usable token, if the token is valid, unused, unexpired and the account can log in. */
	@Transactional(readOnly = true)
	public Optional<WikiUser> userForToken(String token) {
		return usableToken(token).flatMap(t -> users.findById(t.getUserId()))
			.filter(user -> user.isActive() && user.hasPassword());
	}

	/** Sets the new password for the token's user and burns the token (and every other one of that user). */
	public WikiUser reset(String token, String password, String passwordRepeat) {
		PasswordResetToken resetToken = usableToken(token).orElseThrow(() -> new InvalidUserException(INVALID_LINK));
		WikiUser user = users.findById(resetToken.getUserId())
			.filter(u -> u.isActive() && u.hasPassword())
			.orElseThrow(() -> new InvalidUserException(INVALID_LINK));
		if (password == null || password.isEmpty()) {
			throw new InvalidUserException("Geef een nieuw wachtwoord.");
		}
		Instant now = clock.instant();
		user.changePassword(userService.hashPassword(password, passwordRepeat));
		resetToken.markUsed(now);
		tokens.deleteByUserId(user.getId());
		audit.save(AuditEvent.of(UserService.USER_PASSWORD_RESET, null, user.getUsername(), now, user.getUsername()));
		return user;
	}

	private Optional<PasswordResetToken> usableToken(String token) {
		if (token == null || token.isBlank() || token.length() > 100) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return tokens.findByTokenHash(hash(token)).filter(t -> t.isUsable(now));
	}

	/** Hex SHA-256 of the token; only this is stored (package-private for the tests). */
	static String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
