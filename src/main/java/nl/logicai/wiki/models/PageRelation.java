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

/**
 * A page an editor marked as related to another page (the "Gerelateerd" block on the read view).
 * Directed: the row belongs to {@code pageId} and is shown there only. One row per (page, related).
 */
@Entity
@Table(name = "page_relation")
@IdClass(PageRelation.Key.class)
public class PageRelation {

	@Id
	@Column(name = "page_id")
	private UUID pageId;

	@Id
	@Column(name = "related_id")
	private UUID relatedId;

	@Column(name = "created_by", nullable = false, length = 200)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PageRelation() {
	}

	public PageRelation(UUID pageId, UUID relatedId, String actor, Instant now) {
		this.pageId = pageId;
		this.relatedId = relatedId;
		this.createdBy = actor;
		this.createdAt = now;
	}

	public UUID getPageId() {
		return pageId;
	}

	public UUID getRelatedId() {
		return relatedId;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public static class Key implements Serializable {

		private UUID pageId;
		private UUID relatedId;

		public Key() {
		}

		public Key(UUID pageId, UUID relatedId) {
			this.pageId = pageId;
			this.relatedId = relatedId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(pageId, key.pageId) && Objects.equals(relatedId, key.relatedId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pageId, relatedId);
		}
	}
}
