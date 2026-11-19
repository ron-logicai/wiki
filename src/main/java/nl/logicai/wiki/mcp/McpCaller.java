package nl.logicai.wiki.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who a tool call is made for. The MCP endpoint only lets authenticated requests through, and the
 * principal name is the wiki username, exactly as for the web interface, so revisions and audit events
 * written through MCP carry the same names.
 */
@Component
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpCaller {

	/** The wiki username of the user the agent works for. */
	public String actor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new IllegalStateException("MCP tool called without an authenticated user");
		}
		return authentication.getName();
	}

	/** The name of the connected app (for the audit trail); empty when unknown. */
	public String client() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getDetails() instanceof McpClientDetails details
			? details.clientName() : "";
	}

	/** Set as authentication details by the bearer token converter. */
	public record McpClientDetails(String clientId, String clientName) {
	}

}
