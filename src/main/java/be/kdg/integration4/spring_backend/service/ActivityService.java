package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.web.dto.ActivityFriendAttendanceSummary;
import be.kdg.integration4.spring_backend.controller.web.dto.ActivityOverviewItem;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.ActivityParticipation;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.UserRelation;
import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import be.kdg.integration4.spring_backend.mappers.ActivityMapper;
import be.kdg.integration4.spring_backend.repository.ActivityParticipationRepository;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import be.kdg.integration4.spring_backend.repository.UserRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final NotificationService notificationService;
    private final ActivityParticipationRepository participationRepository;
    private final TravelGroupRepository travelGroupRepository;
    private final UserRelationRepository userRelationRepository;
    private final ActivityMapper activityMapper;
    private final CoordinateDistanceCalculator distanceCalculator;

    @Transactional(readOnly = true)
    public List<Activity> getAllActivities() {
        return activityRepository.findAll();
    }

    @Transactional
    public Activity createActivity(Activity activity) {
        activity.setVerificationStatus(VerificationStatus.PENDING);
        Activity savedActivity = activityRepository.save(activity);
        ensureParticipation(savedActivity, savedActivity.getCreatedByUser());
        return savedActivity;
    }

    @Transactional
    public Activity updateActivity(Activity activity) {
        Activity savedActivity = activityRepository.save(activity);
        notificationService.notifyParticipantsOnActivityUpdate(savedActivity);
        return savedActivity;
    }

    @Transactional
    public void deleteActivity(Activity activity) {
        activityRepository.delete(activity);
    }

    @Transactional
    public void deleteActivityById(Long id) {
        Activity activity = getActivityById(id);
        if (activity != null) {
            notificationService.notifyActivityCancelled(activity);
            activityRepository.delete(activity);
        }
    }

    @Transactional(readOnly = true)
    public Activity getActivityById(Long id) {
        return activityRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ActivityOverviewItem> getUpcomingActivities(Long currentUserId) {
        return searchUpcomingActivities(currentUserId, new ActivityFilterCriteria(
                null, null, null, null, null, null, null, false
        ), false, false);
    }

    @Transactional(readOnly = true)
    public List<ActivityOverviewItem> searchUpcomingActivities(Long currentUserId,
                                                               ActivityFilterCriteria criteria,
                                                               boolean createdEvents,
                                                               boolean joinedGroupEvents) {
        ActivityFilterCriteria activeCriteria = criteria != null ? criteria : new ActivityFilterCriteria(
                null, null, null, null, null, null, null, false
        );
        List<Activity> activities = activityRepository.findAllByOrderByStartTimeDescIdAsc();

        Set<Long> joinedGroupActivityIds = currentUserId == null || !joinedGroupEvents
                ? Set.of()
                : new HashSet<>(travelGroupRepository.findActivityIdsForUserAndStatus(currentUserId, TravelGroupStatus.ACTIVE));

        List<Activity> matchingActivities = activities.stream()
                .filter(activity -> canSeeActivity(activity, currentUserId))
                .filter(activity -> matches(activeCriteria, activity))
                .filter(activity -> matchesEventChips(activity, currentUserId, joinedGroupActivityIds, createdEvents, joinedGroupEvents))
                .toList();

        if (matchingActivities.isEmpty()) {
            return List.of();
        }

        List<Long> activityIds = matchingActivities.stream()
                .map(Activity::getId)
                .toList();

        Map<Long, ActivityFriendAttendanceSummary> friendSummaries = currentUserId == null
                ? Map.of()
                : getFriendAttendanceSummaries(activityIds, currentUserId);

        if (Boolean.TRUE.equals(activeCriteria.friendsOnly())) {
            matchingActivities = matchingActivities.stream()
                    .filter(activity -> friendSummaries
                            .getOrDefault(activity.getId(), ActivityFriendAttendanceSummary.empty())
                            .hasAttendingFriends())
                    .toList();
            activityIds = matchingActivities.stream()
                    .map(Activity::getId)
                    .toList();
        }

        if (matchingActivities.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> activeTravelGroupCounts = travelGroupRepository
                .countByActivityIdsAndStatus(activityIds, TravelGroupStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        TravelGroupRepository.ActivityTravelGroupCount::getActivityId,
                        TravelGroupRepository.ActivityTravelGroupCount::getGroupCount
                ));

        return matchingActivities.stream()
                .map(activity -> new ActivityOverviewItem(
                        activityMapper.toDto(activity),
                        friendSummaries.getOrDefault(activity.getId(), ActivityFriendAttendanceSummary.empty()),
                        activeTravelGroupCounts.getOrDefault(activity.getId(), 0L)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActivityFriendAttendanceSummary getFriendAttendanceSummary(Long activityId, Long currentUserId) {
        return getFriendAttendanceSummaries(List.of(activityId), currentUserId)
                .getOrDefault(activityId, ActivityFriendAttendanceSummary.empty());
    }

    private boolean matches(ActivityFilterCriteria criteria, Activity activity) {
        if (criteria == null) return true;

        return matchesKeyword(criteria.keyword(), activity)
                && matchesStartRange(criteria.startsAfter(), criteria.startsBefore(), activity)
                && matchesLocation(criteria, activity);
    }

    private boolean matchesKeyword(String keyword, Activity activity) {
        if (keyword == null || keyword.isBlank()) return true;

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(activity.getTitle(), normalizedKeyword)
                || containsIgnoreCase(activity.getDescription(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private boolean matchesEventChips(Activity activity,
                                      Long currentUserId,
                                      Set<Long> joinedGroupActivityIds,
                                      boolean createdEvents,
                                      boolean joinedGroupEvents) {
        if (!createdEvents && !joinedGroupEvents) return true;
        boolean createdByCurrentUser = createdEvents && isActivityCreator(activity, currentUserId);
        boolean hasJoinedGroup = joinedGroupEvents && joinedGroupActivityIds.contains(activity.getId());
        return createdByCurrentUser || hasJoinedGroup;
    }

    private boolean matchesStartRange(LocalDateTime startsAfter, LocalDateTime startsBefore, Activity activity) {
        LocalDateTime startTime = activity.getStartTime();
        if (startTime == null) return startsAfter == null && startsBefore == null;
        if (startsAfter != null && startTime.isBefore(startsAfter)) return false;
        return startsBefore == null || !startTime.isAfter(startsBefore);
    }

    private boolean matchesLocation(ActivityFilterCriteria criteria, Activity activity) {
        if (criteria.locationLatitude() == null
                || criteria.locationLongitude() == null
                || criteria.locationProximityMeters() == null) {
            return true;
        }

        if (activity.getLocationLatitude() == null || activity.getLocationLongitude() == null) {
            return false;
        }

        double distance = distanceCalculator.distanceInMeters(
                criteria.locationLatitude(),
                criteria.locationLongitude(),
                activity.getLocationLatitude(),
                activity.getLocationLongitude()
        );
        return distance <= criteria.locationProximityMeters();
    }

    @Transactional(readOnly = true)
    public Map<Long, ActivityFriendAttendanceSummary> getFriendAttendanceSummaries(Collection<Long> activityIds, Long currentUserId) {
        if (currentUserId == null || activityIds.isEmpty()) return Map.of();

        Set<Long> friendIds = userRelationRepository.findUserFriends(currentUserId, RelationStatus.FRIENDS)
                .stream()
                .map(relation -> extractFriendId(relation, currentUserId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (friendIds.isEmpty()) return Map.of();

        Comparator<ActivityParticipation> participationComparator = Comparator
                .comparing(ActivityParticipation::getJoinedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(participation -> participation.getUser().getUsername(), String.CASE_INSENSITIVE_ORDER);

        Map<Long, LinkedHashMap<Long, ActivityFriendAttendanceSummary.Attendee>> attendeesByActivityId = new HashMap<>();

        participationRepository.findByActivityIdInAndUser_IdIn(activityIds, friendIds)
                .stream()
                .sorted(participationComparator)
                .forEach(participation -> attendeesByActivityId
                        .computeIfAbsent(participation.getActivity().getId(), ignored -> new LinkedHashMap<>())
                        .putIfAbsent(
                                participation.getUser().getId(),
                                new ActivityFriendAttendanceSummary.Attendee(
                                        participation.getUser().getId(),
                                        participation.getUser().getUsername(),
                                        participation.getUser().getProfilePictureUrl()
                                )
                        ));

        return attendeesByActivityId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ActivityFriendAttendanceSummary(entry.getValue().values().stream().toList())
                ));
    }

    @Transactional
    public void ensureParticipation(Activity activity, User user) {
        if (activity == null || user == null) return;
        if (participationRepository.existsByActivityIdAndUser_Id(activity.getId(), user.getId())) return;

        ActivityParticipation participation = new ActivityParticipation();
        participation.setActivity(activity);
        participation.setUser(user);
        participation.setJoinedAt(LocalDateTime.now());
        participationRepository.save(participation);

        notifyBuddyMatches(activity, user);
    }

    /**
     * When a user joins an upcoming activity, notify any friends already attending
     * (and the user themselves) that they have a potential travel buddy on the same
     * route and timing — so they hear about the match without browsing the site.
     */
    private void notifyBuddyMatches(Activity activity, User newParticipant) {
        LocalDateTime effectiveEnd = activity.getEndTime() != null
                ? activity.getEndTime()
                : (activity.getStartTime() != null ? activity.getStartTime().plusHours(2) : null);
        if (effectiveEnd != null && effectiveEnd.isBefore(LocalDateTime.now())) {
            return;
        }

        Set<Long> friendIds = new HashSet<>();
        for (UserRelation relation : userRelationRepository.findUserFriends(newParticipant.getId(), RelationStatus.FRIENDS)) {
            Long friendId = extractFriendId(relation, newParticipant.getId());
            if (friendId != null) {
                friendIds.add(friendId);
            }
        }
        if (friendIds.isEmpty()) {
            return;
        }

        for (ActivityParticipation friendParticipation :
                participationRepository.findByActivityIdInAndUser_IdIn(List.of(activity.getId()), friendIds)) {
            User buddy = friendParticipation.getUser();
            if (Objects.equals(buddy.getId(), newParticipant.getId())) {
                continue;
            }
            notificationService.notifyBuddyMatch(buddy, newParticipant, activity);
            notificationService.notifyBuddyMatch(newParticipant, buddy, activity);
        }
    }

    @Transactional
    public void synchronizeParticipation(Activity activity, User user, boolean hasTravelPlanForActivity) {
        if (activity == null || user == null) return;

        boolean shouldParticipate = hasTravelPlanForActivity || isActivityCreator(activity, user);

        if (shouldParticipate) {
            ensureParticipation(activity, user);
            return;
        }

        participationRepository.findByActivityIdAndUser_Id(activity.getId(), user.getId())
                .ifPresent(participationRepository::delete);
    }

    private boolean isActivityCreator(Activity activity, User user) {
        return activity.getCreatedByUser() != null
                && Objects.equals(activity.getCreatedByUser().getId(), user.getId());
    }

    private boolean isActivityCreator(Activity activity, Long currentUserId) {
        return currentUserId != null
                && activity.getCreatedByUser() != null
                && Objects.equals(activity.getCreatedByUser().getId(), currentUserId);
    }

    private Long extractFriendId(UserRelation relation, Long currentUserId) {
        if (relation.getUserOne() != null && Objects.equals(relation.getUserOne().getId(), currentUserId)) {
            return relation.getUserTwo() != null ? relation.getUserTwo().getId() : null;
        }
        if (relation.getUserTwo() != null && Objects.equals(relation.getUserTwo().getId(), currentUserId)) {
            return relation.getUserOne() != null ? relation.getUserOne().getId() : null;
        }
        return null;
    }

    private boolean canSeeActivity(Activity activity, Long currentUserId) {
        if (activity.getVerificationStatus() == VerificationStatus.VERIFIED) {
            return true;
        }

        if (activity.getVerificationStatus() == VerificationStatus.PENDING) {
            return true;
        }

        return activity.getCreatedByUser() != null
                && Objects.equals(activity.getCreatedByUser().getId(), currentUserId);
    }
}
