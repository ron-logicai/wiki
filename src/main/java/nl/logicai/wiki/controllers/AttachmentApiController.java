package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.models.Attachment;
import nl.logicai.wiki.services.AttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload endpoint for the editor's file picker. Answers 201 with the URL the document must use,
 * 400 for an unsupported type, 413 when too large, 401 without a session, 403 without editor rights.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentApiController {

	public record UploadResponse(String url, String name, String contentType, long size) {
	}

	private final AttachmentService attachments;

	public AttachmentApiController(AttachmentService attachments) {
		this.attachments = attachments;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse upload(@RequestParam("file") MultipartFile file,
			@RequestParam(required = false) UUID pageId, Authentication auth) {
		Attachment stored = attachments.store(file, pageId, auth.getName());
		return new UploadResponse(stored.getUrl(), stored.getFileName(), stored.getContentType(), stored.getSizeBytes());
	}

}
