package nl.logicai.wiki.controllers;

import java.util.Map;

import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.exceptions.PageConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** JSON error bodies for the editor endpoints: 409 on a stale version, 400 on invalid content. */
@RestControllerAdvice(assignableTypes = {PageApiController.class, TemplateApiController.class})
class ApiExceptionAdvice {

	@ExceptionHandler(PageConflictException.class)
	ResponseEntity<Map<String, Object>> conflict(PageConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(Map.of("error", ex.getMessage(), "currentVersion", ex.getCurrentVersion()));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<Map<String, Object>> lockingConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(Map.of("error", "De inhoud is intussen door iemand anders gewijzigd."));
	}

	@ExceptionHandler(InvalidContentException.class)
	ResponseEntity<Map<String, Object>> invalid(InvalidContentException ex) {
		return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> invalidRequest(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.findFirst()
			.orElse("Ongeldig verzoek.");
		return ResponseEntity.badRequest().body(Map.of("error", message));
	}

}
