package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A wiki page. The UUID is the permanent identity (spec F-03, section 5); title and parent may change.
 * Content is stored as validated BlockNote block JSON in a JSONB column, the single source of truth.
 */
@Entity
@Table(name = "page")
public class Page {

	@Id
	private UUID id;

	@Column(name = "parent_id")
	private UUID parentId;

	@Column(nullable = false, length = 200)
	private String title;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String document;

	@Column(name = "document_schema_version", nullable = false)
	private int documentSchemaVersion;

	@Column(name = "search_text", nullable = false, columnDefinition = "text")
	private String searchText;

	@Column(name = "created_by", nullable = false, length = 200)
	private String createdBy;

	@Column(name = "updated_by", nullable = false, length = 200)
	private String updatedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/** Optimistic lock for concurrent edits (spec F-10). Not the revision number. */
	@Version
	@Column(name = "lock_version", nullable = false)
	private Long lockVersion;

	/** Number of the latest revision in page_revision. */
	@Column(name = "current_revision", nullable = false)
	private int currentRevision;

	protected Page() {
	}

	public static Page create(String title, UUID parentId, WikiDocument document, String actor, Instant now) {
		Page page = new Page();
		page.id = UUID.randomUUID();
		page.parentId = parentId;
		page.title = title;
		page.document = document.json();
		page.documentSchemaVersion = document.schemaVersion();
		page.searchText = document.searchText();
		page.createdBy = actor;
		page.updatedBy = actor;
		page.createdAt = now;
		page.updatedAt = now;
		page.currentRevision = 0;
		return page;
	}

	/** Applies a new title and document; the caller records the matching revision. */
	public PageRevision update(String title, WikiDocument document, String actor, Instant now) {
		this.title = title;
		this.document = document.json();
		this.documentSchemaVersion = document.schemaVersion();
		this.searchText = document.searchText();
		this.updatedBy = actor;
		this.updatedAt = now;
		return nextRevision(actor, now);
	}

	/** Creates the next immutable revision from the current state (spec F-11). */
	public PageRevision nextRevision(String actor, Instant now) {
		this.currentRevision++;
		return new PageRevision(this, currentRevision, actor, now);
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getParentId() {
		return parentId;
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

	public String getSearchText() {
		return searchText;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public Long getLockVersion() {
		return lockVersion;
	}

	public int getCurrentRevision() {
		return currentRevision;
	}

}
