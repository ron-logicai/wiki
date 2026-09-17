package nl.logicai.wiki.repositories;

import nl.logicai.wiki.models.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageRepository extends JpaRepository<Page, UUID> {

	Optional<Page> findByIdAndDeletedAtIsNull(UUID id);

	List<Page> findByDeletedAtIsNullOrderByTitleAsc();

	List<Page> findByParentIdIsNullAndDeletedAtIsNullOrderByTitleAsc();

	List<Page> findByParentIdAndDeletedAtIsNullOrderByTitleAsc(UUID parentId);

	List<Page> findTop10ByDeletedAtIsNullOrderByUpdatedAtDesc();

	List<Page> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

	boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);

	/**
	 * Full-text search on title and content (spec F-13). The 'simple' configuration matches whole
	 * words without stemming, so technical terms such as "Spring Boot" and "PostgreSQL" are found
	 * unchanged; the expression matches the GIN index from V2__pages.sql. Deleted pages are excluded.
	 * The optional tag filter is case-insensitive.
	 */
	@Query(value = """
		select p.* from page p
		where p.deleted_at is null
		  and to_tsvector('simple', p.title || ' ' || p.search_text) @@ plainto_tsquery('simple', :q)
		  and (cast(:tag as text) is null or exists (
		        select 1 from page_tag pt join tag t on t.id = pt.tag_id
		        where pt.page_id = p.id and lower(t.name) = lower(cast(:tag as text))))
		order by ts_rank(to_tsvector('simple', p.title || ' ' || p.search_text), plainto_tsquery('simple', :q)) desc,
		         p.updated_at desc
		""", countQuery = """
		select count(*) from page p
		where p.deleted_at is null
		  and to_tsvector('simple', p.title || ' ' || p.search_text) @@ plainto_tsquery('simple', :q)
		  and (cast(:tag as text) is null or exists (
		        select 1 from page_tag pt join tag t on t.id = pt.tag_id
		        where pt.page_id = p.id and lower(t.name) = lower(cast(:tag as text))))
		""", nativeQuery = true)
	org.springframework.data.domain.Page<Page> search(@Param("q") String q, @Param("tag") String tag, Pageable pageable);

	/** Tag names with the number of matching pages, for the filter column (before the tag filter is applied). */
	@Query(value = """
		select t.name, count(*) from tag t
		join page_tag pt on pt.tag_id = t.id
		join page p on p.id = pt.page_id
		where p.deleted_at is null
		  and to_tsvector('simple', p.title || ' ' || p.search_text) @@ plainto_tsquery('simple', :q)
		group by t.name
		order by count(*) desc, lower(t.name)
		""", nativeQuery = true)
	List<Object[]> countTagsInSearch(@Param("q") String q);

}
