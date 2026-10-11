package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.PageRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageRelationRepository extends JpaRepository<PageRelation, PageRelation.Key> {

	boolean existsByPageIdAndRelatedId(UUID pageId, UUID relatedId);

	void deleteByPageIdAndRelatedId(UUID pageId, UUID relatedId);

	/** The related pages of a page that are not in the trash, by title. */
	@Query("""
			select p from Page p, PageRelation r
			where r.relatedId = p.id and r.pageId = :pageId and p.deletedAt is null
			order by p.title asc
			""")
	List<Page> findActiveRelatedPages(@Param("pageId") UUID pageId);

}
