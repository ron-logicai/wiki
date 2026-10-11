package nl.logicai.wiki.controllers;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import nl.logicai.wiki.exceptions.InvalidTagException;
import nl.logicai.wiki.services.TagService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Tags on pages and the tag overview (spec F-14). Forms POST and redirect to a GET page.
 * <p>
 * The overview exists twice: {@code /tags} for everyone (sidebar item) and {@code /admin/tags} as the
 * "Tags" tab of the admin page (ADMIN only via SecurityConfig). Both render the same template; the
 * model attribute {@code basis} tells the template which prefix to use for its links and form actions,
 * so a rename or delete from the admin tab returns to the admin tab.
 */
@Controller
public class TagController {

	private final TagService tagService;

	public TagController(TagService tagService) {
		this.tagService = tagService;
	}

	@PostMapping("/pages/{id}/tags")
	public String toevoegen(@PathVariable UUID id, @RequestParam String name, Authentication auth,
			RedirectAttributes redirect) {
		try {
			tagService.attach(id, name, auth.getName());
		}
		catch (InvalidTagException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:/pages/" + id;
	}

	@PostMapping("/pages/{id}/tags/{tagId}/remove")
	public String verwijderen(@PathVariable UUID id, @PathVariable UUID tagId) {
		tagService.detach(id, tagId);
		return "redirect:/pages/" + id;
	}

	/**
	 * Tagoverzicht (ontwerp 6a): zoeken op naam ('q') en sorteren op gebruik (standaard) of naam
	 * ('sorteer=naam'). Met 'hernoem' staat de gekozen tag in de kaart "Tag hernoemen" rechts.
	 */
	@GetMapping({"/tags", "/admin/tags"})
	public String overzicht(@RequestParam(required = false) String q, @RequestParam(required = false) String sorteer,
			HttpServletRequest request, Model model) {
		return overzicht(q, sorteer, null, request, model);
	}

	/** Het potlood in de lijst: dezelfde pagina met deze tag geselecteerd om te hernoemen (alleen Admin). */
	@GetMapping({"/tags/{id}/rename", "/admin/tags/{id}/rename"})
	public String hernoemFormulier(@PathVariable UUID id, @RequestParam(required = false) String q,
			@RequestParam(required = false) String sorteer, HttpServletRequest request, Model model) {
		return overzicht(q, sorteer, id, request, model);
	}

	private String overzicht(String q, String sorteer, UUID geselecteerdId, HttpServletRequest request, Model model) {
		String zoek = q == null ? "" : q.strip();
		boolean opNaam = "naam".equals(sorteer);
		List<TagService.TagCount> alle = tagService.overview();
		Comparator<TagService.TagCount> volgorde = opNaam
				? Comparator.comparing(t -> t.tag().getName().toLowerCase(Locale.ROOT))
				: Comparator.comparingLong(TagService.TagCount::paginas).reversed()
					.thenComparing(t -> t.tag().getName().toLowerCase(Locale.ROOT));
		model.addAttribute("tags", alle.stream()
			.filter(t -> zoek.isEmpty() || t.tag().getName().toLowerCase(Locale.ROOT).contains(zoek.toLowerCase(Locale.ROOT)))
			.sorted(volgorde)
			.toList());
		model.addAttribute("q", zoek);
		model.addAttribute("sorteer", opNaam ? "naam" : "gebruik");
		model.addAttribute("geselecteerd", geselecteerdId == null ? null
				: alle.stream().filter(t -> t.tag().getId().equals(geselecteerdId)).findFirst().orElse(null));
		boolean beheer = inBeheer(request);
		model.addAttribute("beheer", beheer);
		model.addAttribute("basis", basis(beheer));
		return "tags";
	}

	@PostMapping({"/tags/{id}/rename", "/admin/tags/{id}/rename"})
	public String hernoemen(@PathVariable UUID id, @RequestParam String name, HttpServletRequest request,
			RedirectAttributes redirect) {
		try {
			tagService.rename(id, name);
			redirect.addFlashAttribute("melding", "Tag hernoemd.");
		}
		catch (InvalidTagException ex) {
			redirect.addFlashAttribute("fout", ex.getMessage());
		}
		return "redirect:" + basis(inBeheer(request));
	}

	@PostMapping({"/tags/{id}/delete", "/admin/tags/{id}/delete"})
	public String verwijderen(@PathVariable UUID id, HttpServletRequest request, RedirectAttributes redirect) {
		tagService.delete(id);
		redirect.addFlashAttribute("melding", "Tag verwijderd. De pagina's zelf zijn niet gewijzigd.");
		return "redirect:" + basis(inBeheer(request));
	}

	/** True when the request came in via the admin tab ({@code /admin/tags...}) instead of {@code /tags...}. */
	static boolean inBeheer(HttpServletRequest request) {
		return request.getRequestURI().startsWith(request.getContextPath() + "/admin/");
	}

	private static String basis(boolean beheer) {
		return beheer ? "/admin/tags" : "/tags";
	}

}
