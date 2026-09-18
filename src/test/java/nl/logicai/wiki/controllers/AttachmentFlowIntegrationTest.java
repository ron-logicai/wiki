package nl.logicai.wiki.controllers;

import java.util.UUID;

import nl.logicai.wiki.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Video upload, use in a document, and authorised download with Range support. */
@SpringBootTest(properties = "wiki.attachments.dir=${java.io.tmpdir}/wiki-test-attachments")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttachmentFlowIntegrationTest {

	/** A minimal MP4 header ("ftyp" box) followed by filler; enough for the type check. */
	private static final byte[] MP4 = mp4Bytes();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper mapper;

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorUploadsAVideoUsesItInThePageAndStreamsIt() throws Exception {
		UUID pageId = createPage("Met video");

		MvcResult upload = mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "demo.mp4", "application/octet-stream", MP4))
				.param("pageId", pageId.toString()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.url", matchesPattern("/attachments/[0-9a-f-]{36}")))
			.andExpect(jsonPath("$.contentType").value("video/mp4"))
			.andExpect(jsonPath("$.name").value("demo.mp4"))
			.andExpect(jsonPath("$.size").value(MP4.length))
			.andReturn();
		String url = mapper.readTree(upload.getResponse().getContentAsString()).path("url").stringValue();

		mvc.perform(put("/api/pages/{id}/content", pageId).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":0,\"title\":\"Met video\",\"document\":[{\"type\":\"video\",\"props\":{\"url\":\""
					+ url + "\",\"name\":\"demo.mp4\",\"caption\":\"Uitleg\"},\"children\":[]}]}"))
			.andExpect(status().isOk());

		mvc.perform(get("/pages/{id}", pageId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<video controls preload=\"metadata\" src=\"" + url + "\"></video>")))
			.andExpect(content().string(containsString("<figcaption>Uitleg</figcaption>")));

		mvc.perform(get(url))
			.andExpect(status().isOk())
			.andExpect(content().contentType("video/mp4"))
			.andExpect(header().string("Accept-Ranges", "bytes"))
			.andExpect(header().string("Content-Disposition", containsString("inline")))
			.andExpect(content().bytes(MP4));

		// The browser's player seeks with Range requests.
		MvcResult partial = mvc.perform(get(url).header("Range", "bytes=0-3"))
			.andExpect(status().isPartialContent())
			.andReturn();
		assertThat(partial.getResponse().getContentAsByteArray()).hasSize(4);
	}

	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void nonVideoFilesAndForeignUrlsAreRefused() throws Exception {
		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "script.mp4", "video/mp4", "<html><script>alert(1)</script>".getBytes())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error", containsString("MP4")));

		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "leeg.mp4", "video/mp4", new byte[0])))
			.andExpect(status().isBadRequest());

		UUID pageId = createPage("Verkeerde video");
		mvc.perform(put("/api/pages/{id}/content", pageId).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":0,\"title\":\"Verkeerde video\",\"document\":[{\"type\":\"video\",\"props\":{\"url\":\"https://evil.example/x.mp4\"}}]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error", containsString("geüpload")));

		mvc.perform(get("/attachments/{id}", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "viewer", roles = "VIEWER")
	void viewerCannotUpload() throws Exception {
		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "demo.mp4", "video/mp4", MP4)))
			.andExpect(status().isForbidden());
	}

	@Test
	void withoutASessionUploadGets401AndDownloadGoesToLogin() throws Exception {
		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "demo.mp4", "video/mp4", MP4)))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/attachments/{id}", UUID.randomUUID()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")));
	}

	private UUID createPage(String title) throws Exception {
		MvcResult result = mvc.perform(post("/pages").with(csrf()).param("title", title))
			.andExpect(status().is3xxRedirection()).andReturn();
		return UUID.fromString(result.getResponse().getRedirectedUrl().substring("/pages/".length()));
	}

	private static byte[] mp4Bytes() {
		byte[] bytes = new byte[64];
		byte[] header = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
		System.arraycopy(header, 0, bytes, 0, header.length);
		return bytes;
	}

}
