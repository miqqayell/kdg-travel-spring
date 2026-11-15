package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.PublicTransportSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicTransportSelectionRepository extends JpaRepository<PublicTransportSelection, Long> {
    Optional<PublicTransportSelection> findByTravelGroupId(Long travelGroupId);
}
