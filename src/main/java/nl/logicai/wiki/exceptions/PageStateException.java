package nl.logicai.wiki.exceptions;

/** The page is in a state that does not allow the action, e.g. deleting a page with active subpages (spec F-12). */
public class PageStateException extends RuntimeException {

	public PageStateException(String message) {
		super(message);
	}

}
