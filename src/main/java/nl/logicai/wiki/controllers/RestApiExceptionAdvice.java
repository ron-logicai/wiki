package nl.logicai.wiki.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import nl.logicai.wiki.exceptions.FormattingLossException;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.exceptions.InvalidTagException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.exceptions.PageStateException;
import nl.logicai.wiki.exceptions.TemplateNotFoundException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * JSON error bodies for the REST API (docs/api.md): {@code {"error": "<melding>", "code": "<CODE>"}}. The
 * code is what a script acts on, the message is for a person. A role that does not allow the change is not
 * handled here: that {@code AccessDeniedException} goes on to the 403 of {@code ApiSecurityConfig}.
 */
@RestControllerAdvice(assignableTypes = PageRestApiController.class)
class RestApiExceptionAdvice {

	@ExceptionHandler(PageNotFoundException.class)
	ResponseEntity<Map<String, Object>> pageNotFound() {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND",
			"Geen actieve pagina met dit id. De pagina staat mogelijk in de prullenbak of heeft nooit bestaan.");
	}

	@ExceptionHandler(TemplateNotFoundException.class)
	ResponseEntity<Map<String, Object>> templateNotFound() {
		return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Geen template met dit id.");
	}

	@ExceptionHandler(PageConflictException.class)
	ResponseEntity<Map<String, Object>> conflict(PageConflictException ex) {
		ResponseEntity<Map<String, Object>> response = error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
		response.getBody().put("currentVersion", ex.getCurrentVersion());
		return response;
	}

	/** Two changes at the same moment: the optimistic lock, or a deadlock between two requests. */
	@ExceptionHandler(ConcurrencyFailureException.class)
	ResponseEntity<Map<String, Object>> concurrent() {
		return error(HttpStatus.CONFLICT, "CONFLICT", "De pagina is op hetzelfde moment gewijzigd. Lees de pagina opnieuw en probeer het nog eens.");
	}

	/** The only state the API runs into: deleting a page that still has active subpages. */
	@ExceptionHandler(PageStateException.class)
	ResponseEntity<Map<String, Object>> state(PageStateException ex) {
		return error(HttpStatus.CONFLICT, "HAS_SUBPAGES", ex.getMessage());
	}

	@ExceptionHandler(FormattingLossException.class)
	ResponseEntity<Map<String, Object>> formattingLoss(FormattingLossException ex) {
		ResponseEntity<Map<String, Object>> response = error(HttpStatus.CONFLICT, "FORMATTING_LOSS", ex.getMessage()
			+ ". Er is niets gewijzigd. Laat die blokken precies zoals je ze las, gebruik append om alleen iets toe te "
			+ "voegen, of herhaal het verzoek met allowFormattingLoss: true.");
		response.getBody().put("losses", ex.getLosses());
		return response;
	}

	@ExceptionHandler({InvalidContentException.class, InvalidTagException.class})
	ResponseEntity<Map<String, Object>> invalid(RuntimeException ex) {
		return error(HttpStatus.BAD_REQUEST, "INVALID", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.map(fieldError -> fieldError.getDefaultMessage())
			.findFirst()
			.orElse("Ongeldig verzoek.");
		return error(HttpStatus.BAD_REQUEST, "INVALID", message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, Object>> unreadable() {
		return error(HttpStatus.BAD_REQUEST, "INVALID", "De body is geen geldige JSON voor dit verzoek.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Map<String, Object>> wrongType(MethodArgumentTypeMismatchException ex) {
		return error(HttpStatus.BAD_REQUEST, "INVALID", "Ongeldige waarde voor '" + ex.getName() + "'.");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException ex) {
		return error(HttpStatus.BAD_REQUEST, "INVALID", "Parameter '" + ex.getParameterName() + "' ontbreekt.");
	}

	private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", message);
		body.put("code", code);
		return ResponseEntity.status(status).body(body);
	}

}
