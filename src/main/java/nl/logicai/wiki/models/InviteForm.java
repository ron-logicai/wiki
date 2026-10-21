package nl.logicai.wiki.models;

/** Form on /admin/users: who to invite and with which role. The invitee chooses username and password. */
public class InviteForm {

	private String email = "";
	private Role role = Role.VIEWER;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

}
