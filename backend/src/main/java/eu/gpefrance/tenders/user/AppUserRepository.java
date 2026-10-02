package eu.gpefrance.tenders.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	List<AppUser> findAllByOrderByDisplayName();

	long countByRoleAndEnabledTrue(Role role);

}
