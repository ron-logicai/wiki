package nl.logicai.wiki.models;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Form model for /pages/{id}/move: the new parent (null = top level) and the version the user saw. */
public class MovePageForm {

	private UUID parentId;

	@NotNull
	private Long baseVersion;

	public UUID getParentId() {
		return parentId;
	}

	public void setParentId(UUID parentId) {
		this.parentId = parentId;
	}

	public Long getBaseVersion() {
		return baseVersion;
	}

	public void setBaseVersion(Long baseVersion) {
		this.baseVersion = baseVersion;
	}

}
