package nl.logicai.wiki.services;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.exceptions.InvalidMoveException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.exceptions.PageStateException;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.PageRepository;
import nl.logicai.wiki.models.PageRevision;
import nl.logicai.wiki.repositories.PageRevisionRepository;
import nl.logicai.wiki.models.WikiDocument;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Business rules for pages. Controllers handle HTTP; this class owns transactions and
 * the save path from spec section 8: validate, then store page, search text and revision
 * in one transaction.
 */
@Service
@Transactional
public class PageService {

	private final PageRepository pages;
	private final PageRevisionRepository revisions;
	private final DocumentValidator validator;
	private final ObjectMapper mapper;
	private final Clock clock;
	private final TemplateService templates;
	private final AuditEventRepository audit;

	public PageService(PageRepository pages, PageRevisionRepository revisions,
			DocumentValidator validator, ObjectMapper mapper, Clock clock, TemplateService templates,
			AuditEventRepository audit) {
		this.pages = pages;
		this.revisions = revisions;
		this.validator = validator;
		this.mapper = mapper;
		this.clock = clock;
		this.templates = templates;
		this.audit = audit;
	}

	@PreAuthorize("hasRole('EDITOR')")
	public Page create(String title, UUID parentId, String actor) {
		return create(title, parentId, actor, null);
	}

	/**
	 * Creates a page, optionally starting from a template (spec F-15): the page gets its own UUID and
	 * an independent copy of the template document; no reference to the template is kept.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Page create(String title, UUID parentId, String actor, UUID templateId) {
		String cleanTitle = validator.normalizeTitle(title);
		if (parentId != null) {
			getActive(parentId);
		}
		WikiDocument document = templateId == null
			? validator.validate(mapper.readTree(WikiDocument.EMPTY_JSON))
			: templates.documentOf(templateId);
		Instant now = clock.instant();
		// Create revision 1 before persisting, so the fresh page keeps lock version 0.
		Page page = Page.create(cleanTitle, parentId, document, actor, now);
		PageRevision first = page.nextRevision(actor, now);
		page = pages.save(page);
		revisions.save(first);
		pages.flush();
		return page;
	}

	/**
	 * Saves title and document when the client's base version matches the stored version.
	 * An unchanged save creates no new revision.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Page saveContent(UUID id, long baseVersion, String title, JsonNode document, String actor) {
		Page page = getActive(id);
		if (page.getLockVersion() != baseVersion) {
			throw new PageConflictException(page.getLockVersion());
		}
		String cleanTitle = validator.normalizeTitle(title);
		WikiDocument validated = validator.validate(document);

		boolean unchanged = cleanTitle.equals(page.getTitle())
			&& mapper.readTree(page.getDocument()).equals(document);
		if (unchanged) {
			return page;
		}
		PageRevision revision = page.update(cleanTitle, validated, actor, clock.instant());
		revisions.save(revision);
		return pages.saveAndFlush(page);
	}

	@Transactional(readOnly = true)
	public Page getActive(UUID id) {
		return pages.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new PageNotFoundException(id));
	}

	/** Ancestors from root to direct parent, for breadcrumbs (spec F-05). */
	@Transactional(readOnly = true)
	public List<Page> ancestors(Page page) {
		List<Page> chain = new ArrayList<>();
		UUID parentId = page.getParentId();
		int guard = 0;
		while (parentId != null && guard++ < 100) {
			Page parent = pages.findById(parentId).orElse(null);
			if (parent == null) {
				break;
			}
			chain.addFirst(parent);
			parentId = parent.getParentId();
		}
		return chain;
	}

	@Transactional(readOnly = true)
	public List<Page> children(UUID parentId) {
		return pages.findByParentIdAndDeletedAtIsNullOrderByTitleAsc(parentId);
	}

	@Transactional(readOnly = true)
	public List<Page> rootPages() {
		return pages.findByParentIdIsNullAndDeletedAtIsNullOrderByTitleAsc();
	}

	@Transactional(readOnly = true)
	public List<Page> allActive() {
		return pages.findByDeletedAtIsNullOrderByTitleAsc();
	}

	/**
	 * Moves a page (with its subtree) under a new parent, or to the top level when newParentId is null
	 * (spec F-04). The URL never changes. Refuses the page itself and its descendants as target, and
	 * checks the version the user saw. SERIALIZABLE so two concurrent moves cannot form a cycle:
	 * PostgreSQL aborts one of them instead.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public Page move(UUID id, UUID newParentId, long baseVersion, String actor) {
		Page page = getActive(id);
		if (page.getLockVersion() != baseVersion) {
			throw new PageConflictException(page.getLockVersion());
		}
		if (newParentId != null) {
			if (newParentId.equals(id)) {
				throw new InvalidMoveException("Een pagina kan niet onder zichzelf staan.");
			}
			Page parent = getActive(newParentId);
			boolean underItself = ancestors(parent).stream().anyMatch(a -> a.getId().equals(id));
			if (underItself) {
				throw new InvalidMoveException("Een pagina kan niet onder een eigen subpagina staan.");
			}
		}
		page.moveTo(newParentId);
		audit.save(AuditEvent.of(AuditEvent.MOVE, id, actor, clock.instant(),
			"naar " + (newParentId == null ? "hoofdniveau" : newParentId)));
		return pages.saveAndFlush(page);
	}

	/** Any page, also one in the trash; for the "this page is in the trash" message (spec F-12, F-16). */
	@Transactional(readOnly = true)
	public Page getAny(UUID id) {
		return pages.findById(id).orElseThrow(() -> new PageNotFoundException(id));
	}

