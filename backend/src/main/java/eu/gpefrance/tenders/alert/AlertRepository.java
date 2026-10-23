package eu.gpefrance.tenders.alert;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, Long> {

	List<Alert> findByUserIdOrderByIdAsc(Long userId);

	Optional<Alert> findByIdAndUserId(Long id, Long userId);

	/**
	 * The saved searches that count for the eligibility of the consultation
	 * files: active, of an account that is enabled. The saved searches of a
	 * disabled account keep their own state, and count again once the account
	 * is enabled.
	 */
	@Query("""
			select a from Alert a
			where a.active = true
			  and exists (select u.id from AppUser u where u.id = a.userId and u.enabled = true)
			order by a.id
			""")
	List<Alert> findActiveOfEnabledUsers();

	List<Alert> findByUserIdAndActiveTrue(Long userId);

	@Query(nativeQuery = true, value = "SELECT revision FROM alert_revision WHERE id = 1")
	long currentRevision();

}
