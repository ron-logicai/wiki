package nl.logicai.wiki.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.logicai.wiki.models.Role;
import nl.logicai.wiki.models.WikiUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiUserRepository extends JpaRepository<WikiUser, UUID> {

	Optional<WikiUser> findByUsernameIgnoreCase(String username);

	Optional<WikiUser> findByEmailIgnoreCase(String email);

	List<WikiUser> findAllByOrderByUsernameAsc();

	long countByRoleAndActiveTrue(Role role);

	long countByActiveTrue();

}
