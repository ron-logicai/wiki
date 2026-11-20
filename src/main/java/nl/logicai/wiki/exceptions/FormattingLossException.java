package nl.logicai.wiki.exceptions;

import java.util.List;

/**
 * A Markdown update would drop formatting Markdown cannot express (colours, widths) from blocks it changed,
 * and the caller did not accept that. Nothing was saved.
 */
public class FormattingLossException extends RuntimeException {

	private final List<String> losses;

	public FormattingLossException(List<String> losses) {
		super("Deze wijziging verwijdert opmaak die Markdown niet kan uitdrukken: " + String.join("; ", losses));
		this.losses = List.copyOf(losses);
	}

	/** What would be lost, one description per block (English, from {@code MarkdownLossDetector}). */
	public List<String> getLosses() {
		return losses;
	}

}
