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
 * A starting document for new pages (spec F-15). Creating a page copies the document; the page
 * keeps no reference to the template, so later template edits never change existing pages.
 */
@Entity
@Table(name = "page_template")
public class PageTemplate {

	@Id
	private UUID id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 300)
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String document;

	@Column(name = "document_schema_version", nullable = false)
	private int documentSchemaVersion;

	@Column(name = "created_by", nullable = false, length = 200)
	private String createdBy;

	@Column(name = "updated_by", nullable = false, length = 200)
	private String updatedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Optimistic lock, like pages (spec section 8: also for templates). */
	@Version
	@Column(name = "lock_version", nullable = false)
	private Long lockVersion;

	protected PageTemplate() {
	}

	public static PageTemplate create(String title, String description, WikiDocument document, String actor, Instant now) {
		PageTemplate template = new PageTemplate();
		template.id = UUID.randomUUID();
		template.title = title;
		template.description = description;
		template.document = document.json();
		template.documentSchemaVersion = document.schemaVersion();
		template.createdBy = actor;
		template.updatedBy = actor;
		template.createdAt = now;
		template.updatedAt = now;
		return template;
	}

	public void update(String title, WikiDocument document, String actor, Instant now) {
		this.title = title;
		this.document = document.json();
		this.documentSchemaVersion = document.schemaVersion();
		this.updatedBy = actor;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getDocument() {
		return document;
	}

	public int getDocumentSchemaVersion() {
		return documentSchemaVersion;
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

	public Long getLockVersion() {
		return lockVersion;
	}

}
