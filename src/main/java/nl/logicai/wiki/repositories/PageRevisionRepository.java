package nl.logicai.wiki.repositories;

import nl.logicai.wiki.models.PageRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRevisionRepository extends JpaRepository<PageRevision, UUID> {

	List<PageRevision> findByPageIdOrderByRevisionNumberDesc(UUID pageId);

	Optional<PageRevision> findByIdAndPageId(UUID id, UUID pageId);

}
