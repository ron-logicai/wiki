package nl.logicai.wiki.controllers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import nl.logicai.wiki.services.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves an uploaded file to a logged-in user (spec N-01: every route is authorised server-side).
 * Spring MVC answers Range requests on the Resource with 206, which is what the browser's video
 * player needs to seek.
 */
@Controller
public class AttachmentController {

	private final AttachmentService attachments;

	public AttachmentController(AttachmentService attachments) {
		this.attachments = attachments;
	}

	@GetMapping("/attachments/{id}")
	public ResponseEntity<Resource> download(@PathVariable UUID id) {
		AttachmentService.Stored stored = attachments.open(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(stored.attachment().getContentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
				.filename(stored.attachment().getFileName(), StandardCharsets.UTF_8).build().toString())
			.header(HttpHeaders.ACCEPT_RANGES, "bytes")
			.header("X-Content-Type-Options", "nosniff")
			// The id is immutable and the bytes never change, so the browser may keep them for itself.
			.cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
			.body(stored.file());
	}

}
