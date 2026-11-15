package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatIdOrderByTimeAsc(Long chatId);
}