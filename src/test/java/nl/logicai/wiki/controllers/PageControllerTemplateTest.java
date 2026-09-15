package nl.logicai.wiki.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.services.BlockRenderer;
import nl.logicai.wiki.services.PageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renders the Thymeleaf templates without a database; the service layer is mocked. */
@WebMvcTest({WebController.class, PageController.class})
@Import({BlockRenderer.class, GlobalModelAdvice.class})
@WithMockUser(username = "editor", roles = "EDITOR")
class PageControllerTemplateTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PageService pageService;

	private final Page page = Page.create("Onboarding",
		null,
		new WikiDocument(
			"[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Welkom <b>hier</b>\",\"styles\":{}}]}]",
			"Welkom hier", 1),
		"editor", Instant.parse("2026-09-15T10:00:00Z"));

	@Test
	void homeRendersRecentPages() throws Exception {
		when(pageService.recentlyChanged()).thenReturn(List.of(page));
		when(pageService.rootPages()).thenReturn(List.of(page));

		mvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Recent gewijzigd")))
			.andExpect(content().string(containsString("/pages/" + page.getId())));
	}

	@Test
	void listRendersTable() throws Exception {
		when(pageService.allActive()).thenReturn(List.of(page));

		mvc.perform(get("/pages"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Alle pagina's")))
			.andExpect(content().string(containsString("Onboarding")));
	}

	@Test
	void viewRendersTitleEscapedContentAndBreadcrumbs() throws Exception {
		when(pageService.getActive(page.getId())).thenReturn(page);
		when(pageService.ancestors(any())).thenReturn(List.of());
		when(pageService.children(page.getId())).thenReturn(List.of());

		mvc.perform(get("/pages/{id}", page.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<h1>Onboarding</h1>")))
			.andExpect(content().string(containsString("<p>Welkom &lt;b&gt;hier&lt;/b&gt;</p>")))
			.andExpect(content().string(containsString("aria-current=\"page\"")))
			.andExpect(content().string(containsString("/pages/" + page.getId() + "/edit")));
	}

	@Test
	void editEmbedsDocumentJsonAndEditorAssets() throws Exception {
		when(pageService.getActive(page.getId())).thenReturn(page);
		when(pageService.ancestors(any())).thenReturn(List.of());

		mvc.perform(get("/pages/{id}/edit", page.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"editor-root\"")))
			.andExpect(content().string(containsString("data-page-id=\"" + page.getId() + "\"")))
			.andExpect(content().string(containsString("\\u003cb>hier")))
			.andExpect(content().string(containsString("/editor/editor.js")))
			.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	void newPageFormRendersParentLink() throws Exception {
		when(pageService.getActive(page.getId())).thenReturn(page);

		mvc.perform(get("/pages/new").param("parentId", page.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("subpagina van")))
			.andExpect(content().string(containsString("name=\"parentId\"")));
	}

	@Test
	void unknownPageRenders404() throws Exception {
		UUID missing = UUID.randomUUID();
		when(pageService.getActive(missing)).thenThrow(new PageNotFoundException(missing));

		mvc.perform(get("/pages/{id}", missing))
			.andExpect(status().isNotFound());
	}

}
