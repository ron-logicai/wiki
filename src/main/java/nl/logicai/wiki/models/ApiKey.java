package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A key for the REST API on /api/v1 (docs/api.md). It belongs to one user and acts with that user's name
 * and current role. Only the SHA-256 hash of the key is stored; {@code prefix} is its first characters, so
 * the owner recognises it in the list.
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

	public static final int NAME_MAX = 100;
	public static final int PREFIX_LENGTH = 12;

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, length = NAME_MAX)
	private String name;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "key_prefix", nullable = false, length = PREFIX_LENGTH)
	private String prefix;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	protected ApiKey() {
	}

	public static ApiKey create(UUID userId, String name, String tokenHash, String prefix, Instant now) {
		ApiKey key = new ApiKey();
		key.id = UUID.randomUUID();
		key.userId = userId;
		key.name = name;
		key.tokenHash = tokenHash;
		key.prefix = prefix;
		key.createdAt = now;
		return key;
	}

	public void recordUse(Instant now) {
		this.lastUsedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public String getPrefix() {
		return prefix;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

}
