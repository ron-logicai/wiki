package nl.logicai.wiki.models;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single-use link for "wachtwoord vergeten" (spec F-01): only the SHA-256 hash of the token is stored,
 * the token itself is in the mail. Usable until {@code expiresAt} and until it is used once.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected PasswordResetToken() {
	}

	public static PasswordResetToken create(UUID userId, String tokenHash, Instant now, Duration ttl) {
		PasswordResetToken token = new PasswordResetToken();
		token.id = UUID.randomUUID();
		token.userId = userId;
		token.tokenHash = tokenHash;
		token.createdAt = now;
		token.expiresAt = now.plus(ttl);
		return token;
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
