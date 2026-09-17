package nl.logicai.wiki.services;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.exceptions.InvalidMoveException;
import nl.logicai.wiki.exceptions.PageConflictException;
import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.WikiDocument;
import nl.logicai.wiki.repositories.PageRepository;
import nl.logicai.wiki.repositories.PageRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cycle rules for moving pages (spec F-04): never under itself or one of its descendants. */
class PageServiceMoveTest {

	private static final Instant NOW = Instant.parse("2026-09-17T09:00:00Z");
	private static final WikiDocument EMPTY = new WikiDocument("[]", "", 1);

	private final PageRepository pages = mock(PageRepository.class);
	private final PageRevisionRepository revisions = mock(PageRevisionRepository.class);
	private final PageService service = new PageService(pages, revisions,
		mock(DocumentValidator.class), new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

	/** Tree: root > child > grandchild, plus an unrelated top-level page. */
	private final Page root = page("Projecten", null);
	private final Page child = page("Hotel-app", root.getId());
	private final Page grandchild = page("Databaseontwerp", child.getId());
	private final Page other = page("Onboarding", null);

	@BeforeEach
	void stubRepository() {
		Map<UUID, Page> all = Map.of(root.getId(), root, child.getId(), child,
			grandchild.getId(), grandchild, other.getId(), other);
		when(pages.findByIdAndDeletedAtIsNull(any())).thenAnswer(inv -> Optional.ofNullable(all.get(inv.getArgument(0))));
		when(pages.findById(any())).thenAnswer(inv -> Optional.ofNullable(all.get(inv.getArgument(0))));
		when(pages.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void movingUnderItselfIsRefused() {
		assertThatThrownBy(() -> service.move(root.getId(), root.getId(), 0))
			.isInstanceOf(InvalidMoveException.class)
			.hasMessageContaining("zichzelf");
		assertThat(root.getParentId()).isNull();
	}

	@Test
	void movingUnderOwnDescendantIsRefused() {
		assertThatThrownBy(() -> service.move(root.getId(), grandchild.getId(), 0))
			.isInstanceOf(InvalidMoveException.class)
			.hasMessageContaining("eigen subpagina");
		assertThat(root.getParentId()).isNull();
	}

	@Test
	void movingToAnotherBranchOrTopLevelWorks() {
		service.move(grandchild.getId(), other.getId(), 0);
		assertThat(grandchild.getParentId()).isEqualTo(other.getId());

		service.move(child.getId(), null, 0);
		assertThat(child.getParentId()).isNull();
	}

	@Test
	void staleVersionIsRejected() {
		assertThatThrownBy(() -> service.move(child.getId(), other.getId(), 7))
			.isInstanceOf(PageConflictException.class);
		assertThat(child.getParentId()).isEqualTo(root.getId());
	}

	@Test
	void moveTargetsExcludeThePageAndItsSubtree() {
		when(pages.findByDeletedAtIsNullOrderByTitleAsc()).thenReturn(java.util.List.of(grandchild, child, other, root)); // by title, like the repository

		assertThat(service.moveTargets(child.getId()))
			.extracting(t -> t.page().getTitle())
			.containsExactly("Onboarding", "Projecten");
	}

	private static Page page(String title, UUID parentId) {
		Page page = Page.create(title, parentId, EMPTY, "editor", NOW);
		ReflectionTestUtils.setField(page, "lockVersion", 0L);
		return page;
	}

}
