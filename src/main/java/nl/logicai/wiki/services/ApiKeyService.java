package nl.logicai.wiki.services;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import nl.logicai.wiki.exceptions.InvalidUserException;
import nl.logicai.wiki.models.ApiKey;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.ApiKeyRepository;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API keys for the REST API on /api/v1 (docs/api.md). A key belongs to a user and acts with that user's
 * name and current role; the user is read from {@code app_user} on every request, so deactivating and role
 * changes apply at once. Only the SHA-256 hash is stored ({@link LinkTokens}); the key itself is returned
 * once, by {@link #create}. Keys are revoked together with the MCP connections: when the account is
 * deleted or deactivated and when its password changes.
 */
@Service
@Transactional
public class ApiKeyService {

	/** Every key starts with this, so a leaked key is recognisable as one of the wiki. */
	public static final String PREFIX = "wiki_";
	static final int MAX_PER_USER = 10;
	/** {@code last_used_at} is for people; one write per this period is enough. */
	private static final Duration USE_RESOLUTION = Duration.ofMinutes(5);

	/**
	 * A key as shown in the lists; never holds the key itself.
	 *
	 * @param prefix the first characters of the key
	 */
	public record Sleutel(UUID id, String username, String naam, String prefix, Instant aangemaaktOp, Instant laatstGebruikt) {
	}

	/** A new key; {@code token} is the key itself and is not retrievable afterwards. */
	public record Nieuw(Sleutel sleutel, String token) {
	}

	/** The user and key behind a request. */
	public record Authenticated(WikiUser user, ApiKey key) {
	}

	private final ApiKeyRepository keys;
	private final WikiUserRepository users;
	private final AuditEventRepository audit;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public ApiKeyService(ApiKeyRepository keys, WikiUserRepository users, AuditEventRepository audit, Clock clock) {
		this.keys = keys;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	/** Creates a key for this user. The username must come from the {@code Authentication}, never from the form. */
	@PreAuthorize("hasRole('EDITOR')")
	public Nieuw create(String username, String rawName) {
		WikiUser user = users.findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new InvalidUserException("Gebruiker niet gevonden."));
		String name = rawName == null ? "" : rawName.strip();
		if (name.isEmpty() || name.length() > ApiKey.NAME_MAX) {
			throw new InvalidUserException("Naam: geef de key een naam van maximaal " + ApiKey.NAME_MAX + " tekens.");
		}
		if (keys.countByUserId(user.getId()) >= MAX_PER_USER) {
			throw new InvalidUserException("Je hebt al " + MAX_PER_USER + " API keys. Trek er eerst een in.");
		}
		String token = PREFIX + LinkTokens.generate(random);
		ApiKey key = keys.save(ApiKey.create(user.getId(), name, LinkTokens.hash(token),
			token.substring(0, ApiKey.PREFIX_LENGTH), clock.instant()));
		audit.save(AuditEvent.of(AuditEvent.API_KEY_CREATE, null, user.getUsername(), clock.instant(), name));
		return new Nieuw(sleutel(key, user.getUsername()), token);
	}

	/**
	 * The active user behind a key, or empty when the key is unknown or revoked or the user is deactivated.
	 * Called by {@code ApiKeyAuthenticationFilter} before anyone is authenticated, so no {@code @PreAuthorize}.
	 */
	public Optional<Authenticated> authenticate(String token) {
		if (!LinkTokens.plausible(token) || !token.startsWith(PREFIX)) {
			return Optional.empty();
		}
		ApiKey key = keys.findByTokenHash(LinkTokens.hash(token)).orElse(null);
		if (key == null) {
			return Optional.empty();
		}
		WikiUser user = users.findById(key.getUserId()).filter(WikiUser::isActive).orElse(null);
		if (user == null) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(now.minus(USE_RESOLUTION))) {
			key.recordUse(now);
		}
		return Optional.of(new Authenticated(user, key));
	}

	/** This user's keys, newest first. */
	@Transactional(readOnly = true)
	public List<Sleutel> keysOf(String username) {
		return users.findByUsernameIgnoreCase(username)
			.map(user -> keys.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
				.map(key -> sleutel(key, user.getUsername()))
				.toList())
			.orElse(List.of());
	}

	/** Every key of every user, for the admin overview. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<Sleutel> allKeys() {
		List<ApiKey> all = keys.findAllByOrderByCreatedAtDesc();
		Map<UUID, String> usernames = users.findAllById(all.stream().map(ApiKey::getUserId).distinct().toList()).stream()
			.collect(Collectors.toMap(WikiUser::getId, WikiUser::getUsername, (a, b) -> a));
		Function<ApiKey, String> owner = key -> usernames.getOrDefault(key.getUserId(), "");
		return all.stream().map(key -> sleutel(key, owner.apply(key))).toList();
	}

	/** Revokes one of the user's own keys. Someone else's id changes nothing. */
	public boolean revoke(UUID id, String username) {
		WikiUser user = users.findByUsernameIgnoreCase(username).orElse(null);
		ApiKey key = keys.findById(id).orElse(null);
		if (user == null || key == null || !key.getUserId().equals(user.getId())) {
			return false;
		}
		return remove(key, user.getUsername(), user.getUsername());
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean revokeAsAdmin(UUID id, String actor) {
		ApiKey key = keys.findById(id).orElse(null);
		if (key == null) {
			return false;
		}
		String owner = users.findById(key.getUserId()).map(WikiUser::getUsername).orElse("");
		return remove(key, owner, actor);
	}

	/**
	 * Removes every key of a user. Called when the account is deleted or deactivated and when its password
	 * changes: whoever knew the old password may have created a key with it.
	 */
	public long revokeAllFor(WikiUser user, String actor) {
		long removed = keys.deleteByUserId(user.getId());
		if (removed > 0) {
			audit.save(AuditEvent.of(AuditEvent.API_KEY_REVOKE, null, actor, clock.instant(),
				"alle API keys van " + user.getUsername()));
		}
		return removed;
	}

	private boolean remove(ApiKey key, String owner, String actor) {
		keys.delete(key);
		audit.save(AuditEvent.of(AuditEvent.API_KEY_REVOKE, null, actor, clock.instant(), key.getName() + " van " + owner));
		return true;
	}

	private static Sleutel sleutel(ApiKey key, String username) {
		return new Sleutel(key.getId(), username, key.getName(), key.getPrefix(), key.getCreatedAt(), key.getLastUsedAt());
	}

}
