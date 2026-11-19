package nl.logicai.wiki.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import nl.logicai.wiki.config.McpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The limits on open client registration for the MCP server (docs/mcp.md). */
class McpClientRegistrationPolicyTest {

	private final JdbcOperations jdbc = mock(JdbcOperations.class);

	private McpClientRegistrationPolicy policy(int maxClients, int perHour) {
		var properties = new McpProperties(true, "", "", Duration.ofMinutes(30), Duration.ofDays(30),
			List.of("https://claude.ai/api/mcp/auth_callback", "https://chatgpt.com/connector/oauth/*", "http://localhost"),
			maxClients, perHour);
		return new McpClientRegistrationPolicy(properties, jdbc, Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void redirectUrisMustBeOnTheAllowlist() {
		McpClientRegistrationPolicy policy = policy(200, 10);

		assertThat(policy.isAllowedRedirectUri("https://claude.ai/api/mcp/auth_callback")).isTrue();
		assertThat(policy.isAllowedRedirectUri("https://chatgpt.com/connector/oauth/abc_123")).isTrue();
		// Loopback: any port and path, but only the host that is listed, and only plain http without user info.
		assertThat(policy.isAllowedRedirectUri("http://localhost:3118/callback")).isTrue();
		assertThat(policy.isAllowedRedirectUri("http://127.0.0.1:3118/callback")).isFalse();
		assertThat(policy.isAllowedRedirectUri("http://user@localhost:3118/callback")).isFalse();
		assertThat(policy.isAllowedRedirectUri("http://localhost.evil.example/callback")).isFalse();

		assertThat(policy.isAllowedRedirectUri("https://claude.ai/api/mcp/auth_callback/extra")).isFalse();
		assertThat(policy.isAllowedRedirectUri("https://claude.ai.evil.example/api/mcp/auth_callback")).isFalse();
		assertThat(policy.isAllowedRedirectUri("https://chatgpt.com/connector/oauth/")).isFalse();
		assertThat(policy.isAllowedRedirectUri("https://chatgpt.com/connector/oauth/../../evil")).isFalse();
		assertThat(policy.isAllowedRedirectUri("https://chatgpt.com/connector/oauth/a?next=https://evil.example")).isFalse();
		assertThat(policy.isAllowedRedirectUri("https://evil.example/callback")).isFalse();
		assertThat(policy.isAllowedRedirectUri("javascript:alert(1)")).isFalse();
		assertThat(policy.isAllowedRedirectUri(null)).isFalse();
	}

	@Test
	void oneAddressRegistersOnlySoOftenPerHour() {
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
		McpClientRegistrationPolicy policy = policy(200, 3);

		policy.throttle();
		policy.throttle();
		policy.throttle();

		assertThatThrownBy(policy::throttle).isInstanceOf(OAuth2AuthenticationException.class)
			.hasMessageContaining("Too many client registrations");
	}

	@Test
	void noRegistrationsBeyondTheMaximumNumberOfClients() {
		when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(200);

		assertThatThrownBy(policy(200, 10)::throttle).isInstanceOf(OAuth2AuthenticationException.class)
			.hasMessageContaining("no new client registrations");
	}

}
