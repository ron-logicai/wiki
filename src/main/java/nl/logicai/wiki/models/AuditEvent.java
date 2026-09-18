package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Who did what and when (spec section 7): move, delete, restore. Never holds document content. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

	public static final String MOVE = "page.move";
	public static final String DELETE = "page.delete";
	public static final String RESTORE = "page.restore";
	public static final String RESTORE_REVISION = "page.restore-revision";

	@Id
	private UUID id;

	@Column(nullable = false, length = 40)
	private String action;

	@Column(name = "page_id")
	private UUID pageId;

	@Column(nullable = false, length = 200)
	private String actor;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false, length = 500)
	private String details;

	protected AuditEvent() {
	}

	public static AuditEvent of(String action, UUID pageId, String actor, Instant now, String details) {
		AuditEvent event = new AuditEvent();
		event.id = UUID.randomUUID();
		event.action = action;
		event.pageId = pageId;
		event.actor = actor;
		event.occurredAt = now;
		event.details = details == null ? "" : details;
		return event;
	}

	public UUID getId() {
		return id;
	}

	public String getAction() {
		return action;
	}

	public UUID getPageId() {
		return pageId;
	}

	public String getActor() {
		return actor;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getDetails() {
		return details;
	}

}
