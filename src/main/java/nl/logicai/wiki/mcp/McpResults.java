package nl.logicai.wiki.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.exceptions.InvalidTagException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.exceptions.PageStateException;
import nl.logicai.wiki.exceptions.TemplateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the outcome of a tool into an MCP result. A success is returned twice, as structured content and as
 * the same JSON in a text block, because not every client reads structured content (ChatGPT expects both).
 * A failure becomes an error result with a short code the model can act on; the texts are English because
 * models read them, with the wiki's own (Dutch) message appended where it explains what was wrong.
 */
@Component
@ConditionalOnProperty(name = "wiki.mcp.enabled", havingValue = "true")
public class McpResults {

	private static final Logger log = LoggerFactory.getLogger(McpResults.class);

	private final ObjectMapper mapper;

	public McpResults(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	/** A refusal the tool itself decides on, for example an update that would drop formatting. */
	public static class ToolRefusal extends RuntimeException {

		private final String code;

		public ToolRefusal(String code, String message) {
			super(message);
			this.code = code;
		}

		public String code() {
			return code;
		}

	}

	public CallToolResult run(Supplier<Map<String, Object>> action) {
		try {
			return ok(action.get());
		}
		catch (ToolRefusal e) {
			return error(e.code(), e.getMessage());
		}
		catch (PageConflictException e) {
			return error("CONFLICT", "The page was changed by someone else (current version " + e.getCurrentVersion()
				+ "). Call get_page again, re-apply your change to the current content and retry with baseVersion "
				+ e.getCurrentVersion() + ".");
		}
		catch (ObjectOptimisticLockingFailureException e) {
			return error("CONFLICT", "The page was changed at the same moment. Call get_page again and retry.");
		}
		catch (PageNotFoundException e) {
			return error("NOT_FOUND", "No active page with this id. It may be in the trash or never have existed.");
		}
		catch (TemplateNotFoundException e) {
			return error("NOT_FOUND", "No template with this id. Use list_templates to see the available templates.");
		}
		catch (InvalidContentException | InvalidTagException | PageStateException e) {
			return error("INVALID", e.getMessage());
		}
		catch (IllegalArgumentException e) {
			return error("INVALID", e.getMessage());
		}
		catch (AccessDeniedException e) {
			return error("FORBIDDEN", "Your wiki role does not allow this. Changing pages needs the Editor role.");
		}
		catch (RuntimeException e) {
			// Never leak internals to the client (spec N-09); the log has the details.
			log.error("MCP tool failed", e);
			return error("INTERNAL", "The wiki could not complete this request.");
		}
	}

	private CallToolResult ok(Map<String, Object> value) {
		return CallToolResult.builder()
			.structuredContent(value)
			.addTextContent(mapper.writeValueAsString(value))
			.build();
	}

	private CallToolResult error(String code, String message) {
		return CallToolResult.builder().isError(true).addTextContent(code + ": " + message).build();
	}

	/** An id argument as UUID; a malformed value is the caller's mistake, not a server error. */
	public static UUID uuid(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("'" + name + "' is required.");
		}
		try {
			return UUID.fromString(value.strip());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("'" + name + "' must be a page id (UUID) as returned by the other tools.");
		}
	}

	public static UUID optionalUuid(String value, String name) {
		return value == null || value.isBlank() ? null : uuid(value, name);
	}

	/** An insertion-ordered map without null values, so the JSON has no "field": null noise. */
	public static Map<String, Object> map(Object... keysAndValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
			if (keysAndValues[i + 1] != null) {
				map.put((String) keysAndValues[i], keysAndValues[i + 1]);
			}
		}
		return map;
	}

}
