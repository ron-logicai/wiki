package nl.logicai.wiki.repositories;

import nl.logicai.wiki.models.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRepository extends JpaRepository<Page, UUID> {

	Optional<Page> findByIdAndDeletedAtIsNull(UUID id);

	List<Page> findByDeletedAtIsNullOrderByTitleAsc();

	List<Page> findByParentIdIsNullAndDeletedAtIsNullOrderByTitleAsc();

	List<Page> findByParentIdAndDeletedAtIsNullOrderByTitleAsc(UUID parentId);

	List<Page> findTop10ByDeletedAtIsNullOrderByUpdatedAtDesc();

}
