package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.models.PageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageTemplateRepository extends JpaRepository<PageTemplate, UUID> {

	List<PageTemplate> findAllByOrderByTitleAsc();

}
