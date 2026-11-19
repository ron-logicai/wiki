package nl.logicai.wiki.services;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.repositories.AuditEventRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The connections between wiki users and AI agents (docs/mcp.md): which app works for which user, and
 * revoking them. A connection is a row in {@code oauth2_authorization} that Spring's authorization server
 * wrote when the user gave consent; this service only reads and deletes those rows, with plain JDBC, so it
 * also works (and finds nothing) while the MCP server is switched off.
 *
 * <p>Deleting the row is what revokes: {@code WikiJwtAuthenticationConverter} checks on every MCP request
 * that the row of the token still exists, and without the row the refresh token is unknown. The consent
 * goes too, so connecting the same app again asks the user again.
 */
@Service
@Transactional
public class McpConnectionService {

	/**
	 * @param id            the connection (authorization) id
	 * @param username      the wiki user the app works for
	 * @param appNaam       the name the app registered with; chosen by the app, so not proof of who it is
	 * @param hosts         where the app sends the user back to after login, e.g. {@code claude.ai}
	 * @param gekoppeldOp   when the user gave consent
	 * @param laatstVernieuwd when the app last fetched a new access token (at most half an hour before its last use)
	 * @param verlooptOp    when the connection ends if the app does not come back
	 */
	public record Koppeling(String id, String username, String appNaam, List<String> hosts, Instant gekoppeldOp,
			Instant laatstVernieuwd, Instant verlooptOp) {
	}

	private static final String SELECT = """
		select a.id, a.principal_name, c.client_name, c.redirect_uris, a.authorization_code_issued_at,
		       a.access_token_issued_at, a.refresh_token_expires_at, a.access_token_expires_at
		from oauth2_authorization a join oauth2_registered_client c on c.id = a.registered_client_id
		where a.access_token_value is not null
		""";

	private final JdbcOperations jdbc;
	private final AuditEventRepository audit;
	private final Clock clock;

	public McpConnectionService(JdbcOperations jdbc, AuditEventRepository audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** The apps connected to this user's account, newest first. */
	public List<Koppeling> koppelingenVan(String username) {
		purgeExpired();
		return jdbc.query(SELECT + " and a.principal_name = ? order by a.access_token_issued_at desc",
			McpConnectionService::koppeling, username);
	}

	/** Every connection of every user, for the admin overview. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<Koppeling> alleKoppelingen() {
		purgeExpired();
		return jdbc.query(SELECT + " order by a.principal_name, a.access_token_issued_at desc", McpConnectionService::koppeling);
	}

	/** Revokes one of the user's own connections. Someone else's id changes nothing. */
	public boolean intrekken(String id, String username) {
		return revoke(id, username, username);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean intrekkenAlsBeheerder(String id, String actor) {
		return revoke(id, null, actor);
	}

	/**
	 * Cuts off every agent of a user. Called when the account is deleted or deactivated and when its password
	 * changes: whoever knew the old password may have connected an agent with it.
	 */
	public int revokeAllFor(String username, String actor) {
		int removed = jdbc.update("delete from oauth2_authorization where principal_name = ?", username);
		jdbc.update("delete from oauth2_authorization_consent where principal_name = ?", username);
		if (removed > 0) {
			audit.save(AuditEvent.of(AuditEvent.MCP_REVOKE, null, actor, clock.instant(), "alle koppelingen van " + username));
		}
		return removed;
	}

	/**
	 * Called when a connection is established: removes what the same user left behind on the way, such as
	 * an authorization request that was never finished.
	 */
	public void onConnected(String username, String authorizationId) {
		jdbc.update("delete from oauth2_authorization where principal_name = ? and access_token_value is null and id <> ?",
			username, authorizationId);
	}

	private boolean revoke(String id, String owner, String actor) {
		List<String[]> rows = jdbc.query("""
			select a.principal_name, a.registered_client_id, c.client_name
			from oauth2_authorization a join oauth2_registered_client c on c.id = a.registered_client_id
			where a.id = ?
			""", (rs, n) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)}, id);
		if (rows.isEmpty() || (owner != null && !owner.equals(rows.get(0)[0]))) {
			return false;
		}
		String username = rows.get(0)[0];
		String clientId = rows.get(0)[1];
		jdbc.update("delete from oauth2_authorization where id = ?", id);
		Integer left = jdbc.queryForObject(
			"select count(*) from oauth2_authorization where principal_name = ? and registered_client_id = ?",
			Integer.class, username, clientId);
		if (left == null || left == 0) {
			jdbc.update("delete from oauth2_authorization_consent where principal_name = ? and registered_client_id = ?",
				username, clientId);
		}
		audit.save(AuditEvent.of(AuditEvent.MCP_REVOKE, null, actor, clock.instant(), rows.get(0)[2] + " van " + username));
		return true;
	}

	/** Connections whose refresh token ran out, and authorization codes nobody collected. */
	private void purgeExpired() {
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
			delete from oauth2_authorization
			where (refresh_token_value is not null and refresh_token_expires_at < ?)
			   or (refresh_token_value is null and access_token_value is not null and access_token_expires_at < ?)
			   or (access_token_value is null and authorization_code_value is not null and authorization_code_expires_at < ?)
			""", now, now, now);
	}

	private static Koppeling koppeling(ResultSet rs, int row) throws SQLException {
		Instant refreshExpires = instant(rs, 7);
		return new Koppeling(rs.getString(1), rs.getString(2), rs.getString(3), hosts(rs.getString(4)), instant(rs, 5),
			instant(rs, 6), refreshExpires != null ? refreshExpires : instant(rs, 8));
	}

	private static Instant instant(ResultSet rs, int column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	/** {@code redirect_uris} is a comma-separated list; the host says more to a person than the whole URI. */
	static List<String> hosts(String redirectUris) {
		if (redirectUris == null || redirectUris.isBlank()) {
			return List.of();
		}
		return Arrays.stream(redirectUris.split(","))
			.map(String::strip)
			.map(uri -> {
				try {
					String host = URI.create(uri).getHost();
					return host == null ? uri : host;
				}
				catch (IllegalArgumentException e) {
					return uri;
				}
			})
			.distinct()
			.sorted()
			.toList();
	}

}
