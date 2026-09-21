package nl.logicai.wiki.security;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A minimal browser for the login-flow tests (spec N-02): redirects are never followed so every hop
 * can be asserted, cookies are kept per name from {@code Set-Cookie}, and the CSRF token is read from
 * the rendered HTML the way a form or the editor does. The real HTTP stack is used on purpose: only
 * there do the cookie attributes and the redirect chain exist.
 */
final class BrowserSession {

	/** Matches the hidden input of the login form and the meta tag of the wiki pages. */
	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*?(?:value|content)=\"([^\"]+)\"");

	private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private final String baseUrl;

	private final Map<String, String> cookies = new LinkedHashMap<>();

	private List<String> lastSetCookies = List.of();

	BrowserSession(int port) {
		this.baseUrl = "http://localhost:" + port;
	}

	String baseUrl() {
		return baseUrl;
	}

	HttpResponse<String> get(String path) {
		return send(request(path).GET());
	}

	HttpResponse<String> postForm(String path, Map<String, String> fields) {
		String body = fields.entrySet().stream()
			.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
			.collect(Collectors.joining("&"));
		return send(request(path)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	HttpResponse<String> putJson(String path, String json, Map<String, String> headers) {
		HttpRequest.Builder builder = request(path)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString(json));
		headers.forEach(builder::header);
		return send(builder);
	}

	/** The CSRF token rendered in the given HTML (login form input or wiki meta tag). */
	static String csrfToken(String html) {
		Matcher matcher = CSRF.matcher(html);
		if (!matcher.find()) {
			throw new AssertionError("Geen CSRF-token gevonden in de HTML");
		}
		return matcher.group(1);
	}

	/** The Location header of a redirect, without the base URL so tests can compare paths. */
	String location(HttpResponse<?> response) {
		String location = response.headers().firstValue("Location")
			.orElseThrow(() -> new AssertionError("Geen Location-header, status was " + response.statusCode()));
		return location.startsWith(baseUrl) ? location.substring(baseUrl.length()) : location;
	}

	/** Every Set-Cookie header of the last response. */
	List<String> lastSetCookies() {
		return lastSetCookies;
	}

	/** The Set-Cookie header for the given cookie name in the last response, if any. */
	Optional<String> lastSetCookie(String name) {
		return lastSetCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst();
	}

	Optional<String> cookie(String name) {
		return Optional.ofNullable(cookies.get(name));
	}

	void forgetCookies() {
		cookies.clear();
	}

	private HttpRequest.Builder request(String path) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
		if (!cookies.isEmpty()) {
			builder.header("Cookie", cookies.entrySet().stream()
				.map(e -> e.getKey() + "=" + e.getValue())
				.collect(Collectors.joining("; ")));
		}
		return builder;
	}

	private HttpResponse<String> send(HttpRequest.Builder builder) {
		try {
			HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			lastSetCookies = response.headers().allValues("Set-Cookie");
			for (String header : lastSetCookies) {
				remember(header);
			}
			return response;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private void remember(String setCookie) {
		String[] parts = setCookie.split(";");
		String[] nameValue = parts[0].split("=", 2);
		String name = nameValue[0].trim();
		String value = nameValue.length > 1 ? nameValue[1].trim() : "";
		boolean expired = false;
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].trim().equalsIgnoreCase("Max-Age=0")) {
				expired = true;
			}
		}
		if (expired || value.isEmpty()) {
			cookies.remove(name);
		}
		else {
			cookies.put(name, value);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
