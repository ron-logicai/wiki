package nl.logicai.wiki.security;

import java.util.List;

import nl.logicai.wiki.mcp.McpCaller.McpClientDetails;
import nl.logicai.wiki.models.WikiUser;
import nl.logicai.wiki.repositories.WikiUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a valid access token into the wiki user it was issued to, on every MCP request (spec F-02, N-01).
 * A signature and an expiry date are not enough here: the user is loaded from {@code app_user}, so a
 * deactivated or deleted user is refused at once and a role change applies at once (the token carries no
 * role), and the connection the token belongs to must still exist, so revoking a connection or changing a
 * password cuts an agent off immediately instead of when its token expires.
 *
 * <p>The principal name is the wiki username, as everywhere else, so {@code @PreAuthorize}, revisions and
 * audit events work exactly as for a browser session. This is the bearer-token counterpart of
 * {@link ActiveUserFilter}.
 */
public class WikiJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	/** The id of the connection (oauth2_authorization row) a token belongs to. */
	public static final String AUTHORIZATION_ID_CLAIM = "wiki_aid";
	/** The name of the connected app, for the audit trail. */
	public static final String CLIENT_NAME_CLAIM = "wiki_client";

	private final WikiUserRepository users;
	private final JdbcOperations jdbc;

	public WikiJwtAuthenticationConverter(WikiUserRepository users, JdbcOperations jdbc) {
		this.users = users;
		this.jdbc = jdbc;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String subject = jwt.getSubject();
		WikiUser user = subject == null ? null : users.findByUsernameIgnoreCase(subject).orElse(null);
		if (user == null || !user.isActive()) {
			throw new InvalidBearerTokenException("The user of this token is no longer active");
		}
		String authorizationId = jwt.getClaimAsString(AUTHORIZATION_ID_CLAIM);
		Integer connections = authorizationId == null ? Integer.valueOf(0)
			: jdbc.queryForObject("select count(*) from oauth2_authorization where id = ?", Integer.class, authorizationId);
		if (connections == null || connections == 0) {
			throw new InvalidBearerTokenException("This connection was revoked");
		}
		var token = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(user.getRole().authority())),
			user.getUsername());
		String clientName = jwt.getClaimAsString(CLIENT_NAME_CLAIM);
		token.setDetails(new McpClientDetails(jwt.getClaimAsString("client_id"), clientName == null ? "" : clientName));
		return token;
	}

}
