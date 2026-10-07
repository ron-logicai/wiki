package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.models.Page;
import nl.logicai.wiki.models.PageFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface PageFavoriteRepository extends JpaRepository<PageFavorite, PageFavorite.Key>{
    boolean existsByPageIdAndUsername(UUID pageId, String username);
    void deleteByPageIdAndUsername(UUID pageId, String username);
    @Query("""
          select p from Page p, PageFavorite f
          where f.pageId = p.id and f.username = :username and p.deleteAt is null
          order by p.title asc
          """)
    List<Page>findActivePages(@Param("username")String username);

}
