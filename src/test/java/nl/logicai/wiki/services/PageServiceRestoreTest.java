package nl.logicai.wiki.services;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.exceptions.PageNotFoundException;
import nl.logicai.wiki.models.AuditEvent;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.PageRevision;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.repositories.AuditEventRepository;
import nl.logicai.wiki.repositories.PageRepository;
import nl.logicai.wiki.repositories.PageRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rules for restoring a revision (spec F-11, section 8, "Nadere regels"): restore is a new revision,
 * goes through validation and the version check, and changes nothing but title and content.
 */
class PageServiceRestoreTest {

	private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
	private static final String OLD_JSON = "[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Oud\",\"styles\":{}}]}]";
	private static final String NEW_JSON = "[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Nieuw\",\"styles\":{}}]}]";
	private static final WikiDocument OLD_DOC = new WikiDocument(OLD_JSON, "Oud", 1);
	private static final WikiDocument NEW_DOC = new WikiDocument(NEW_JSON, "Nieuw", 1);

	private final PageRepository pages = mock(PageRepository.class);
	private final PageRevisionRepository revisions = mock(PageRevisionRepository.class);
	private final DocumentValidator validator = mock(DocumentValidator.class);
	private final AuditEventRepository audit = mock(AuditEventRepository.class);
	private final PageService service = new PageService(pages, revisions, validator, new ObjectMapper(),
		Clock.fixed(NOW, ZoneOffset.UTC), mock(TemplateService.class), audit);

	private final UUID parentId = UUID.randomUUID();
	private Page page;
	private PageRevision first;   // revision 1: "Oude titel" + OLD_DOC
	private PageRevision second;  // revision 2: "Nieuwe titel" + NEW_DOC (current)

	@BeforeEach
	void pageWithTwoRevisions() {
		page = Page.create("Oude titel", parentId, OLD_DOC, "anna", NOW.minusSeconds(600));
		ReflectionTestUtils.setField(page, "lockVersion", 1L);
		first = page.nextRevision("anna", NOW.minusSeconds(600));
		second = page.update("Nieuwe titel", NEW_DOC, "mark", NOW.minusSeconds(300));

		when(pages.findByIdAndDeletedAtIsNull(page.getId())).thenReturn(Optional.of(page));
		when(pages.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		when(revisions.findByIdAndPageId(first.getId(), page.getId())).thenReturn(Optional.of(first));
		when(revisions.findByIdAndPageId(second.getId(), page.getId())).thenReturn(Optional.of(second));
		when(validator.normalizeTitle(anyString())).thenAnswer(inv -> inv.getArgument(0));
		when(validator.validate(any())).thenReturn(OLD_DOC);
	}

	@Test
	void restoreBecomesRevisionThreeWithTheOldTitleAndContent() {
		PageService.RevisionRestore result = service.restoreRevision(page.getId(), first.getId(), 1, "editor");

		assertThat(result.changed()).isTrue();
		assertThat(result.source()).isSameAs(first);
		assertThat(page.getTitle()).isEqualTo("Oude titel");
		assertThat(page.getDocument()).isEqualTo(OLD_JSON);
		assertThat(page.getSearchText()).isEqualTo("Oud");
		assertThat(page.getCurrentRevision()).isEqualTo(3);
		assertThat(page.getUpdatedBy()).isEqualTo("editor");
		assertThat(page.getUpdatedAt()).isEqualTo(NOW);

		ArgumentCaptor<PageRevision> saved = ArgumentCaptor.forClass(PageRevision.class);
		verify(revisions).save(saved.capture());
		assertThat(saved.getValue().getRevisionNumber()).isEqualTo(3);
		assertThat(saved.getValue().getTitle()).isEqualTo("Oude titel");
		assertThat(saved.getValue().getAuthor()).isEqualTo("editor");
	}

	@Test
	void restoreGoesThroughTheSameValidationAsASave() {
		service.restoreRevision(page.getId(), first.getId(), 1, "editor");

		verify(validator).normalizeTitle("Oude titel");
		verify(validator).validate(any());
	}

	@Test
	void restoreLeavesParentUntouchedAndWritesAnAuditEvent() {
		service.restoreRevision(page.getId(), first.getId(), 1, "editor");

		assertThat(page.getParentId()).isEqualTo(parentId);
		assertThat(page.isDeleted()).isFalse();

		ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
		verify(audit).save(event.capture());
		assertThat(event.getValue().getAction()).isEqualTo(AuditEvent.RESTORE_REVISION);
		assertThat(event.getValue().getPageId()).isEqualTo(page.getId());
		assertThat(event.getValue().getDetails()).contains("versie 1").contains("versie 3");
		assertThat(event.getValue().getDetails()).doesNotContain("Oud"); // never document content
	}

	@Test
	void restoringTheCurrentRevisionChangesNothing() {
		PageService.RevisionRestore result = service.restoreRevision(page.getId(), second.getId(), 1, "editor");

		assertThat(result.changed()).isFalse();
		assertThat(page.getCurrentRevision()).isEqualTo(2);
		assertThat(page.getUpdatedBy()).isEqualTo("mark");
		verify(revisions, never()).save(any());
		verify(audit, never()).save(any());
	}

	@Test
	void staleVersionIsRejectedBeforeAnythingChanges() {
		assertThatThrownBy(() -> service.restoreRevision(page.getId(), first.getId(), 0, "editor"))
			.isInstanceOf(PageConflictException.class)
			.hasMessageContaining("huidige versie 1");

		assertThat(page.getTitle()).isEqualTo("Nieuwe titel");
		assertThat(page.getCurrentRevision()).isEqualTo(2);
		verify(revisions, never()).save(any());
	}

	@Test
	void revisionThatDoesNotBelongToThePageIsNotFound() {
		UUID foreign = UUID.randomUUID();
		when(revisions.findByIdAndPageId(foreign, page.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.restoreRevision(page.getId(), foreign, 1, "editor"))
			.isInstanceOf(PageNotFoundException.class);
		assertThat(page.getCurrentRevision()).isEqualTo(2);
	}

}
