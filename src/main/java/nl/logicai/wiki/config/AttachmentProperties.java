package nl.logicai.wiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where uploaded files live and how large one may be. Values come from {@code wiki.attachments.*} in
 * application.yaml; the directory must be outside the public static files and on a persistent volume.
 *
 * @param dir          storage directory for the bytes (the row in {@code attachment} holds the metadata)
 * @param maxSize      limit for images and PDF (spec U-01: 10 MB by default)
 * @param maxVideoSize limit for MP4/WebM video, an extension beyond U-01; must not exceed
 *                     {@code spring.servlet.multipart.max-file-size}
 */
@ConfigurationProperties(prefix = "wiki.attachments")
public record AttachmentProperties(String dir, DataSize maxSize, DataSize maxVideoSize) {
}
