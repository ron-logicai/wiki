package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.UUID;

import nl.logicai.wiki.models.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

	List<AuditEvent> findByPageIdOrderByOccurredAtDesc(UUID pageId);

	List<AuditEvent> findTop20ByOrderByOccurredAtDesc();

}
