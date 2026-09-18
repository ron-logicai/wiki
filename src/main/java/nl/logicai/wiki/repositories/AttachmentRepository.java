package nl.logicai.wiki.repositories;

import java.util.UUID;

import nl.logicai.wiki.models.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
}
