package nl.logicai.wiki.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.services.BlockRenderer;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.TagService;
import nl.logicai.wiki.services.SearchService;
import nl.logicai.wiki.services.TemplateService;
import nl.logicai.wiki.models.PageTemplate;
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
@WebMvcTest({WebController.class, PageController.class, TagController.class, TemplateController.class, SearchController.class, TrashController.class})
@Import({BlockRenderer.class, GlobalModelAdvice.class, Datums.class})
@WithMockUser(username = "editor", roles = "EDITOR")
class PageControllerTemplateTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PageService pageService;

	@MockitoBean
	private TagService tagService;

	@MockitoBean
	private TemplateService templateService;

	@MockitoBean
	private SearchService searchService;

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
			.andExpect(content().string(containsString("Recent bijgewerkt")))
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
		when(pageService.getAny(page.getId())).thenReturn(page);
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
		PageTemplate template = PageTemplate.create("Werkinstructie", "Stappenplan", new WikiDocument("[]", "", 1), "editor", Instant.parse("2026-09-17T09:00:00Z"));
		when(templateService.all()).thenReturn(List.of(template));

		mvc.perform(get("/pages/new").param("parentId", page.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("subpagina van")))
			.andExpect(content().string(containsString("name=\"parentId\"")));
		mvc.perform(get("/pages/new"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Leeg")))
			.andExpect(content().string(containsString("name=\"templateId\"")))
			.andExpect(content().string(containsString("value=\"" + template.getId() + "\"")));
	}

	@Test
	void moveFormRendersTopLevelAndTargets() throws Exception {
		Page other = Page.create("Projecten", null, new WikiDocument("[]", "", 1), "editor", Instant.parse("2026-09-15T10:00:00Z"));
		when(pageService.getActive(page.getId())).thenReturn(page);
		when(pageService.ancestors(any())).thenReturn(List.of());
		when(pageService.moveTargets(page.getId())).thenReturn(List.of(new PageService.MoveTarget(other, 0)));

		mvc.perform(get("/pages/{id}/move", page.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Hoofdniveau")))
			.andExpect(content().string(containsString("name=\"parentId\"")))
			.andExpect(content().string(containsString("value=\"" + other.getId() + "\"")))
			.andExpect(content().string(containsString("/pages/" + page.getId() + "/move")));
	}

	@Test
	void tagOverviewRendersCountsWithoutAdminForms() throws Exception {
		Tag tag = Tag.create("Shopify", "editor", Instant.parse("2026-09-17T09:00:00Z"));
		when(tagService.overview()).thenReturn(List.of(new TagService.TagCount(tag, 2)));

		mvc.perform(get("/tags"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Shopify")))
			.andExpect(content().string(containsString(">2</span> pagina's")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/rename"))));
	}

	@Test
	void pageRendersTagsAndAddFormForEditor() throws Exception {
		Tag tag = Tag.create("Onboarding", "editor", Instant.parse("2026-09-17T09:00:00Z"));
		when(pageService.getAny(page.getId())).thenReturn(page);
		when(pageService.ancestors(any())).thenReturn(List.of());
		when(pageService.children(page.getId())).thenReturn(List.of());
		when(tagService.tagsOf(page.getId())).thenReturn(List.of(tag));

		mvc.perform(get("/pages/{id}", page.getId()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("class=\"label label--tag\"")))
			.andExpect(content().string(containsString("/tags/" + tag.getId() + "/remove")))
			.andExpect(content().string(containsString("name=\"name\"")));
	}

	@Test
	void searchPageRendersHitsFiltersAndPaging() throws Exception {
		SearchService.Hit hit = new SearchService.Hit(page, "Werkinstructies",
			List.of(new SearchService.Segment("Log in bij ", false), new SearchService.Segment("Shopify", true)));
		when(searchService.search("shopify", null, 1)).thenReturn(new SearchService.Result("shopify", null, 1, 2, 21,
			List.of(hit), List.of(new SearchService.TagCount("Werkinstructie", 3))));

		mvc.perform(get("/search").param("q", "shopify"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/pages/" + page.getId())))
			.andExpect(content().string(containsString("Werkinstructies / Onboarding")))
			.andExpect(content().string(containsString("<mark>Shopify</mark>")))
			.andExpect(content().string(containsString("tag=Werkinstructie")))
			.andExpect(content().string(containsString("Volgende")));
	}

	@Test
	void deletedPageRendersTrashMessageWith410() throws Exception {
		Page weg = Page.create("Oude notitie", null, new WikiDocument("[]", "", 1), "editor", Instant.parse("2026-09-15T10:00:00Z"));
		weg.moveToTrash("editor", Instant.parse("2026-09-17T12:00:00Z"));
		when(pageService.getAny(weg.getId())).thenReturn(weg);

		mvc.perform(get("/pages/{id}", weg.getId()))
			.andExpect(status().isGone())
			.andExpect(content().string(containsString("staat in de prullenbak")))
			.andExpect(content().string(containsString("/trash")));
	}

	@Test
	void trashPageRendersItemsAndRestoreForms() throws Exception {
		Page weg = Page.create("Oude notitie", null, new WikiDocument("[]", "", 1), "editor", Instant.parse("2026-09-15T10:00:00Z"));
		weg.moveToTrash("editor", Instant.parse("2026-09-17T12:00:00Z"));
		when(pageService.trash()).thenReturn(List.of(new PageService.TrashItem(weg, null, false)));

		mvc.perform(get("/trash"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Oude notitie")))
			.andExpect(content().string(containsString("/trash/" + weg.getId() + "/restore")))
			.andExpect(content().string(containsString("stond op het hoofdniveau")));
	}

	@Test
	void unknownPageRenders404() throws Exception {
		UUID missing = UUID.randomUUID();
		when(pageService.getAny(missing)).thenThrow(new PageNotFoundException(missing));

		mvc.perform(get("/pages/{id}", missing))
			.andExpect(status().isNotFound());
	}

}
