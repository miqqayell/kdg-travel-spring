package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
}