	/** A page in the trash with the state of its former parent, for the trash overview. */
	public record TrashItem(Page page, Page parent, boolean parentActive) {
	}

	@Transactional(readOnly = true)
	public List<TrashItem> trash() {
		List<TrashItem> items = new ArrayList<>();
		for (Page page : pages.findByDeletedAtIsNotNullOrderByDeletedAtDesc()) {
			Page parent = page.getParentId() == null ? null : pages.findById(page.getParentId()).orElse(null);
			items.add(new TrashItem(page, parent, parent != null && !parent.isDeleted()));
		}
		return items;
	}

	/**
	 * Soft delete (spec F-12). Refused while active subpages exist: move or delete those first.
	 * The version the user saw must match, like every other change.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Page moveToTrash(UUID id, long baseVersion, String actor) {
		Page page = getActive(id);
		if (page.getLockVersion() != baseVersion) {
			throw new PageConflictException(page.getLockVersion());
		}
		if (pages.existsByParentIdAndDeletedAtIsNull(id)) {
			throw new PageStateException("Deze pagina heeft nog subpagina's. Verplaats of verwijder die eerst.");
		}
		Instant now = clock.instant();
		page.moveToTrash(actor, now);
		audit.save(AuditEvent.of(AuditEvent.DELETE, id, actor, now, ""));
		return pages.saveAndFlush(page);
	}

	/**
	 * Restore from the trash (spec F-12). Keeps the old parent when it is still active; otherwise the
	 * caller chooses an active parent or the top level (null). Identity, history and tags stay.
	 */
	@PreAuthorize("hasRole('EDITOR')")
	public Page restore(UUID id, UUID chosenParentId, boolean parentChosen, String actor) {
		Page page = getAny(id);
		if (!page.isDeleted()) {
			throw new PageStateException("Deze pagina staat niet in de prullenbak.");
		}
		UUID parentId;
		if (parentChosen) {
			parentId = chosenParentId == null ? null : getActive(chosenParentId).getId();
		}
		else {
			UUID oldParent = page.getParentId();
			boolean oldParentActive = oldParent != null && pages.findByIdAndDeletedAtIsNull(oldParent).isPresent();
			if (oldParent != null && !oldParentActive) {
				throw new PageStateException("De oorspronkelijke bovenliggende pagina is verwijderd. Kies een andere plek.");
			}
			parentId = oldParent;
		}
		page.restore(parentId);
		audit.save(AuditEvent.of(AuditEvent.RESTORE, id, actor, clock.instant(),
			"onder " + (parentId == null ? "hoofdniveau" : parentId)));
		return pages.saveAndFlush(page);
	}

	/** A possible new parent for the move form, with its depth in the tree for indentation. */
	public record MoveTarget(Page page, int depth) {
	}

	/**
	 * All active pages except the page itself and its descendants, in tree order (spec F-04).
	 * With null nothing is excluded: every page can be the parent of a new page.
	 */
	@Transactional(readOnly = true)
	public List<MoveTarget> moveTargets(UUID id) {
		List<MoveTarget> targets = new ArrayList<>();
		verzamelDoelen(tree(), id, 0, targets);
		return targets;
	}

	private void verzamelDoelen(List<PageNode> knopen, UUID uitgesloten, int diepte, List<MoveTarget> targets) {
		for (PageNode knoop : knopen) {
			if (knoop.page().getId().equals(uitgesloten)) {
				continue; // skips the page and, with it, its whole subtree
			}
			targets.add(new MoveTarget(knoop.page(), diepte));
			verzamelDoelen(knoop.children(), uitgesloten, diepte + 1, targets);
		}
	}

	/** A page with its (active) subpages, for the sidebar tree and the spaces on the home page. */
	public record PageNode(Page page, List<PageNode> children) {

		/** Number of pages below this one, at any depth. */
		public int aantalNakomelingen() {
			int totaal = 0;
			for (PageNode kind : children) {
				totaal += 1 + kind.aantalNakomelingen();
			}
			return totaal;
		}

	}

	/** All active pages as a tree, one query; siblings ordered by title. */
	@Transactional(readOnly = true)
	public List<PageNode> tree() {
		Map<UUID, List<Page>> perOuder = new HashMap<>();
		for (Page page : allActive()) {
			perOuder.computeIfAbsent(page.getParentId(), k -> new ArrayList<>()).add(page);
		}
		return takken(null, perOuder, 0);
	}

	private List<PageNode> takken(UUID parentId, Map<UUID, List<Page>> perOuder, int diepte) {
		if (diepte > 100) {
			return List.of();
		}
		List<PageNode> knopen = new ArrayList<>();
		for (Page page : perOuder.getOrDefault(parentId, List.of())) {
			knopen.add(new PageNode(page, takken(page.getId(), perOuder, diepte + 1)));
		}
		return knopen;
	}

	@Transactional(readOnly = true)
	public List<Page> recentlyChanged() {
		return pages.findTop10ByDeletedAtIsNullOrderByUpdatedAtDesc();
	}

	@Transactional(readOnly = true)
	public List<PageRevision> history(UUID pageId) {
		getActive(pageId);
		return revisions.findByPageIdOrderByRevisionNumberDesc(pageId);
	}

	@Transactional(readOnly = true)
	public PageRevision revision(UUID pageId, UUID revisionId) {
		getActive(pageId);
		return revisions.findByIdAndPageId(revisionId, pageId)
			.orElseThrow(() -> new PageNotFoundException(revisionId));
	}

	/** Makes the document JSON safe to embed inside a &lt;script type="application/json"&gt; tag. */
	public static String embeddableJson(String json) {
		return json.replace("<", "\\u003c");
	}

}
