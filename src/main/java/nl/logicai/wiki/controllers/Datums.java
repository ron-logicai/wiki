package nl.logicai.wiki.controllers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Datumweergave voor de templates, zoals in het wireframe: "Vandaag 10:24", "Gisteren 16:03",
 * "12 okt 14:37". Gebruik in Thymeleaf via {@code ${@datums.relatief(...)}}.
 */
@Component("datums")
public class Datums {

	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
	private static final Locale NL = Locale.forLanguageTag("nl-NL");
	private static final DateTimeFormatter TIJD = DateTimeFormatter.ofPattern("HH:mm", NL);
	private static final DateTimeFormatter DAG = DateTimeFormatter.ofPattern("d LLL HH:mm", NL);
	private static final DateTimeFormatter DAG_MET_JAAR = DateTimeFormatter.ofPattern("d LLL yyyy HH:mm", NL);
	private static final DateTimeFormatter VOLLEDIG = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", NL);

	private final Clock clock;

	public Datums(ObjectProvider<Clock> clock) {
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	public String relatief(Instant instant) {
		if (instant == null) {
			return "";
		}
		ZonedDateTime tijdstip = instant.atZone(ZONE);
		LocalDate dag = tijdstip.toLocalDate();
		LocalDate vandaag = LocalDate.now(clock.withZone(ZONE));
		if (dag.equals(vandaag)) {
			return "Vandaag " + TIJD.format(tijdstip);
		}
		if (dag.equals(vandaag.minusDays(1))) {
			return "Gisteren " + TIJD.format(tijdstip);
		}
		if (dag.getYear() == vandaag.getYear()) {
			return DAG.format(tijdstip);
		}
		return DAG_MET_JAAR.format(tijdstip);
	}

	public String volledig(Instant instant) {
		return instant == null ? "" : VOLLEDIG.format(instant.atZone(ZONE));
	}

}
