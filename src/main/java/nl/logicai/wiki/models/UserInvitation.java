package nl.logicai.wiki.models;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An invitation sent by an admin (spec F-01: no public registration): the single-use link lets the
 * invitee choose a username and password, the {@link WikiUser} row is created only then. Only the
 * SHA-256 hash of the token is stored; the token itself is in the mail. Usable until {@code expiresAt}
 * and until it is accepted once.
 */
@Entity
@Table(name = "user_invitation")
public class UserInvitation {

	@Id
	private UUID id;

	@Column(nullable = false, length = WikiUser.EMAIL_MAX)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "invited_by", nullable = false, length = 200)
	private String invitedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	protected UserInvitation() {
	}

	public static UserInvitation create(String email, Role role, String tokenHash, String invitedBy, Instant now, Duration ttl) {
		UserInvitation invitation = new UserInvitation();
		invitation.id = UUID.randomUUID();
		invitation.email = email;
		invitation.role = role;
		invitation.tokenHash = tokenHash;
		invitation.invitedBy = invitedBy;
		invitation.createdAt = now;
		invitation.expiresAt = now.plus(ttl);
		return invitation;
	}

	public boolean isUsable(Instant now) {
		return acceptedAt == null && now.isBefore(expiresAt);
	}

	public void markAccepted(Instant now) {
		this.acceptedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public String getInvitedBy() {
		return invitedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

}
