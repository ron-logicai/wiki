package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An allowed user of the wiki (spec F-01, F-02). Users are never deleted, only deactivated, so audit
 * entries and revision authors keep pointing at a real account. {@code passwordHash} is null for
 * users who only log in through Entra.
 */
@Entity
@Table(name = "app_user")
public class WikiUser {

	public static final int USERNAME_MAX = 100;
	public static final int DISPLAY_NAME_MAX = 200;
	public static final int EMAIL_MAX = 200;

	@Id
	private UUID id;

	@Column(nullable = false, length = USERNAME_MAX)
	private String username;

	@Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX)
	private String displayName;

	@Column(length = EMAIL_MAX)
	private String email;

	@Column(name = "password_hash", length = 200)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by", nullable = false, length = 200)
	private String createdBy;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected WikiUser() {
	}

	public static WikiUser create(String username, String displayName, String email, String passwordHash,
			Role role, String actor, Instant now) {
		WikiUser user = new WikiUser();
		user.id = UUID.randomUUID();
		user.username = username;
		user.displayName = displayName;
		user.email = email;
		user.passwordHash = passwordHash;
		user.role = role;
		user.active = true;
		user.createdAt = now;
		user.createdBy = actor;
		return user;
	}

	public void changeRole(Role role) {
		this.role = role;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
	}

	public boolean hasPassword() {
		return passwordHash != null;
	}

	public UUID getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

}
