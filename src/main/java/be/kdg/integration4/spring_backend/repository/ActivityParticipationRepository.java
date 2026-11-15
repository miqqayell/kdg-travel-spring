package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.ActivityParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityParticipationRepository extends JpaRepository<ActivityParticipation, Long> {
    // Finds everyone participating in a specific activity
    List<ActivityParticipation> findByActivityId(Long activityId);

    List<ActivityParticipation> findByActivityIdInAndUser_IdIn(Collection<Long> activityIds, Collection<Long> userIds);

    boolean existsByActivityIdAndUser_Id(Long activityId, Long userId);

    Optional<ActivityParticipation> findByActivityIdAndUser_Id(Long activityId, Long userId);
}
