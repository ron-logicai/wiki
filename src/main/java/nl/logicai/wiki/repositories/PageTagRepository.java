package nl.logicai.wiki.repositories;

import java.util.UUID;

import nl.logicai.wiki.models.PageTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageTagRepository extends JpaRepository<PageTag, PageTag.Key> {

	boolean existsByPageIdAndTagId(UUID pageId, UUID tagId);

	void deleteByPageIdAndTagId(UUID pageId, UUID tagId);

	void deleteByTagId(UUID tagId);

}
