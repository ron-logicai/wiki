package nl.logicai.wiki.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "Wachtwoord vergeten": how long a reset link is valid ({@code ttl}) and how long a user waits before
 * a new link is sent ({@code cooldown}, against mail bombing one address).
 */
@ConfigurationProperties(prefix = "wiki.password-reset")
public record PasswordResetProperties(Duration ttl, Duration cooldown) {
}
