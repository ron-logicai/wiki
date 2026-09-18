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
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploaded video files (extension beyond the MVP, built to the rules of spec U-01): server-side check of
 * type and size, storage outside the public static files, download only through an authorised route.
 * The type is taken from the file's first bytes, never from the browser's claim or the file name.
 */
@Service
public class AttachmentService {

	/** Only files uploaded to this wiki may appear in a document; the id is the whole identity. */
	public static final Pattern INTERNAL_URL = Pattern.compile(
		"^/attachments/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");

	static final Map<String, String> ALLOWED = Map.of("video/mp4", "MP4", "video/webm", "WebM");

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
		long max = properties.maxSize().toBytes();
		if (file.getSize() > max) {
			throw new InvalidContentException("Het bestand is te groot (maximaal " + properties.maxSize().toMegabytes() + " MB).");
		}
		String contentType;
		try (InputStream in = file.getInputStream()) {
			contentType = detectContentType(in.readNBytes(16));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (contentType == null) {
			throw new InvalidContentException("Alleen MP4- en WebM-video's worden ondersteund.");
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

	/** The video type from the first bytes: MP4/MOV family ("ftyp" at offset 4) or WebM (EBML header). */
	static String detectContentType(byte[] head) {
		if (head.length >= 8 && Arrays.equals(Arrays.copyOfRange(head, 4, 8), MP4_MAGIC)) {
			return "video/mp4";
		}
		if (head.length >= 4 && Arrays.equals(Arrays.copyOfRange(head, 0, 4), WEBM_MAGIC)) {
			return "video/webm";
		}
		return null;
	}

	private Path pathOf(UUID id) {
		return Path.of(properties.dir()).toAbsolutePath().normalize().resolve(id.toString());
	}

	/** Keeps only the base name, trimmed to the column width; the name is display-only. */
	private static String safeName(String original) {
		String name = original == null ? "" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).strip();
		if (name.isEmpty()) {
			name = "video";
		}
		return name.length() > 255 ? name.substring(0, 255) : name;
	}

}
