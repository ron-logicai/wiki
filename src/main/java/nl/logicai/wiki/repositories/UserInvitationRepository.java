package nl.logicai.wiki.repositories;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.models.UserInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Invitations for new accounts; the delete methods run in the caller's transaction. */
public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

	Optional<UserInvitation> findByTokenHash(String tokenHash);

	/** The e-mail address is stored normalised (lower case), so an exact match is enough. */
	void deleteByEmail(String email);

	void deleteByExpiresAtBefore(Instant now);

}
