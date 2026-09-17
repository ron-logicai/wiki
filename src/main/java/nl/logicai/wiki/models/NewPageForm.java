package nl.logicai.wiki.models;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Form model for /pages/new; bound separately from the JPA entity (spec section 6). */
public class NewPageForm {

	@NotBlank(message = "Een titel is verplicht.")
	@Size(max = 200, message = "De titel mag maximaal 200 tekens bevatten.")
	private String title = "";

	private UUID parentId;

	/** Optional template to copy the starting content from (spec F-15). */
	private UUID templateId;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public UUID getParentId() {
		return parentId;
	}

	public void setParentId(UUID parentId) {
		this.parentId = parentId;
	}

	public UUID getTemplateId() {
		return templateId;
	}

	public void setTemplateId(UUID templateId) {
		this.templateId = templateId;
	}

}
