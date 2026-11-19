package nl.logicai.wiki.mcp;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The MCP tools over the real endpoint: JSON-RPC requests to {@code /mcp}, answered by the same services
 * the web interface uses.
 */
@SpringBootTest(properties = "wiki.mcp.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockUser(username = "editor", roles = "EDITOR")
class McpToolsIntegrationTest {

	private static final String PARAGRAPH = """
		[{"id":"b1","type":"paragraph","props":{},"content":[{"type":"text","text":"Zoekwoord %s in een alinea","styles":{}}],"children":[]}]""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void listsTheToolsWithReadOnlyHints() throws Exception {
		JsonNode tools = rpc("tools/list", "{}").path("result").path("tools");

		assertThat(tools.findValuesAsString("name"))
			.contains("search", "fetch", "search_pages", "get_page", "list_pages", "list_recent_changes",
				"list_tags", "list_templates", "create_page", "update_page", "append_to_page", "add_tag", "remove_tag");
		JsonNode search = toolNamed(tools, "search");
		assertThat(search.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
		assertThat(search.path("inputSchema").path("properties").has("query")).isTrue();
		JsonNode update = toolNamed(tools, "update_page");
		assertThat(update.path("annotations").path("readOnlyHint").asBoolean()).isFalse();
		assertThat(update.path("annotations").path("destructiveHint").asBoolean()).isTrue();
		assertThat(update.path("inputSchema").path("required").valueStream().map(JsonNode::asString))
			.containsExactlyInAnyOrder("id", "baseVersion", "markdown");
	}

	@Test
	void createsAPageFromMarkdownWithTagsAndAnAuditEvent() throws Exception {
		String marker = "nieuw" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String arguments = mapper.writeValueAsString(java.util.Map.of(
			"title", "Agent " + marker,
			"markdown", "# Agent " + marker + "\n\n## Doel\n\nTekst met **vet** en een [link](http://localhost:8080/pages/abc).\n\n- een\n- twee\n",
			"tags", java.util.List.of("mcp-" + marker)));

		JsonNode created = call("create_page", arguments);

		assertThat(created.path("isError").asBoolean(false)).isFalse();
		JsonNode page = created.path("structuredContent");
		UUID id = UUID.fromString(page.path("id").asString());
		assertThat(page.path("version").asLong()).isEqualTo(1);
		assertThat(page.path("status").asString()).isEqualTo("Concept");
		assertThat(page.path("tags").valueStream().map(JsonNode::asString)).containsExactly("mcp-" + marker);

		// The repeated title heading is dropped, the internal link is stored relative, and the page reads back the same.
		JsonNode stored = mapper.readTree(jdbc.queryForObject("select document from page where id = ?", String.class, id.toString()));
		assertThat(stored.valueStream().map(block -> block.path("type").asString()))
			.containsExactly("heading", "paragraph", "bulletListItem", "bulletListItem");
		assertThat(stored.get(1).path("content").findValuesAsString("href")).containsExactly("/pages/abc");
		assertThat(call("get_page", "{\"id\":\"" + id + "\"}").path("structuredContent").path("markdown").asString())
			.isEqualTo("## Doel\n\nTekst met **vet** en een [link](http://localhost:8080/pages/abc).\n\n- een\n- twee\n");

		assertThat(jdbc.queryForList("select action, actor, details from audit_event where page_id = ?", id.toString()))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.get("action")).isEqualTo("page.mcp.create");
				assertThat(row.get("actor")).isEqualTo("editor");
				assertThat(row.get("details")).isEqualTo("via MCP");
			});
	}

	@Test
	void updateNeedsTheCurrentVersionAndAppendLeavesExistingContentAlone() throws Exception {
		String marker = "wijzig" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID id = createPage("Pagina " + marker, PARAGRAPH.formatted(marker));

		JsonNode stale = call("update_page", "{\"id\":\"" + id + "\",\"baseVersion\":0,\"markdown\":\"Overschreven\"}");
		assertThat(stale.path("isError").asBoolean()).isTrue();
		assertThat(stale.path("content").path(0).path("text").asString())
			.startsWith("CONFLICT").contains("baseVersion 1");

		JsonNode updated = call("update_page", "{\"id\":\"" + id + "\",\"baseVersion\":1,\"title\":\"Hernoemd " + marker
			+ "\",\"markdown\":\"Zoekwoord " + marker + " in een alinea\\n\\nTweede alinea\"}");
		assertThat(updated.path("isError").asBoolean(false)).isFalse();
		assertThat(updated.path("structuredContent").path("version").asLong()).isEqualTo(2);
		assertThat(updated.path("structuredContent").path("title").asString()).isEqualTo("Hernoemd " + marker);

		JsonNode appended = call("append_to_page", "{\"id\":\"" + id + "\",\"baseVersion\":2,\"markdown\":\"## Toegevoegd\"}");
		assertThat(appended.path("structuredContent").path("version").asLong()).isEqualTo(3);

		// The first block was not changed by either call, so it is still the stored block with its own id.
		JsonNode stored = mapper.readTree(jdbc.queryForObject("select document from page where id = ?", String.class, id.toString()));
		assertThat(stored.valueStream().map(block -> block.path("type").asString()))
			.containsExactly("paragraph", "paragraph", "heading");
		assertThat(stored.get(0).path("id").asString()).isEqualTo("b1");
		assertThat(jdbc.queryForList("select details from audit_event where page_id = ? and action = 'page.mcp.update' order by details",
			String.class, id.toString())).containsExactly("via MCP, revisie 3", "via MCP, revisie 4");

		// Sending the same content again is not a change: no new version, no revision.
		JsonNode same = call("update_page", "{\"id\":\"" + id + "\",\"baseVersion\":3,\"markdown\":"
			+ mapper.writeValueAsString(call("get_page", "{\"id\":\"" + id + "\"}").path("structuredContent").path("markdown").asString()) + "}");
		assertThat(same.path("structuredContent").path("version").asLong()).isEqualTo(3);
	}

	@Test
	void updateRefusesToDropFormattingUnlessAllowed() throws Exception {
		String marker = "kleur" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID id = createPage("Pagina " + marker, """
			[{"id":"rood","type":"paragraph","props":{},"content":[{"type":"text","text":"Let op %s","styles":{"textColor":"red"}}],"children":[]},
			 {"id":"gewoon","type":"paragraph","props":{},"content":[{"type":"text","text":"Gewone alinea","styles":{}}],"children":[]}]
			""".formatted(marker));

		JsonNode read = call("get_page", "{\"id\":\"" + id + "\"}").path("structuredContent");
		assertThat(read.path("warnings").get(0).asString()).startsWith("Block 1 (paragraph \"Let op " + marker + "\"): text colour");

		// Changing only the plain paragraph keeps the coloured block as stored.
		JsonNode harmless = call("update_page", "{\"id\":\"" + id + "\",\"baseVersion\":1,\"markdown\":\"Let op " + marker + "\\n\\nAangepaste alinea\"}");
		assertThat(harmless.path("isError").asBoolean(false)).isFalse();
		JsonNode kept = mapper.readTree(jdbc.queryForObject("select document from page where id = ?", String.class, id.toString()));
		assertThat(kept.get(0).path("id").asString()).isEqualTo("rood");
		assertThat(kept.get(0).path("content").get(0).path("styles").path("textColor").asString()).isEqualTo("red");

		JsonNode refused = call("update_page", "{\"id\":\"" + id + "\",\"baseVersion\":2,\"markdown\":\"Ander begin\\n\\nAangepaste alinea\"}");
		assertThat(refused.path("isError").asBoolean()).isTrue();
		assertThat(refused.path("content").path(0).path("text").asString()).startsWith("FORMATTING_LOSS").contains("text colour");
		assertThat(jdbc.queryForObject("select lock_version from page where id = ?", Long.class, id.toString())).isEqualTo(2);

		JsonNode allowed = call("update_page", "{\"id\":\"" + id + "\",\"baseVersion\":2,\"allowFormattingLoss\":true,\"markdown\":\"Ander begin\\n\\nAangepaste alinea\"}");
		assertThat(allowed.path("structuredContent").path("version").asLong()).isEqualTo(3);
	}

	@Test
	void viewerCanReadButNotWrite() throws Exception {
		String marker = "kijk" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID id = createPage("Pagina " + marker, PARAGRAPH.formatted(marker));
		var viewer = user("viewer").roles("VIEWER");

		assertThat(callAs(viewer, "get_page", "{\"id\":\"" + id + "\"}").path("isError").asBoolean(false)).isFalse();
		for (String attempt : new String[] {
				"create_page|{\"title\":\"Verboden " + marker + "\"}",
				"update_page|{\"id\":\"" + id + "\",\"baseVersion\":1,\"markdown\":\"x\"}",
				"append_to_page|{\"id\":\"" + id + "\",\"baseVersion\":1,\"markdown\":\"x\"}",
				"add_tag|{\"id\":\"" + id + "\",\"tag\":\"x\"}",
				"remove_tag|{\"id\":\"" + id + "\",\"tag\":\"x\"}"}) {
			String[] parts = attempt.split("\\|", 2);
			JsonNode refused = callAs(viewer, parts[0], parts[1]);
			assertThat(refused.path("isError").asBoolean()).as(parts[0]).isTrue();
			assertThat(refused.path("content").path(0).path("text").asString()).as(parts[0]).startsWith("FORBIDDEN");
		}
		assertThat(jdbc.queryForObject("select count(*) from page where title = ?", Long.class, "Verboden " + marker)).isZero();
		assertThat(jdbc.queryForObject("select lock_version from page where id = ?", Long.class, id.toString())).isEqualTo(1);
	}

	@Test
	void tagsAreAddedAndRemovedByName() throws Exception {
		String marker = "tag" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID id = createPage("Pagina " + marker, PARAGRAPH.formatted(marker));

		JsonNode added = call("add_tag", "{\"id\":\"" + id + "\",\"tag\":\"Label " + marker + "\"}");
		assertThat(added.path("structuredContent").path("tags").valueStream().map(JsonNode::asString))
			.containsExactly("Label " + marker);

		JsonNode removed = call("remove_tag", "{\"id\":\"" + id + "\",\"tag\":\"label " + marker + "\"}");
		assertThat(removed.path("structuredContent").path("tags").isEmpty()).isTrue();

		JsonNode invalid = call("add_tag", "{\"id\":\"" + id + "\",\"tag\":\" \"}");
		assertThat(invalid.path("content").path(0).path("text").asString()).isEqualTo("INVALID: Een tagnaam is verplicht.");
	}

	@Test
	void searchAndFetchHaveTheShapeChatGptExpects() throws Exception {
		String marker = "mcp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID id = createPage("Pagina " + marker, PARAGRAPH.formatted(marker));

		JsonNode found = call("search", "{\"query\":\"" + marker + "\"}");
		assertThat(found.path("isError").asBoolean(false)).isFalse();
		JsonNode first = found.path("structuredContent").path("results").path(0);
		assertThat(first.path("id").asString()).isEqualTo(id.toString());
		assertThat(first.path("title").asString()).isEqualTo("Pagina " + marker);
		assertThat(first.path("url").asString()).isEqualTo("http://localhost:8080/pages/" + id);
		// The same JSON once more as text, for clients that do not read structured content.
		assertThat(mapper.readTree(found.path("content").path(0).path("text").asString()))
			.isEqualTo(found.path("structuredContent"));

		JsonNode page = call("fetch", "{\"id\":\"" + id + "\"}").path("structuredContent");
		assertThat(page.path("text").asString()).isEqualTo("Zoekwoord " + marker + " in een alinea\n");
		assertThat(page.path("url").asString()).isEqualTo("http://localhost:8080/pages/" + id);
		assertThat(page.path("metadata").path("version").asString()).isEqualTo("1");
	}

	@Test
	void getPageReturnsMetadataAndMarkdown() throws Exception {
		String marker = "meta" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID id = createPage("Pagina " + marker, PARAGRAPH.formatted(marker));

		JsonNode page = call("get_page", "{\"id\":\"" + id + "\"}").path("structuredContent");

		assertThat(page.path("title").asString()).isEqualTo("Pagina " + marker);
		assertThat(page.path("version").asLong()).isEqualTo(1);
		assertThat(page.path("revision").asInt()).isEqualTo(2);
		assertThat(page.path("status").asString()).isEqualTo("Concept");
		assertThat(page.path("markdown").asString()).contains("Zoekwoord " + marker);
		assertThat(page.path("children").isArray()).isTrue();
	}

	@Test
	void unknownAndMalformedIdsAreToolErrors() throws Exception {
		JsonNode missing = call("get_page", "{\"id\":\"" + UUID.randomUUID() + "\"}");
		assertThat(missing.path("isError").asBoolean()).isTrue();
		assertThat(missing.path("content").path(0).path("text").asString()).startsWith("NOT_FOUND");

		JsonNode malformed = call("get_page", "{\"id\":\"geen-uuid\"}");
		assertThat(malformed.path("isError").asBoolean()).isTrue();
		assertThat(malformed.path("content").path(0).path("text").asString()).startsWith("INVALID");
	}

	private static JsonNode toolNamed(JsonNode tools, String name) {
		for (JsonNode tool : tools.values()) {
			if (name.equals(tool.path("name").asString())) {
				return tool;
			}
		}
		throw new AssertionError("No tool named " + name);
	}

	/** The result of a tools/call. */
	private JsonNode call(String tool, String arguments) throws Exception {
		return callAs(null, tool, arguments);
	}

	private JsonNode callAs(RequestPostProcessor user, String tool, String arguments) throws Exception {
		return rpc(user, "tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}").path("result");
	}

	private JsonNode rpc(String method, String params) throws Exception {
		return rpc(null, method, params);
	}

	private JsonNode rpc(RequestPostProcessor user, String method, String params) throws Exception {
		MvcResult result = mcp(user, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}")
			.andExpect(status().isOk())
			.andReturn();
		return mapper.readTree(result.getResponse().getContentAsString());
	}

	private ResultActions mcp(RequestPostProcessor user, String body) throws Exception {
		// No CSRF token: /mcp is the bearer-token endpoint. The user comes from @WithMockUser or the given
		// post-processor here; the real token path is covered by McpOAuthFlowIntegrationTest.
		var request = post("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.content(body);
		return mvc.perform(user == null ? request : request.with(user));
	}

	private UUID createPage(String title, String document) throws Exception {
		MvcResult created = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = created.getResponse().getRedirectedUrl();
		UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
		String body = "{\"baseVersion\":0,\"title\":" + mapper.writeValueAsString(title) + ",\"document\":" + document + "}";
		mvc.perform(put("/api/pages/{id}/content", id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk());
		return id;
	}

}
