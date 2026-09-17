package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.models.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

	Optional<Tag> findByNameIgnoreCase(String name);

	List<Tag> findAllByOrderByNameAsc();

	@Query("select t from Tag t join PageTag pt on pt.tagId = t.id where pt.pageId = :pageId order by lower(t.name)")
	List<Tag> findByPageId(@Param("pageId") UUID pageId);

	/** Tag id and the number of active (not deleted) pages carrying it. */
	@Query("""
		select t.id, count(p.id) from Tag t
		left join PageTag pt on pt.tagId = t.id
		left join Page p on p.id = pt.pageId and p.deletedAt is null
		group by t.id
		""")
	List<Object[]> countActivePagesPerTag();

}
