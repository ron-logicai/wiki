package nl.logicai.wiki.controllers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

	/** A real 1x1 PNG, so a browser would also render it. */
	private static final byte[] PNG = Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	/** JPEG SOI marker plus a JFIF APP0 segment; enough for the type check. */
	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10,
		'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, (byte) 0xFF, (byte) 0xD9};

	private static final byte[] PDF = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n"
		.getBytes(StandardCharsets.US_ASCII);

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

	/** Spec U-01: PNG, JPEG and PDF are uploaded, used as "image" / "file" blocks, and served inline. */
	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void editorUploadsAnImageAndAPdfAndThePageShowsThem() throws Exception {
		UUID pageId = createPage("Met afbeelding en PDF");

		String pngUrl = upload("schema.png", PNG, "image/png", pageId);
		String jpegUrl = upload("foto.jpg", JPEG, "image/jpeg", pageId);
		String pdfUrl = upload("handleiding.pdf", PDF, "application/pdf", pageId);

		mvc.perform(put("/api/pages/{id}/content", pageId).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"baseVersion\":0,\"title\":\"Met afbeelding en PDF\",\"document\":["
					+ "{\"type\":\"image\",\"props\":{\"url\":\"" + pngUrl + "\",\"name\":\"schema.png\",\"caption\":\"Het schema\",\"showPreview\":true,\"previewWidth\":320},\"children\":[]},"
					+ "{\"type\":\"image\",\"props\":{\"url\":\"" + jpegUrl + "\",\"name\":\"foto.jpg\"},\"children\":[]},"
					+ "{\"type\":\"file\",\"props\":{\"url\":\"" + pdfUrl + "\",\"name\":\"handleiding.pdf\",\"caption\":\"Versie 2\"},\"children\":[]}]}"))
			.andExpect(status().isOk());

		mvc.perform(get("/pages/{id}", pageId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(
				"<img src=\"" + pngUrl + "\" alt=\"schema.png\" loading=\"lazy\" width=\"320\">")))
			.andExpect(content().string(containsString("<figcaption>Het schema</figcaption>")))
			.andExpect(content().string(containsString("<img src=\"" + jpegUrl + "\" alt=\"foto.jpg\" loading=\"lazy\">")))
			.andExpect(content().string(containsString(
				"<figure class=\"bijlage\"><a href=\"" + pdfUrl + "\" target=\"_blank\" rel=\"noopener\">handleiding.pdf</a>"
					+ "<figcaption>Versie 2</figcaption></figure>")));

		mvc.perform(get(pngUrl))
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().bytes(PNG));
		mvc.perform(get(pdfUrl))
			.andExpect(status().isOk())
			.andExpect(content().contentType("application/pdf"))
			.andExpect(header().string("Content-Disposition", containsString("inline")))
			.andExpect(content().bytes(PDF));

		// Name and caption of the files are searchable.
		mvc.perform(get("/search").param("q", "handleiding"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Met afbeelding en PDF")));
	}

	/** Spec U-01: at most 10 MB for PNG/JPEG/PDF; active formats such as SVG never get in, whatever the name. */
	@Test
	@WithMockUser(username = "editor", roles = "EDITOR")
	void imagesLargerThanTenMegabytesAndActiveFormatsAreRefused() throws Exception {
		byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
		System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);
		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "groot.png", "image/png", tooLarge)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error", containsString("maximaal 10 MB")));

		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "plaatje.png", "image/png",
					"<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error", containsString("PNG, JPEG en PDF")));

		mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", "plaatje.gif", "image/gif", "GIF89a  ".getBytes())))
			.andExpect(status().isBadRequest());
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

	private String upload(String fileName, byte[] bytes, String expectedType, UUID pageId) throws Exception {
		// The browser's claim is deliberately wrong: the server must go by the bytes.
		MvcResult upload = mvc.perform(multipart("/api/attachments").with(csrf())
				.file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes))
				.param("pageId", pageId.toString()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.url", matchesPattern("/attachments/[0-9a-f-]{36}")))
			.andExpect(jsonPath("$.contentType").value(expectedType))
			.andExpect(jsonPath("$.name").value(fileName))
			.andExpect(jsonPath("$.size").value(bytes.length))
			.andReturn();
		return mapper.readTree(upload.getResponse().getContentAsString()).path("url").stringValue();
	}

	private static byte[] mp4Bytes() {
		byte[] bytes = new byte[64];
		byte[] header = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
		System.arraycopy(header, 0, bytes, 0, header.length);
		return bytes;
	}

}
