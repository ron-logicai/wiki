package nl.logicai.wiki.services;

import java.util.Locale;

/**
 * Allowed link destinations (spec N-03): http, https, mailto and application-relative paths.
 * Script URLs and protocol-relative URLs are rejected.
 */
public final class LinkPolicy {

	private LinkPolicy() {
	}

	public static boolean isAllowed(String href) {
		if (href == null || href.isBlank() || href.length() > 2_000) {
			return false;
		}
		String value = href.strip();
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
			return true;
		}
		if (value.startsWith("#")) {
			return true;
		}
		return value.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\");
	}

	/** Returns the href when allowed, otherwise a harmless fragment link. */
	public static String sanitize(String href) {
		return isAllowed(href) ? href.strip() : "#";
	}

}
