package nl.logicai.wiki.exceptions;

/** A user form is incomplete or a change would lock the admins out (spec section 1, F-02). */
public class InvalidUserException extends RuntimeException {

	public InvalidUserException(String message) {
		super(message);
	}

}
