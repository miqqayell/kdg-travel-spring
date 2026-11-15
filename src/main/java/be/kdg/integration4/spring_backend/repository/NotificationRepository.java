package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Finds all notifications for a specific user, sorted from newest to oldest
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long userId);
}
