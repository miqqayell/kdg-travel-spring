package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<Activity> findAllByOrderByStartTimeDescIdAsc();

    List<Activity> findByStartTimeAfterOrderByStartTimeAscIdAsc(LocalDateTime startTime);

    long countByVerificationStatus(VerificationStatus status);

    List<Activity> findByVerificationStatusOrderByStartTimeAscIdAsc(VerificationStatus status);

    List<Activity> findAllByOrderByStartTimeAscIdAsc();
}
