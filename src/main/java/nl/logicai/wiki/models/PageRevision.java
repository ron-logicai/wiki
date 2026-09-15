package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable snapshot of a page's title and document (spec F-11). Never updated after insert.
 */
@Entity
@Table(name = "page_revision")
public class PageRevision {

	@Id
	private UUID id;

	@Column(name = "page_id", nullable = false)
	private UUID pageId;

	@Column(name = "revision_number", nullable = false)
	private int revisionNumber;

	@Column(nullable = false, length = 200)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String document;

	@Column(name = "document_schema_version", nullable = false)
	private int documentSchemaVersion;

	@Column(nullable = false, length = 200)
	private String author;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PageRevision() {
	}

	PageRevision(Page page, int revisionNumber, String author, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.pageId = page.getId();
		this.revisionNumber = revisionNumber;
		this.title = page.getTitle();
		this.document = page.getDocument();
		this.documentSchemaVersion = page.getDocumentSchemaVersion();
		this.author = author;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPageId() {
		return pageId;
	}

	public int getRevisionNumber() {
		return revisionNumber;
	}

	public String getTitle() {
		return title;
	}

	public String getDocument() {
		return document;
	}

	public int getDocumentSchemaVersion() {
		return documentSchemaVersion;
	}

	public String getAuthor() {
		return author;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
