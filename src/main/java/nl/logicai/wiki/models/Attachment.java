package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Metadata of an uploaded file. The bytes are stored on disk under the id (see AttachmentService);
 * the document refers to the file as {@code /attachments/{id}}.
 */
@Entity
@Table(name = "attachment")
public class Attachment {

	@Id
	private UUID id;

	@Column(name = "page_id")
	private UUID pageId;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "uploaded_by", nullable = false, length = 200)
	private String uploadedBy;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	protected Attachment() {
	}

	public static Attachment create(UUID id, UUID pageId, String fileName, String contentType, long sizeBytes,
			String actor, Instant now) {
		Attachment attachment = new Attachment();
		attachment.id = id;
		attachment.pageId = pageId;
		attachment.fileName = fileName;
		attachment.contentType = contentType;
		attachment.sizeBytes = sizeBytes;
		attachment.uploadedBy = actor;
		attachment.uploadedAt = now;
		return attachment;
	}

	/** The URL the document and the read view use for this file. */
	public String getUrl() {
		return "/attachments/" + id;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPageId() {
		return pageId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
