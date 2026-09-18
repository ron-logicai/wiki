package nl.logicai.wiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where uploaded files live and how large one may be. Values come from {@code wiki.attachments.*} in
 * application.yaml; the directory must be outside the public static files and on a persistent volume.
 */
@ConfigurationProperties(prefix = "wiki.attachments")
public record AttachmentProperties(String dir, DataSize maxSize) {
}
