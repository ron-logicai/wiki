package nl.logicai.wiki.models;

/** Global roles (spec section 1). ADMIN implies EDITOR implies VIEWER, see SecurityConfig. */
public enum Role {
	VIEWER("Viewer"), EDITOR("Editor"), ADMIN("Admin");

	private final String label;

	Role(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public String authority() {
		return "ROLE_" + name();
	}
}
