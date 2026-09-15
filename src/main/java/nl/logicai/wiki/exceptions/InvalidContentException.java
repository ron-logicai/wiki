package nl.logicai.wiki.exceptions;

/** Thrown when a submitted title or document does not meet the documented subset. Maps to HTTP 400. */
public class InvalidContentException extends RuntimeException {

	public InvalidContentException(String message) {
		super(message);
	}

}
