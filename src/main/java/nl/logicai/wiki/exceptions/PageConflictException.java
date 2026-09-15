package nl.logicai.wiki.exceptions;

/**
 * The submitted change was based on an outdated version (spec F-10): HTTP 409.
 * Nothing is overwritten; the client keeps its own text and can open the current version separately.
 */
public class PageConflictException extends RuntimeException {

	private final long currentVersion;

	public PageConflictException(long currentVersion) {
		super("De pagina is intussen gewijzigd (huidige versie " + currentVersion + ").");
		this.currentVersion = currentVersion;
	}

	public long getCurrentVersion() {
		return currentVersion;
	}

}
