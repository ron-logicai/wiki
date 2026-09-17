package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.exceptions.TemplateNotFoundException;
import nl.logicai.wiki.models.PageTemplate;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.repositories.PageTemplateRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Templates (spec F-15): editors create, edit and delete them. A new page gets an independent copy
 * of the document (see {@link PageService#create}); the template is never linked to the page.
 */
@Service
@Transactional
public class TemplateService {

	private final PageTemplateRepository templates;
	private final DocumentValidator validator;
	private final ObjectMapper mapper;
	private final Clock clock;

	public TemplateService(PageTemplateRepository templates, DocumentValidator validator,
			ObjectMapper mapper, Clock clock) {
		this.templates = templates;
		this.validator = validator;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PageTemplate> all() {
		return templates.findAllByOrderByTitleAsc();
	}

	@Transactional(readOnly = true)
	public PageTemplate get(UUID id) {
		return templates.findById(id).orElseThrow(() -> new TemplateNotFoundException(id));
	}

	/** A validated copy of the template's document, for a new page. */
	@Transactional(readOnly = true)
	public WikiDocument documentOf(UUID id) {
		return validator.validate(mapper.readTree(get(id).getDocument()));
	}

	@PreAuthorize("hasRole('EDITOR')")
	public PageTemplate create(String title, String description, String actor) {
		String cleanTitle = validator.normalizeTitle(title);
		String cleanDescription = description == null ? "" : description.trim();
		if (cleanDescription.length() > 300) {
			cleanDescription = cleanDescription.substring(0, 300);
		}
		WikiDocument document = validator.validate(mapper.readTree(WikiDocument.EMPTY_JSON));
		return templates.saveAndFlush(PageTemplate.create(cleanTitle, cleanDescription, document, actor, clock.instant()));
	}

	/** Same save contract as pages: base version must match, content is validated (spec section 8). */
	@PreAuthorize("hasRole('EDITOR')")
	public PageTemplate saveContent(UUID id, long baseVersion, String title, JsonNode document, String actor) {
		PageTemplate template = get(id);
		if (template.getLockVersion() != baseVersion) {
			throw new PageConflictException(template.getLockVersion());
		}
		String cleanTitle = validator.normalizeTitle(title);
		WikiDocument validated = validator.validate(document);
		template.update(cleanTitle, validated, actor, clock.instant());
		return templates.saveAndFlush(template);
	}

	/** Deleting a template leaves every page created from it untouched (spec F-15). */
	@PreAuthorize("hasRole('EDITOR')")
	public void delete(UUID id) {
		templates.delete(get(id));
	}

}
