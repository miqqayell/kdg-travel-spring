package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.web.dto.SuggestedTravelFriendDto;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.ActivityParticipation;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.UserRelation;
import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import be.kdg.integration4.spring_backend.repository.ActivityParticipationRepository;
import be.kdg.integration4.spring_backend.repository.UserRelationRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FriendSuggestionService {

    private static final int MAX_SUGGESTIONS = 10;

    private final ActivityParticipationRepository participationRepository;
    private final UserRelationRepository userRelationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SuggestedTravelFriendDto> suggestForActivity(User currentUser, Activity activity) {
        if (currentUser == null || activity == null || isPast(activity)) {
            return List.of();
        }

        Set<Long> friendIds = friendIdsOf(currentUser.getId());
        if (friendIds.isEmpty()) {
            return List.of();
        }

        Set<Long> alreadyParticipating = new HashSet<>();
        for (ActivityParticipation p : participationRepository
                .findByActivityIdInAndUser_IdIn(List.of(activity.getId()), friendIds)) {
            alreadyParticipating.add(p.getUser().getId());
        }

        List<SuggestedTravelFriendDto> suggestions = new ArrayList<>();
        for (User friend : userRepository.findAllById(friendIds)) {
            if (alreadyParticipating.contains(friend.getId())) {
                continue;
            }
            suggestions.add(new SuggestedTravelFriendDto(
                    friend.getId(),
                    friend.getUsername(),
                    friend.getProfilePictureUrl(),
                    null,
                    null,
                    false,
                    "Invite this friend to travel together"
            ));
        }

        suggestions.sort(Comparator.comparing(
                SuggestedTravelFriendDto::username,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        return suggestions.size() > MAX_SUGGESTIONS
                ? suggestions.subList(0, MAX_SUGGESTIONS)
                : suggestions;
    }

    private boolean isPast(Activity activity) {
        LocalDateTime reference = activity.getEndTime() != null
                ? activity.getEndTime()
                : (activity.getStartTime() != null ? activity.getStartTime().plusHours(2) : null);
        return reference != null && reference.isBefore(LocalDateTime.now());
    }

    private Set<Long> friendIdsOf(Long userId) {
        Set<Long> ids = new HashSet<>();
        for (UserRelation relation : userRelationRepository.findUserFriends(userId, RelationStatus.FRIENDS)) {
            Long otherId = relation.getUserOne().getId().equals(userId)
                    ? relation.getUserTwo().getId()
                    : relation.getUserOne().getId();
            ids.add(otherId);
        }
        return ids;
    }
}