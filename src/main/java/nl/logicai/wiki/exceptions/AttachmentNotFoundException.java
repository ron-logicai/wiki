package nl.logicai.wiki.exceptions;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Unknown attachment, or its file is missing on disk: HTTP 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AttachmentNotFoundException extends RuntimeException {

	public AttachmentNotFoundException(UUID id) {
		super("Bijlage niet gevonden: " + id);
	}

}
