package nl.logicai.wiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit input limits (spec N-05). Values come from {@code wiki.limits.*} in application.yaml.
 */
@ConfigurationProperties(prefix = "wiki.limits")
public record WikiLimits(int titleMaxLength, long documentMaxBytes) {
}
