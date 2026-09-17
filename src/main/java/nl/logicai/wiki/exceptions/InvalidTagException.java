package nl.logicai.wiki.exceptions;

/** A tag name is blank, too long, or already taken by another tag (spec F-14). */
public class InvalidTagException extends RuntimeException {

	public InvalidTagException(String message) {
		super(message);
	}

}
