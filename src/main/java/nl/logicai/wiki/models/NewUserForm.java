package nl.logicai.wiki.models;

/** Form on /admin/users for a new user. The password is optional: Entra-only users have none. */
public class NewUserForm {

	private String username = "";
	private String email = "";
	private String password = "";
	private String passwordRepeat = "";
	private Role role = Role.VIEWER;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPasswordRepeat() {
		return passwordRepeat;
	}

	public void setPasswordRepeat(String passwordRepeat) {
		this.passwordRepeat = passwordRepeat;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

}
