package nl.logicai.wiki.exceptions;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Unknown or deleted page: HTTP 404 (spec section 5). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PageNotFoundException extends RuntimeException {

	public PageNotFoundException(UUID id) {
		super("Pagina niet gevonden: " + id);
	}

}
