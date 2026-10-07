package nl.logicai.wiki.models;

/** Status van een pagina (spec U-07): een klein vast setje, geen publicatiestroom. */
public enum PageStatus {
	CONCEPT("Concept", "De pagina is een concept."),
	ACTUEEL("Actueel", "De pagina is actueel."),
	VEROUDERD("Verouderd", "De pagina is verouderd.");

	private final String label;

	/** Melding na het kiezen van deze status (spec U-07). */
	private final String melding;

	PageStatus(String label, String melding) {
		this.label = label;
		this.melding = melding;
	}

	public String getLabel() {
		return label;
	}

	public String getMelding() {
		return melding;
	}
}
