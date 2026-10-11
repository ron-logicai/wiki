package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.exceptions.PageStateException;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.PageRelation;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.PageRelationRepository;
import nl.logicai.wiki.repositories.PageRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Related pages (the "Gerelateerd" block on the read view): an explicit, directed list that editors
 * maintain per page. Pages in the trash drop out of the list automatically and come back on restore.
 */
@Service
@Transactional
public class RelationService {

	private final PageRelationRepository relations;
	private final PageRepository pages;
	private final AuditEventRepository audit;
	private final Clock clock;

	public RelationService(PageRelationRepository relations, PageRepository pages, AuditEventRepository audit,
			Clock clock) {
		this.relations = relations;
		this.pages = pages;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Page> relatedOf(UUID pageId) {
		return relations.findActiveRelatedPages(pageId);
	}

	/** Active pages that can still be added: everything except the page itself and what is already linked. */
	@Transactional(readOnly = true)
	public List<Page> candidatesFor(UUID pageId) {
		Set<UUID> linked = relations.findActiveRelatedPages(pageId).stream().map(Page::getId).collect(Collectors.toSet());
		return pages.findByDeletedAtIsNullOrderByTitleAsc().stream()
			.filter(p -> !p.getId().equals(pageId) && !linked.contains(p.getId()))
			.toList();
	}

	/** Links {@code relatedId} to the page; returns the linked page. Self, trashed and duplicate links are refused. */
	@PreAuthorize("hasRole('EDITOR')")
	public Page add(UUID pageId, UUID relatedId, String actor) {
		Page page = pages.findByIdAndDeletedAtIsNull(pageId).orElseThrow(() -> new PageNotFoundException(pageId));
		if (relatedId == null || relatedId.equals(pageId)) {
			throw new PageStateException("Een pagina kan niet aan zichzelf gekoppeld worden.");
		}
		Page target = pages.findByIdAndDeletedAtIsNull(relatedId)
			.orElseThrow(() -> new PageStateException("Deze pagina bestaat niet of staat in de prullenbak."));
		if (relations.existsByPageIdAndRelatedId(pageId, relatedId)) {
			throw new PageStateException("“" + target.getTitle() + "” is al gekoppeld.");
		}
		relations.save(new PageRelation(pageId, relatedId, actor, clock.instant()));
		audit.save(AuditEvent.of(AuditEvent.RELATION_ADD, page.getId(), actor, clock.instant(), "met " + target.getTitle()));
		return target;
	}

	/** Removes the link; idempotent, like detaching a tag. */
	@PreAuthorize("hasRole('EDITOR')")
	public void remove(UUID pageId, UUID relatedId, String actor) {
		if (!relations.existsByPageIdAndRelatedId(pageId, relatedId)) {
			return;
		}
		relations.deleteByPageIdAndRelatedId(pageId, relatedId);
		String title = pages.findById(relatedId).map(Page::getTitle).orElse(relatedId.toString());
		audit.save(AuditEvent.of(AuditEvent.RELATION_REMOVE, pageId, actor, clock.instant(), "met " + title));
	}

}
