package nl.logicai.wiki.controllers;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import nl.logicai.wiki.exceptions.InvalidContentException;
import nl.logicai.wiki.exceptions.InvalidMoveException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.exceptions.PageStateException;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
	public String bekijken(@PathVariable UUID id, Model model, HttpServletResponse response) {
		Page page = pageService.getAny(id);
		if (page.isDeleted()) {
			// A link to a page in the trash shows a clear message, never another page (spec F-12, F-16).
			model.addAttribute("pagina", page);
			response.setStatus(HttpServletResponse.SC_GONE);
			return "pagina-verwijderd";
		}
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

	/** Restores an earlier revision as a new one (spec F-11). Every existing revision stays in the history. */
	@PostMapping("/{id}/history/{revisionId}/restore")
	public String revisieHerstellen(@PathVariable UUID id, @PathVariable UUID revisionId,
			@RequestParam long baseVersion, Authentication auth, RedirectAttributes redirect) {
		try {
			PageService.RevisionRestore result = pageService.restoreRevision(id, revisionId, baseVersion, auth.getName());
			int bron = result.source().getRevisionNumber();
			redirect.addFlashAttribute("melding", result.changed()
				? "Versie " + bron + " is teruggezet als versie " + result.page().getCurrentRevision() + "."
				: "Versie " + bron + " is al gelijk aan de actuele pagina; er is niets gewijzigd.");
			return "redirect:/pages/" + id;
		}
		catch (PageConflictException | InvalidContentException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
			return "redirect:/pages/" + id + "/history/" + revisionId;
		}
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
			BindingResult binding, Authentication auth, Model model, HttpServletResponse response) {
		if (!binding.hasErrors()) {
			try {
				pageService.move(id, form.getParentId(), form.getBaseVersion(), auth.getName());
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

	@PostMapping("/{id}/delete")
	public String naarPrullenbak(@PathVariable UUID id, @RequestParam long baseVersion, Authentication auth,
			RedirectAttributes redirect) {
		try {
			Page page = pageService.moveToTrash(id, baseVersion, auth.getName());
			redirect.addFlashAttribute("melding", "201c" + page.getTitle() + "201d staat in de prullenbak. Je kunt de pagina daar herstellen.");
			return page.getParentId() == null ? "redirect:/" : "redirect:/pages/" + page.getParentId();
		}
		catch (PageStateException | PageConflictException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
			return "redirect:/pages/" + id;
		}
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
