package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    Optional<User> findByOriginalId(String originalId);

    List<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email);

    long countByStatus(UserStatus status);
}