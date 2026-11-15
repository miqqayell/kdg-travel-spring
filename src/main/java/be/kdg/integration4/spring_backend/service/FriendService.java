package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.FrequentBuddy;
import be.kdg.integration4.spring_backend.domain.FriendRequest;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.UserRelation;
import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import be.kdg.integration4.spring_backend.domain.enums.RequestStatus;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.FrequentBuddyRepository;
import be.kdg.integration4.spring_backend.repository.FriendRequestRepository;
import be.kdg.integration4.spring_backend.repository.UserRelationRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRelationRepository userRelationRepository;
    private final UserRepository userRepository;
    private final FrequentBuddyRepository frequentBuddyRepository;
    private final NotificationService notificationService;

    @Transactional
    public void sendFriendRequest(String senderOriginalId, Long recipientId) {
        User sender = getUserByOriginalId(senderOriginalId);
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        if (sender.getId().equals(recipient.getId())) throw new BadRequestException("Cannot add yourself.");
        if (userRelationRepository.findByUsers(sender.getId(), recipient.getId()).isPresent()) throw new BadRequestException("Relation already exists.");
        if (friendRequestRepository.findBySenderIdAndRecipientIdAndStatus(sender.getId(), recipient.getId(), RequestStatus.PENDING).isPresent()) throw new BadRequestException("Request already pending.");

        FriendRequest request = new FriendRequest();
        request.setSender(sender);
        request.setRecipient(recipient);
        request.setStatus(RequestStatus.PENDING);
        request.setRequestSentAt(LocalDateTime.now());
        friendRequestRepository.save(request);

        notificationService.notifyFriendRequest(sender, recipient);
    }

    @Transactional(readOnly = true)
    public boolean areFriends(String currentOriginalId, Long targetUserId) {
        User currentUser = getUserByOriginalId(currentOriginalId);
        return userRelationRepository.findByUsers(currentUser.getId(), targetUserId)
                .filter(relation -> relation.getStatus() == RelationStatus.FRIENDS)
                .isPresent();
    }

    @Transactional
    public void acceptFriendRequest(String currentOriginalId, Long requestId) {
        User currentUser = getUserByOriginalId(currentOriginalId);
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!request.getRecipient().getId().equals(currentUser.getId())) throw new BadRequestException("Not your request.");
        if (request.getStatus() != RequestStatus.PENDING) throw new BadRequestException("Request not pending.");

        // 1. Update request status
        request.setStatus(RequestStatus.ACCEPTED);
        request.setRequestProcessedAt(LocalDateTime.now());
        friendRequestRepository.save(request);

        // 2. Create Bi-directional Friend Link
        User userOne = request.getSender().getId() < request.getRecipient().getId() ? request.getSender() : request.getRecipient();
        User userTwo = request.getSender().getId() < request.getRecipient().getId() ? request.getRecipient() : request.getSender();

        if (userRelationRepository.findByUsers(userOne.getId(), userTwo.getId()).isEmpty()) {
            UserRelation relation = new UserRelation();
            relation.setUserOne(userOne);
            relation.setUserTwo(userTwo);
            relation.setStatus(RelationStatus.FRIENDS);
            relation.setSince(LocalDateTime.now());
            relation.setLocationSharingEnabled(false);
            userRelationRepository.save(relation);
        }
    }

    @Transactional
    public void declineFriendRequest(String currentOriginalId, Long requestId) {
        User currentUser = getUserByOriginalId(currentOriginalId);
        FriendRequest request = friendRequestRepository.findById(requestId).orElseThrow();
        if (!request.getRecipient().getId().equals(currentUser.getId())) throw new BadRequestException("Not your request.");

        request.setStatus(RequestStatus.REJECTED);
        request.setRequestProcessedAt(LocalDateTime.now());
        friendRequestRepository.save(request);
    }

    @Transactional
    public void manageRelation(String currentOriginalId, Long targetUserId, String action) {
        User currentUser = getUserByOriginalId(currentOriginalId);
        UserRelation relation = userRelationRepository.findByUsers(currentUser.getId(), targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));

        if ("BLOCK".equalsIgnoreCase(action)) {
            relation.setStatus(RelationStatus.BLOCKED);
            userRelationRepository.save(relation);
        } else if ("REMOVE".equalsIgnoreCase(action)) {
            userRelationRepository.delete(relation);
        }
    }

    @Transactional
    public void markFrequentBuddy(String currentOriginalId, Long buddyId) {
        User owner = getUserByOriginalId(currentOriginalId);
        if (owner.getId().equals(buddyId)) {
            throw new BadRequestException("Cannot mark yourself as a travel buddy.");
        }
        User buddy = userRepository.findById(buddyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (frequentBuddyRepository.existsByOwnerIdAndBuddyId(owner.getId(), buddy.getId())) {
            return;
        }

        FrequentBuddy frequentBuddy = new FrequentBuddy();
        frequentBuddy.setOwner(owner);
        frequentBuddy.setBuddy(buddy);
        frequentBuddy.setSavedAt(LocalDateTime.now());
        frequentBuddyRepository.save(frequentBuddy);
    }

    @Transactional
    public void removeFrequentBuddy(String currentOriginalId, Long buddyId) {
        User owner = getUserByOriginalId(currentOriginalId);
        frequentBuddyRepository.findByOwnerIdAndBuddyId(owner.getId(), buddyId)
                .ifPresent(frequentBuddyRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isFrequentBuddy(String currentOriginalId, Long buddyId) {
        User owner = getUserByOriginalId(currentOriginalId);
        return frequentBuddyRepository.existsByOwnerIdAndBuddyId(owner.getId(), buddyId);
    }

    @Transactional(readOnly = true)
    public List<User> getFrequentBuddies(String currentOriginalId) {
        User owner = getUserByOriginalId(currentOriginalId);
        return frequentBuddyRepository.findByOwnerIdOrderBySavedAtDesc(owner.getId()).stream()
                .map(FrequentBuddy::getBuddy)
                .toList();
    }

    private User getUserByOriginalId(String originalId) {
        return userRepository.findByOriginalId(originalId).orElseThrow();
    }
}