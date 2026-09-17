package nl.logicai.wiki.models;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Link between a page and a tag (spec F-14). Kept as its own entity, not as a collection on
 * Page, so adding or removing a tag does not bump the page's lock version.
 */
@Entity
@Table(name = "page_tag")
@IdClass(PageTag.Key.class)
public class PageTag {

	@Id
	@Column(name = "page_id")
	private UUID pageId;

	@Id
	@Column(name = "tag_id")
	private UUID tagId;

	protected PageTag() {
	}

	public PageTag(UUID pageId, UUID tagId) {
		this.pageId = pageId;
		this.tagId = tagId;
	}

	public UUID getPageId() {
		return pageId;
	}

	public UUID getTagId() {
		return tagId;
	}

	/** Composite key for JPA. */
	public static class Key implements Serializable {

		private UUID pageId;
		private UUID tagId;

		public Key() {
		}

		public Key(UUID pageId, UUID tagId) {
			this.pageId = pageId;
			this.tagId = tagId;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(pageId, key.pageId) && Objects.equals(tagId, key.tagId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pageId, tagId);
		}

	}

}
