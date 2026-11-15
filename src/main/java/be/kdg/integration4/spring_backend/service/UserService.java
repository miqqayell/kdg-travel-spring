package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.web.dto.UserListItem;
import be.kdg.integration4.spring_backend.domain.FriendRequest;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.UserRelation;
import be.kdg.integration4.spring_backend.domain.enums.RequestStatus;
import be.kdg.integration4.spring_backend.repository.FriendRequestRepository;
import be.kdg.integration4.spring_backend.repository.UserRelationRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserRelationRepository userRelationRepository;
    private final FriendRequestRepository friendRequestRepository;

    @Transactional(readOnly = true)
    public List<UserListItem> getUsersForDirectory(String searchQuery, String currentUserOriginalId) {
        String normalizedQuery = searchQuery == null ? "" : searchQuery.trim();
        User currentUser = userRepository.findByOriginalId(currentUserOriginalId).orElseThrow();

        List<User> users = normalizedQuery.isBlank()
                ? userRepository.findAll()
                : userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(normalizedQuery, normalizedQuery);

        return users.stream()
                .sorted(Comparator
                        .comparing((User user) -> !user.getId().equals(currentUser.getId()))
                        .thenComparing(user -> user.getUsername().toLowerCase())
                        .thenComparing(user -> user.getEmail().toLowerCase()))
                .map(user -> new UserListItem(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getProfilePictureUrl(),
                        user.getId().equals(currentUser.getId()),
                        determineRelationStatus(currentUser, user)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(String originalUserId) {
        return userRepository.findByOriginalId(originalUserId)
                .orElseThrow(() -> new IllegalStateException("Logged in user not found in database"));
    }

    public String getRelationStatus(String currentOriginalId, Long targetUserId) {
        User currentUser = userRepository.findByOriginalId(currentOriginalId).orElseThrow();
        User targetUser = userRepository.findById(targetUserId).orElseThrow();
        return determineRelationStatus(currentUser, targetUser);
    }

    // Helper method to figure out the relationship between the logged-in user and the searched user
    private String determineRelationStatus(User currentUser, User targetUser) {
        if (currentUser.getId().equals(targetUser.getId())) return "SELF";

        // 1. Are they already friends or blocked?
        Optional<UserRelation> relation = userRelationRepository.findByUsers(currentUser.getId(), targetUser.getId());
        if (relation.isPresent()) {
            return relation.get().getStatus().name(); // Returns "FRIENDS" or "BLOCKED"
        }

        // 2. Did I send them a request?
        Optional<FriendRequest> sent = friendRequestRepository.findBySenderIdAndRecipientIdAndStatus(currentUser.getId(), targetUser.getId(), RequestStatus.PENDING);
        if (sent.isPresent()) return "PENDING_SENT";

        // 3. Did they send me a request? (I need to accept it!)
        Optional<FriendRequest> received = friendRequestRepository.findBySenderIdAndRecipientIdAndStatus(targetUser.getId(), currentUser.getId(), RequestStatus.PENDING);
        if (received.isPresent()) return "PENDING_RECEIVED_" + received.get().getId(); // Pass the request ID so the UI can accept it

        return "NONE";
    }
}
