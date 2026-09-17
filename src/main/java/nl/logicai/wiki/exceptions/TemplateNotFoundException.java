package nl.logicai.wiki.exceptions;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Unknown template id: HTTP 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TemplateNotFoundException extends RuntimeException {

	public TemplateNotFoundException(UUID id) {
		super("Template " + id + " bestaat niet.");
	}

}
