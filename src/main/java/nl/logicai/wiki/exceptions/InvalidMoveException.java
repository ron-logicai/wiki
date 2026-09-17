package nl.logicai.wiki.exceptions;

/** A page cannot be placed under itself or under one of its own descendants (spec F-04). */
public class InvalidMoveException extends RuntimeException {

	public InvalidMoveException(String message) {
		super(message);
	}

}
