package nl.logicai.wiki.models;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A page a user has starred (spec U-03); one row per (page, user). */
@Entity
@Table(name = "page_favorite")
@IdClass(PageFavorite.Key.class)
public class PageFavorite {

	@Id
	@Column(name = "page_id")
	private UUID pageId;

	@Id
	@Column(nullable = false, length = 100)
	private String username;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PageFavorite() {
	}

	public PageFavorite(UUID pageId, String username, Instant now) {
		this.pageId = pageId;
		this.username = username;
		this.createdAt = now;
	}

	public UUID getPageId() {
		return pageId;
	}

	public String getUsername() {
		return username;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public static class Key implements Serializable {

		private UUID pageId;
		private String username;

		public Key() {
		}

		public Key(UUID pageId, String username) {
			this.pageId = pageId;
			this.username = username;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(pageId, key.pageId) && Objects.equals(username, key.username);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pageId, username);
		}
	}
}
