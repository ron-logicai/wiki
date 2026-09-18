package nl.logicai.wiki.services;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises YouTube links so the read view can show the video itself instead of only a hyperlink.
 * The document keeps the plain link (BlockNote subset unchanged); only the server-side rendering adds
 * an iframe, built from a strictly validated video id and never from user-supplied HTML (spec N-03).
 */
public final class VideoEmbed {

	/** The player is loaded from the privacy-enhanced domain; no cookies until the user presses play. */
	static final String EMBED_BASE = "https://www.youtube-nocookie.com/embed/";

	private static final Set<String> HOSTS = Set.of(
		"youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
		"youtube-nocookie.com", "www.youtube-nocookie.com", "youtu.be");

	private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
	private static final Pattern PATH_WITH_ID = Pattern.compile("^/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})/?$");
	private static final Pattern SECONDS = Pattern.compile("^(\\d{1,6})s?$");

	private VideoEmbed() {
	}

	/** The embed URL for a YouTube link, or empty when the link is not a recognised YouTube video. */
	public static Optional<String> embedUrl(String href) {
		if (href == null || href.isBlank()) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = new URI(href.strip());
		}
		catch (URISyntaxException ex) {
			return Optional.empty();
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!(scheme.equals("https") || scheme.equals("http")) || !HOSTS.contains(host)) {
			return Optional.empty();
		}
		String path = uri.getPath() == null ? "" : uri.getPath();
		String id = null;
		if (host.equals("youtu.be")) {
			id = path.length() > 1 ? path.substring(1).replaceAll("/$", "") : null;
		}
		else if (path.equals("/watch")) {
			id = queryParam(uri.getRawQuery(), "v");
		}
		else {
			Matcher m = PATH_WITH_ID.matcher(path);
			if (m.matches()) {
				id = m.group(1);
			}
		}
		if (id == null || !VIDEO_ID.matcher(id).matches()) {
			return Optional.empty();
		}
		String url = EMBED_BASE + id;
		String start = queryParam(uri.getRawQuery(), "t");
		if (start != null) {
			Matcher s = SECONDS.matcher(start);
			if (s.matches()) {
				url += "?start=" + Integer.parseInt(s.group(1));
			}
		}
		return Optional.of(url);
	}

	private static String queryParam(String rawQuery, String name) {
		if (rawQuery == null) {
			return null;
		}
		for (String pair : rawQuery.split("&")) {
			int eq = pair.indexOf('=');
			String key = eq < 0 ? pair : pair.substring(0, eq);
			if (key.equals(name)) {
				return eq < 0 ? "" : pair.substring(eq + 1);
			}
		}
		return null;
	}

}
