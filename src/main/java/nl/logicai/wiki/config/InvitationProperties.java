package nl.logicai.wiki.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Inviting new users by e-mail: how long the invitation link is valid ({@code ttl}). */
@ConfigurationProperties(prefix = "wiki.invitation")
public record InvitationProperties(Duration ttl) {
}
