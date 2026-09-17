package nl.logicai.wiki.services;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.exceptions.InvalidTagException;
import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.models.PageTag;
import nl.logicai.wiki.models.Tag;
import nl.logicai.wiki.repositories.PageRepository;
import nl.logicai.wiki.repositories.PageTagRepository;
import nl.logicai.wiki.repositories.TagRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tags (spec F-14): unique name ignoring case; editors create and attach them, admins rename and
 * delete them. Removing a tag never removes a page.
 */
@Service
@Transactional
public class TagService {

	/** A tag with the number of active pages that carry it, for the overview. */
	public record TagCount(Tag tag, long paginas) {
	}

	private final TagRepository tags;
	private final PageTagRepository links;
	private final PageRepository pages;
	private final Clock clock;

	public TagService(TagRepository tags, PageTagRepository links, PageRepository pages, Clock clock) {
		this.tags = tags;
		this.links = links;
		this.pages = pages;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Tag> tagsOf(UUID pageId) {
		return tags.findByPageId(pageId);
	}

	@Transactional(readOnly = true)
	public List<TagCount> overview() {
		Map<UUID, Long> counts = new HashMap<>();
		for (Object[] row : tags.countActivePagesPerTag()) {
			counts.put((UUID) row[0], (Long) row[1]);
		}
		return tags.findAllByOrderByNameAsc().stream()
			.map(tag -> new TagCount(tag, counts.getOrDefault(tag.getId(), 0L)))
			.toList();
	}

	/** Attaches a tag by name to an active page, creating the tag when it does not exist yet. */
	@PreAuthorize("hasRole('EDITOR')")
	public Tag attach(UUID pageId, String rawName, String actor) {
		pages.findByIdAndDeletedAtIsNull(pageId).orElseThrow(() -> new PageNotFoundException(pageId));
		String name = normalize(rawName);
		Tag tag = tags.findByNameIgnoreCase(name)
			.orElseGet(() -> tags.save(Tag.create(name, actor, clock.instant())));
		if (!links.existsByPageIdAndTagId(pageId, tag.getId())) {
			links.save(new PageTag(pageId, tag.getId()));
		}
		return tag;
	}

	@PreAuthorize("hasRole('EDITOR')")
	public void detach(UUID pageId, UUID tagId) {
		links.deleteByPageIdAndTagId(pageId, tagId);
	}

	/** Renames a tag; the pages keep the tag because the link is by id (spec F-14). */
	@PreAuthorize("hasRole('ADMIN')")
	public Tag rename(UUID tagId, String rawName) {
		Tag tag = tags.findById(tagId).orElseThrow(() -> new InvalidTagException("Deze tag bestaat niet meer."));
		String name = normalize(rawName);
		Optional<Tag> taken = tags.findByNameIgnoreCase(name);
		if (taken.isPresent() && !taken.get().getId().equals(tagId)) {
			throw new InvalidTagException("Er bestaat al een tag met de naam “" + taken.get().getName() + "”.");
		}
		tag.rename(name);
		return tag;
	}

	/** Deletes the tag and its links; no page disappears (spec F-14). */
	@PreAuthorize("hasRole('ADMIN')")
	public void delete(UUID tagId) {
		links.deleteByTagId(tagId);
		tags.deleteById(tagId);
	}

	private static String normalize(String rawName) {
		String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
		if (name.isEmpty()) {
			throw new InvalidTagException("Een tagnaam is verplicht.");
		}
		if (name.length() > Tag.MAX_LENGTH) {
			throw new InvalidTagException("Een tagnaam mag maximaal " + Tag.MAX_LENGTH + " tekens bevatten.");
		}
		return name;
	}

}
