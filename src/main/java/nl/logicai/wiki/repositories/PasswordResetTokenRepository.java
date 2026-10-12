package nl.logicai.wiki.repositories;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.models.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reset tokens for "wachtwoord vergeten"; the delete methods run in the caller's transaction. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	boolean existsByUserIdAndCreatedAtAfter(UUID userId, Instant since);

	void deleteByUserId(UUID userId);

	void deleteByExpiresAtBefore(Instant now);

}
