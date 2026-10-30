package nl.logicai.wiki.controllers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Weergavenamen voor de templates: {@code ${@namen.weergave(pagina.updatedBy)}} geeft de naam uit
 * {@code app_user} bij een interne gebruikersnaam, of de gebruikersnaam zelf als die niet (meer) bestaat
 * (verwijderde gebruikers, "local-profile", mock-users in tests). Gebruikers kiezen en zien sinds 30-10-2026
 * geen gebruikersnaam meer; die is alleen nog de sleutel op pagina's, favorieten en in de audit.
 * De hele (kleine) gebruikerslijst wordt per request één keer geladen en op het request bewaard, zodat een
 * lijstpagina niet per rij een query doet.
 */
@Component("namen")
public class Namen {

	private static final String CACHE = Namen.class.getName() + ".cache";

	private final WikiUserRepository users;

	public Namen(WikiUserRepository users) {
		this.users = users;
	}

	public String weergave(String username) {
		if (username == null || username.isBlank()) {
			return "";
		}
		String name = cache().get(username.toLowerCase(Locale.ROOT));
		return name == null ? username : name;
	}

	/** Eerste letter van de weergavenaam voor de avatar, in hoofdletter. */
	public String initiaal(String username) {
		String name = weergave(username);
		return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> cache() {
		HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
				? attrs.getRequest() : null;
		if (request == null) {
			return load();
		}
		Map<String, String> cached = (Map<String, String>) request.getAttribute(CACHE);
		if (cached == null) {
			cached = load();
			request.setAttribute(CACHE, cached);
		}
		return cached;
	}

	private Map<String, String> load() {
		Map<String, String> names = new HashMap<>();
		for (WikiUser user : users.findAll()) {
			names.put(user.getUsername().toLowerCase(Locale.ROOT), user.getDisplayName());
		}
		return names;
	}

}
