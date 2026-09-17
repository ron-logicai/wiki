package nl.logicai.wiki.controllers;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import nl.logicai.wiki.exceptions.InvalidMoveException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.models.MovePageForm;
import nl.logicai.wiki.models.NewPageForm;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.PageRevision;
import nl.logicai.wiki.services.BlockRenderer;
import nl.logicai.wiki.services.PageService;
import nl.logicai.wiki.services.TagService;
import nl.logicai.wiki.services.TemplateService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Server-rendered page routes from the URL contract (spec section 5). Every visible page is a
 * full Thymeleaf page reachable by direct link; forms POST and redirect to a GET.
 */
@Controller
@RequestMapping("/pages")
public class PageController {

	private final PageService pageService;
	private final BlockRenderer blockRenderer;
	private final TagService tagService;
	private final TemplateService templateService;

	public PageController(PageService pageService, BlockRenderer blockRenderer, TagService tagService,
			TemplateService templateService) {
		this.pageService = pageService;
		this.blockRenderer = blockRenderer;
		this.tagService = tagService;
		this.templateService = templateService;
	}

	@GetMapping
	public String lijst(Model model) {
		model.addAttribute("paginas", pageService.allActive());
		return "paginas";
	}

	@GetMapping("/new")
	public String nieuwFormulier(@RequestParam(required = false) UUID parentId, Model model) {
		NewPageForm form = new NewPageForm();
		form.setParentId(parentId);
		model.addAttribute("form", form);
		voegOuderToe(parentId, model);
		return "pagina-formulier";
	}

	@PostMapping
	public String aanmaken(@Valid @ModelAttribute("form") NewPageForm form, BindingResult binding,
			Authentication auth, Model model) {
		if (binding.hasErrors()) {
			voegOuderToe(form.getParentId(), model);
			return "pagina-formulier";
		}
		Page page = pageService.create(form.getTitle(), form.getParentId(), auth.getName(), form.getTemplateId());
		return "redirect:/pages/" + page.getId();
	}

	@GetMapping("/{id}")
	public String bekijken(@PathVariable UUID id, Model model) {
		Page page = pageService.getActive(id);
		model.addAttribute("pagina", page);
		model.addAttribute("ouders", pageService.ancestors(page));
		model.addAttribute("subpaginas", pageService.children(id));
		model.addAttribute("tags", tagService.tagsOf(id));
		model.addAttribute("inhoudHtml", blockRenderer.render(page.getDocument()));
		return "pagina";
	}

	@GetMapping("/{id}/edit")
	public String bewerken(@PathVariable UUID id, Model model) {
		Page page = pageService.getActive(id);
		model.addAttribute("pagina", page);
		model.addAttribute("ouders", pageService.ancestors(page));
		model.addAttribute("documentJson", PageService.embeddableJson(page.getDocument()));
		return "pagina-bewerken";
	}

	@GetMapping("/{id}/history")
	public String geschiedenis(@PathVariable UUID id, Model model) {
		Page page = pageService.getActive(id);
		model.addAttribute("pagina", page);
		model.addAttribute("ouders", pageService.ancestors(page));
		model.addAttribute("revisies", pageService.history(id));
		return "geschiedenis";
	}

	@GetMapping("/{id}/history/{revisionId}")
	public String revisie(@PathVariable UUID id, @PathVariable UUID revisionId, Model model) {
		Page page = pageService.getActive(id);
		PageRevision revision = pageService.revision(id, revisionId);
		model.addAttribute("pagina", page);
		model.addAttribute("ouders", pageService.ancestors(page));
		model.addAttribute("revisie", revision);
		model.addAttribute("inhoudHtml", blockRenderer.render(revision.getDocument()));
		return "revisie";
	}

	@GetMapping("/{id}/move")
	public String verplaatsFormulier(@PathVariable UUID id, Model model) {
		Page page = pageService.getActive(id);
		MovePageForm form = new MovePageForm();
		form.setParentId(page.getParentId());
		form.setBaseVersion(page.getLockVersion());
		model.addAttribute("form", form);
		voegVerplaatsContextToe(page, model);
		return "pagina-verplaatsen";
	}

	@PostMapping("/{id}/move")
	public String verplaatsen(@PathVariable UUID id, @Valid @ModelAttribute("form") MovePageForm form,
			BindingResult binding, Model model, HttpServletResponse response) {
		if (!binding.hasErrors()) {
			try {
				pageService.move(id, form.getParentId(), form.getBaseVersion());
				return "redirect:/pages/" + id;
			}
			catch (InvalidMoveException ex) {
				binding.rejectValue("parentId", "ongeldig", ex.getMessage());
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			}
			catch (PageConflictException ex) {
				binding.reject("conflict", ex.getMessage() + " Controleer de nieuwe plek en probeer opnieuw.");
				response.setStatus(HttpServletResponse.SC_CONFLICT);
			}
		}
		Page page = pageService.getActive(id);
		form.setBaseVersion(page.getLockVersion());
		voegVerplaatsContextToe(page, model);
		return "pagina-verplaatsen";
	}

	private void voegVerplaatsContextToe(Page page, Model model) {
		model.addAttribute("pagina", page);
		model.addAttribute("ouders", pageService.ancestors(page));
		model.addAttribute("doelen", pageService.moveTargets(page.getId()));
	}

	private void voegOuderToe(UUID parentId, Model model) {
		model.addAttribute("ouder", parentId == null ? null : pageService.getActive(parentId));
		model.addAttribute("plekken", pageService.moveTargets(null));
		model.addAttribute("templates", templateService.all());
	}

}
