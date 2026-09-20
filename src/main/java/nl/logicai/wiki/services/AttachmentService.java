package nl.logicai.wiki.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import nl.logicai.wiki.config.AttachmentProperties;
import nl.logicai.wiki.exceptions.AttachmentNotFoundException;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.models.Attachment;
import nl.logicai.wiki.repositories.AttachmentRepository;
import nl.logicai.wiki.repositories.PageRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploaded files (spec U-01): PNG, JPEG and PDF of at most {@code wiki.attachments.max-size}, plus MP4/WebM
 * video as an extension with its own limit. The server checks type and size, stores the bytes outside the
 * public static files and serves them only through an authorised route. The type is taken from the file's
 * first bytes, never from the browser's claim or the file name, so SVG, HTML and other active formats can
 * never get in under a different name.
 */
@Service
public class AttachmentService {

	/** Only files uploaded to this wiki may appear in a document; the id is the whole identity. */
	public static final Pattern INTERNAL_URL = Pattern.compile(
		"^/attachments/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");

	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
	private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] WEBM_MAGIC = {(byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
	private static final byte[] MP4_MAGIC = "ftyp".getBytes(StandardCharsets.US_ASCII);

	private final AttachmentRepository attachments;
	private final PageRepository pages;
	private final AttachmentProperties properties;
	private final Clock clock;

	public AttachmentService(AttachmentRepository attachments, PageRepository pages,
			AttachmentProperties properties, Clock clock) {
		this.attachments = attachments;
		this.pages = pages;
		this.properties = properties;
		this.clock = clock;
	}

	/** A stored file: its metadata and a readable handle on the bytes. */
	public record Stored(Attachment attachment, Resource file) {
	}

	@PreAuthorize("hasRole('EDITOR')")
	@Transactional
	public Attachment store(MultipartFile file, UUID pageId, String actor) {
		if (file == null || file.isEmpty()) {
			throw new InvalidContentException("Kies een bestand om te uploaden.");
		}
		String contentType;
		try (InputStream in = file.getInputStream()) {
			contentType = detectContentType(in.readNBytes(16));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (contentType == null) {
			throw new InvalidContentException("Alleen PNG, JPEG en PDF (en MP4- of WebM-video) worden ondersteund.");
		}
		DataSize max = isVideo(contentType) ? properties.maxVideoSize() : properties.maxSize();
		if (file.getSize() > max.toBytes()) {
			throw new InvalidContentException((isVideo(contentType) ? "Een video" : "Een afbeelding of PDF")
				+ " mag maximaal " + max.toMegabytes() + " MB groot zijn.");
		}
		if (pageId != null && pages.findByIdAndDeletedAtIsNull(pageId).isEmpty()) {
			throw new InvalidContentException("De pagina voor deze upload bestaat niet.");
		}

		UUID id = UUID.randomUUID();
		Path target = pathOf(id);
		try {
			Files.createDirectories(target.getParent());
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
			Attachment attachment = Attachment.create(id, pageId, safeName(file.getOriginalFilename()), contentType,
				Files.size(target), actor, clock.instant());
			return attachments.saveAndFlush(attachment);
		}
		catch (IOException | RuntimeException ex) {
			try {
				Files.deleteIfExists(target); // no orphan file when the row could not be written
			}
			catch (IOException ignored) {
				// the original problem is what the caller needs to see
			}
			if (ex instanceof IOException io) {
				throw new UncheckedIOException(io);
			}
			throw (RuntimeException) ex;
		}
	}

	@Transactional(readOnly = true)
	public Stored open(UUID id) {
		Attachment attachment = attachments.findById(id).orElseThrow(() -> new AttachmentNotFoundException(id));
		Path path = pathOf(id);
		if (!Files.isRegularFile(path)) {
			throw new AttachmentNotFoundException(id);
		}
		return new Stored(attachment, new FileSystemResource(path));
	}

	/** True for {@code /attachments/{uuid}}, the only form of file URL a document may contain. */
	public static boolean isInternalUrl(String url) {
		return url != null && INTERNAL_URL.matcher(url).matches();
	}

	static boolean isVideo(String contentType) {
		return contentType.startsWith("video/");
	}

	/**
	 * The type from the first bytes: PNG and JPEG signatures, {@code %PDF-}, the MP4/MOV family ("ftyp" at
	 * offset 4) or WebM (EBML header). Anything else, including SVG and HTML, is null and refused.
	 */
	static String detectContentType(byte[] head) {
		if (startsWith(head, 0, PNG_MAGIC)) {
			return "image/png";
		}
		if (startsWith(head, 0, JPEG_MAGIC)) {
			return "image/jpeg";
		}
		if (startsWith(head, 0, PDF_MAGIC)) {
			return "application/pdf";
		}
		if (startsWith(head, 4, MP4_MAGIC)) {
			return "video/mp4";
		}
		if (startsWith(head, 0, WEBM_MAGIC)) {
			return "video/webm";
		}
		return null;
	}

	private static boolean startsWith(byte[] head, int offset, byte[] magic) {
		return head.length >= offset + magic.length
			&& Arrays.equals(head, offset, offset + magic.length, magic, 0, magic.length);
	}

	private Path pathOf(UUID id) {
		return Path.of(properties.dir()).toAbsolutePath().normalize().resolve(id.toString());
	}

	/** Keeps only the base name, trimmed to the column width; the name is display-only. */
	private static String safeName(String original) {
		String name = original == null ? "" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).strip();
		if (name.isEmpty()) {
			name = "bestand";
		}
		return name.length() > 255 ? name.substring(0, 255) : name;
	}

}
