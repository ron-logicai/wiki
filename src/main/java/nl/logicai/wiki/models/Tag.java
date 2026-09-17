package nl.logicai.wiki.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A label that can be attached to many pages (spec F-14). The name is unique ignoring case. */
@Entity
@Table(name = "tag")
public class Tag {

	public static final int MAX_LENGTH = 50;

	@Id
	private UUID id;

	@Column(nullable = false, length = MAX_LENGTH)
	private String name;

	@Column(name = "created_by", nullable = false)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Tag() {
	}

	public static Tag create(String name, String actor, Instant now) {
		Tag tag = new Tag();
		tag.id = UUID.randomUUID();
		tag.name = name;
		tag.createdBy = actor;
		tag.createdAt = now;
		return tag;
	}

	public void rename(String name) {
		this.name = name;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
