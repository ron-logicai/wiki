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

	/** Most recently changed active pages, limited by the pageable (home list, spec section 9: no unbounded lists). */
	List<Page> findByDeletedAtIsNullOrderByUpdatedAtDesc(Pageable pageable);

	/** Title substring match for link suggestions (spec U-06); Spring Data escapes % and _ in the argument. */
	List<Page> findTop10ByDeletedAtIsNullAndTitleContainingIgnoreCaseOrderByTitleAsc(String title);

	List<Page> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

	boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);

	long countByDeletedAtIsNull();

	long countByDeletedAtIsNotNull();

	/**
	 * Full-text search on title and content (spec F-13) through the FULLTEXT index from V2__pages.sql.
	 * {@code q} is a MySQL boolean-mode expression built by {@code SearchService} ("+spring +boot": every
	 * word required, whole words, no stemming), never raw user input. Deleted pages are excluded.
	 * The optional tag filter is case-insensitive.
	 */
	@Query(value = """
		select p.* from page p
		where p.deleted_at is null
		  and match(p.title, p.search_text) against (:q in boolean mode)
		  and (:tag is null or exists (
		        select 1 from page_tag pt join tag t on t.id = pt.tag_id
		        where pt.page_id = p.id and lower(t.name) = lower(:tag)))
		order by match(p.title, p.search_text) against (:q in boolean mode) desc,
		         p.updated_at desc
		""", countQuery = """
		select count(*) from page p
		where p.deleted_at is null
		  and match(p.title, p.search_text) against (:q in boolean mode)
		  and (:tag is null or exists (
		        select 1 from page_tag pt join tag t on t.id = pt.tag_id
		        where pt.page_id = p.id and lower(t.name) = lower(:tag)))
		""", nativeQuery = true)
	org.springframework.data.domain.Page<Page> search(@Param("q") String q, @Param("tag") String tag, Pageable pageable);

	/** Tag names with the number of matching pages, for the filter column (before the tag filter is applied). */
	@Query(value = """
		select t.name, count(*) from tag t
		join page_tag pt on pt.tag_id = t.id
		join page p on p.id = pt.page_id
		where p.deleted_at is null
		  and match(p.title, p.search_text) against (:q in boolean mode)
		group by t.name
		order by count(*) desc, lower(t.name)
		""", nativeQuery = true)
	List<Object[]> countTagsInSearch(@Param("q") String q);

	/**
	 * Substring search for queries the full-text index cannot answer (only words shorter than three
	 * characters or stopwords, see {@code SearchService}). {@code pattern} is a LIKE pattern with '!' as
	 * escape character; both columns are case-insensitive.
	 */
	@Query(value = """
		select p.* from page p
		where p.deleted_at is null
		  and (p.title like :pattern escape '!' or p.search_text like :pattern escape '!')
		  and (:tag is null or exists (
		        select 1 from page_tag pt join tag t on t.id = pt.tag_id
		        where pt.page_id = p.id and lower(t.name) = lower(:tag)))
		order by p.updated_at desc
		""", countQuery = """
		select count(*) from page p
		where p.deleted_at is null
		  and (p.title like :pattern escape '!' or p.search_text like :pattern escape '!')
		  and (:tag is null or exists (
		        select 1 from page_tag pt join tag t on t.id = pt.tag_id
		        where pt.page_id = p.id and lower(t.name) = lower(:tag)))
		""", nativeQuery = true)
	org.springframework.data.domain.Page<Page> searchSubstring(@Param("pattern") String pattern, @Param("tag") String tag,
			Pageable pageable);

	/** The tag counts that go with {@link #searchSubstring}. */
	@Query(value = """
		select t.name, count(*) from tag t
		join page_tag pt on pt.tag_id = t.id
		join page p on p.id = pt.page_id
		where p.deleted_at is null
		  and (p.title like :pattern escape '!' or p.search_text like :pattern escape '!')
		group by t.name
		order by count(*) desc, lower(t.name)
		""", nativeQuery = true)
	List<Object[]> countTagsInSubstringSearch(@Param("pattern") String pattern);

}
