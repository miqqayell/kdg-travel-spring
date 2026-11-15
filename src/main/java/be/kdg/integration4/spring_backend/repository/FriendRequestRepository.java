package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.FriendRequest;
import be.kdg.integration4.spring_backend.domain.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    List<FriendRequest> findByRecipientIdAndStatus(Long recipientId, RequestStatus status);
    Optional<FriendRequest> findBySenderIdAndRecipientIdAndStatus(Long senderId, Long recipientId, RequestStatus status);
}