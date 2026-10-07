package nl.logicai.wiki.models;

/** Status van een pagina (spec U-07): een klein vast setje, geen publicatiestroom. */
public enum PageStatus {
	CONCEPT("Concept"),
	ACTUEEL("Actueel"),
	VEROUDERD("Verouderd");

	private final String label;

	PageStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
