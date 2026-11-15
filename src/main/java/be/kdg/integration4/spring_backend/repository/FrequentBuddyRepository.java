package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.FrequentBuddy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FrequentBuddyRepository extends JpaRepository<FrequentBuddy, Long> {

    List<FrequentBuddy> findByOwnerIdOrderBySavedAtDesc(Long ownerId);

    Optional<FrequentBuddy> findByOwnerIdAndBuddyId(Long ownerId, Long buddyId);

    boolean existsByOwnerIdAndBuddyId(Long ownerId, Long buddyId);
}
