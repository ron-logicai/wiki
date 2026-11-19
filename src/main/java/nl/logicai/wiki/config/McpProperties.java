package nl.logicai.wiki.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the MCP server for AI agents ({@code wiki.mcp.*} in application.yaml; see docs/mcp.md).
 * The whole feature is off unless {@code enabled} is true: no {@code /mcp} endpoint, no OAuth endpoints.
 *
 * @param enabled             switches the MCP endpoint and the OAuth authorization server on
 * @param issuer              OAuth issuer; empty means {@code wiki.base-url}
 * @param signingKey          RSA private key (PKCS8 PEM) that signs access tokens; empty means a key that
 *                            only lives as long as the process (local use and tests)
 * @param accessTokenTtl      lifetime of an access token
 * @param refreshTokenTtl     lifetime of a refresh token; every use rotates it
 * @param allowedRedirectUris redirect URIs a dynamically registered client may use; loopback addresses
 *                            ({@code http://localhost}, {@code http://127.0.0.1}) match on any port
 * @param maxClients          upper bound on dynamically registered clients
 * @param maxRegistrationsPerHour how many clients one address may register per hour
 */
@ConfigurationProperties(prefix = "wiki.mcp")
public record McpProperties(boolean enabled, String issuer, String signingKey, Duration accessTokenTtl,
		Duration refreshTokenTtl, List<String> allowedRedirectUris, int maxClients, int maxRegistrationsPerHour) {
}
