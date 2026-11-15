package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.TravelGroupMembership;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TravelGroupMembershipRepository extends JpaRepository<TravelGroupMembership, Long> {
    // Checks if the user has already joined
    boolean existsByTravelGroupIdAndUserId(Long travelGroupId, Long userId);

    // Finds the specific membership record so we can delete it when they leave
    Optional<TravelGroupMembership> findByTravelGroupIdAndUserId(Long travelGroupId, Long userId);

    long countByUserIdAndTravelGroupStatus(Long userId, TravelGroupStatus status);

    @EntityGraph(attributePaths = {"travelGroup", "travelGroup.memberships"})
    List<TravelGroupMembership> findByUserIdAndTravelGroupStatus(Long userId, TravelGroupStatus status);

    @EntityGraph(attributePaths = {"travelGroup", "travelGroup.memberships"})
    List<TravelGroupMembership> findByUserIdAndTravelGroupStatusAndTravelGroupMeetupTimeBefore(
            Long userId,
            TravelGroupStatus status,
            LocalDateTime now
    );

    List<TravelGroupMembership> findByTravelGroupId(Long travelGroupId);
}