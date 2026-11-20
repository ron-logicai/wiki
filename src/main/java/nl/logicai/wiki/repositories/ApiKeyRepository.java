package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.models.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

/** API keys for /api/v1; the delete method runs in the caller's transaction. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

	Optional<ApiKey> findByTokenHash(String tokenHash);

	List<ApiKey> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<ApiKey> findAllByOrderByCreatedAtDesc();

	long countByUserId(UUID userId);

	long deleteByUserId(UUID userId);

}
