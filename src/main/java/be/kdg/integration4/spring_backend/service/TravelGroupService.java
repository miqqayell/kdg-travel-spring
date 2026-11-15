package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupResponse;
import be.kdg.integration4.spring_backend.controller.web.dto.TravelGroupMemberItem;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.PublicTransportSelection;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.TravelGroupMembership;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.UserRelation;
import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.dto.transit.SuggestedRoute;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupMembershipRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import be.kdg.integration4.spring_backend.repository.UserRelationRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.transit.TransitRouteQuery;
import be.kdg.integration4.spring_backend.service.transit.TransitRouteSuggestionService;
import be.kdg.integration4.spring_backend.service.transit.TransitSuggestionResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TravelGroupService {
    private static final Logger logger = LoggerFactory.getLogger(TravelGroupService.class);

    private final TravelGroupRepository travelGroupRepository;
    private final ActivityService activityService;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final TravelGroupMembershipRepository travelGroupMembershipRepository;
    private final UserRelationRepository userRelationRepository;
    private final CoordinateDistanceCalculator coordinateDistanceCalculator;
    private final PublicTransportSelectionService publicTransportSelectionService;
    private final TransitRouteSuggestionService transitRouteSuggestionService;

    @Transactional(readOnly = true)
    public TravelGroupResponse getGroupById(Long groupId) {
        TravelGroup group = travelGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Travel group not found"));

        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public boolean isGroupCreator(Long groupId, String originalUserId) {
        User user = getUserByOriginalId(originalUserId);
        return travelGroupRepository.findById(groupId)
                .map(g -> g.getCreatedByUser().getId().equals(user.getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isUserMember(Long groupId, String originalUserId) {
        User user = getUserByOriginalId(originalUserId);
        return travelGroupMembershipRepository.existsByTravelGroupIdAndUserId(groupId, user.getId());
    }

    @Transactional(readOnly = true)
    public List<TravelGroupMemberItem> getGroupMembers(Long groupId, String originalUserId) {
        TravelGroup group = travelGroupRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Travel group not found"));

        User currentUser = StringUtils.hasText(originalUserId) ? getUserByOriginalId(originalUserId) : null;
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        Set<Long> friendIds = currentUser != null ? getFriendIds(currentUser.getId()) : Set.of();
        Long creatorId = group.getCreatedByUser().getId();

        return group.getMemberships().stream()
                .map(membership -> toMemberItem(membership, currentUserId, friendIds, creatorId))
                .sorted(memberComparator())
                .toList();
    }

    @Transactional
    public void joinGroup(Long groupId, String originalUserId) {
        User user = getUserByOriginalId(originalUserId);
        TravelGroup group = travelGroupRepository.findById(groupId)
                .filter(g -> g.getStatus() == TravelGroupStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Travel group not found"));

        if (group.getCreatedByUser().getId().equals(user.getId())) {
            throw new BadRequestException("You are the creator of this group.");
        }

        group.addMember(user, LocalDateTime.now());
        travelGroupRepository.save(group);
        activityService.ensureParticipation(group.getActivity(), user);
    }

    @Transactional
    public void leaveGroup(Long groupId, String originalUserId) {
        User user = getUserByOriginalId(originalUserId);
        TravelGroup group = getActiveGroup(groupId);

        if (group.getCreatedByUser().getId().equals(user.getId())) {
            throw new BadRequestException("Group creators cannot leave their own travel group.");
        }

        TravelGroupMembership membership = travelGroupMembershipRepository.findByTravelGroupIdAndUserId(groupId, user.getId())
                .orElseThrow(() -> new BadRequestException("You are not a member of this group."));

        travelGroupMembershipRepository.delete(membership);
        travelGroupMembershipRepository.flush();
        reconcileParticipation(group.getActivity(), user);
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> getGroupsForActivity(Long activityId) {
        ensureActivityExists(activityId);

        return travelGroupRepository.findByActivityIdAndStatusOrderById(activityId, TravelGroupStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> getMyGroups(String originalUserId) {
        User currentUser = getUserByOriginalId(originalUserId);

        return travelGroupRepository.findByCreatedByUserIdAndStatusOrderById(currentUser.getId(), TravelGroupStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchMyGroups(String originalUserId, TravelGroupFilterCriteria criteria) {
        User currentUser = getUserByOriginalId(originalUserId);

        List<TravelGroup> candidateGroups = findMyGroupsForFiltering(currentUser, criteria);

        return filterAndMap(candidateGroups, criteria);
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> getCreatedGroups(String originalUserId, String sortOption) {
        User currentUser = getUserByOriginalId(originalUserId);

        return travelGroupRepository.findByCreatedByUserIdAndStatusOrderById(currentUser.getId(), TravelGroupStatus.ACTIVE)
                .stream()
                .sorted(travelGroupComparator(sortOption))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> getJoinedGroupsExcludingCreatedGroups(String originalUserId, String sortOption) {
        User currentUser = getUserByOriginalId(originalUserId);

        return travelGroupRepository.findJoinedGroupsExcludingCreatedGroups(currentUser.getId(), TravelGroupStatus.ACTIVE)
                .stream()
                .sorted(travelGroupComparator(sortOption))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchCreatedGroups(String originalUserId, TravelGroupFilterCriteria criteria, String sortOption) {
        User currentUser = getUserByOriginalId(originalUserId);
        List<TravelGroup> candidateGroups = findMyGroupsForFiltering(currentUser, criteria);

        return filterAndMap(candidateGroups, criteria, sortOption);
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchJoinedGroupsExcludingCreatedGroups(String originalUserId,
                                                                              TravelGroupFilterCriteria criteria,
                                                                              String sortOption) {
        User currentUser = getUserByOriginalId(originalUserId);
        List<TravelGroup> candidateGroups = findJoinedGroupsForFiltering(currentUser, criteria);

        return filterAndMap(candidateGroups, criteria, sortOption);
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchCreatedAndJoinedGroups(String originalUserId,
                                                                  TravelGroupFilterCriteria criteria,
                                                                  String sortOption) {
        User currentUser = getUserByOriginalId(originalUserId);
        LinkedHashMap<Long, TravelGroup> candidateGroups = new LinkedHashMap<>();

        findMyGroupsForFiltering(currentUser, criteria).forEach(group -> candidateGroups.put(group.getId(), group));
        findJoinedGroupsForFiltering(currentUser, criteria).forEach(group -> candidateGroups.put(group.getId(), group));

        return filterAndMap(candidateGroups.values().stream().toList(), criteria, sortOption);
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchAllGroups(String originalUserId, TravelGroupFilterCriteria criteria, String sortOption) {
        List<TravelGroup> candidateGroups = findAllGroupsForFiltering(originalUserId, criteria);

        return filterAndMap(candidateGroups, criteria, sortOption);
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchGroupsForActivity(Long activityId, String originalUserId, TravelGroupFilterCriteria criteria) {
        return searchGroupsForActivity(activityId, originalUserId, criteria, "dateAsc");
    }

    @Transactional(readOnly = true)
    public List<TravelGroupResponse> searchGroupsForActivity(Long activityId, String originalUserId, TravelGroupFilterCriteria criteria, String sortOption) {
        ensureActivityExists(activityId);
        List<TravelGroup> candidateGroups = findActivityGroupsForFiltering(activityId, originalUserId, criteria);

        return filterAndMap(candidateGroups, criteria, sortOption);
    }

    @Transactional(readOnly = true)
    public TravelGroupResponse getMyGroupById(Long groupId, String originalUserId) {
        User currentUser = getUserByOriginalId(originalUserId);
        TravelGroup group = getOwnedActiveGroup(groupId, currentUser.getId());

        return toResponse(group);
    }

    @Transactional
    public TravelGroupResponse createGroup(TravelGroupRequest request, String originalUserId) {
        validateRequest(request);

        User creator = getUserByOriginalId(originalUserId);
        Activity activity = resolveActivity(request.getActivityId());

        TravelGroup travelGroup = TravelGroup.create(
                request.getTitle(),
                request.getDescription(),
                request.getStartPointLatitude(),
                request.getStartPointLongitude(),
                request.getEndPointLatitude(),
                request.getEndPointLongitude(),
                request.getTransportationMode(),
                request.getDistanceKm(),
                request.getMeetupTime(),
                request.getMaxSize(),
                Boolean.TRUE.equals(request.getContributionRequired()),
                creator,
                activity,
                LocalDateTime.now()
        );

        syncPublicTransportSelection(request, travelGroup);
        TravelGroup savedGroup = travelGroupRepository.save(travelGroup);
        activityService.ensureParticipation(savedGroup.getActivity(), creator);
        return toResponse(savedGroup);
    }

    @Transactional(readOnly = true)
    public PublicTransportSelection getPublicTransportSelection(Long travelGroupId) {
        return publicTransportSelectionService.getSelectionForGroup(travelGroupId).orElse(null);
    }

    @Transactional
    public TravelGroupResponse updateGroup(Long groupId, TravelGroupRequest request, String originalUserId) {
        validateRequest(request);

        User currentUser = getUserByOriginalId(originalUserId);
        TravelGroup group = getOwnedActiveGroup(groupId, currentUser.getId());
        Activity previousActivity = group.getActivity();
        List<User> participants = getParticipants(group);
        Activity activity = resolveActivity(request.getActivityId());

        group.updateDetails(
                request.getTitle(),
                request.getDescription(),
                request.getStartPointLatitude(),
                request.getStartPointLongitude(),
                request.getEndPointLatitude(),
                request.getEndPointLongitude(),
                request.getTransportationMode(),
                request.getDistanceKm(),
                request.getMeetupTime(),
                request.getMaxSize(),
                Boolean.TRUE.equals(request.getContributionRequired()),
                activity

        );
        syncPublicTransportSelection(request, group);

        if (!sameActivity(previousActivity, activity)) {
            participants.forEach(participant -> activityService.ensureParticipation(activity, participant));
            travelGroupRepository.flush();
            participants.forEach(participant -> reconcileParticipation(previousActivity, participant));
        }

        return toResponse(group);
    }

    @Transactional
    public void softDeleteGroup(Long groupId, String originalUserId) {
        User currentUser = getUserByOriginalId(originalUserId);
        TravelGroup group = getOwnedActiveGroup(groupId, currentUser.getId());
        Activity activity = group.getActivity();
        List<User> participants = getParticipants(group);

        group.markDeleted();
        travelGroupRepository.flush();
        participants.forEach(participant -> reconcileParticipation(activity, participant));
    }

    private List<TravelGroup> findMyGroupsForFiltering(User currentUser, TravelGroupFilterCriteria criteria) {
        if (shouldFilterByFriendMembership(criteria)) {
            return travelGroupRepository.findByCreatorAndStatusWithFriendMembers(
                    currentUser.getId(),
                    currentUser.getId(),
                    TravelGroupStatus.ACTIVE,
                    RelationStatus.FRIENDS
            );
        }

        return travelGroupRepository.findByCreatedByUserIdAndStatusOrderById(currentUser.getId(), TravelGroupStatus.ACTIVE);
    }

    private List<TravelGroup> findJoinedGroupsForFiltering(User currentUser, TravelGroupFilterCriteria criteria) {
        if (shouldFilterByFriendMembership(criteria)) {
            return travelGroupRepository.findJoinedGroupsExcludingCreatedGroupsWithFriendMembers(
                    currentUser.getId(),
                    TravelGroupStatus.ACTIVE,
                    RelationStatus.FRIENDS
            );
        }

        return travelGroupRepository.findJoinedGroupsExcludingCreatedGroups(currentUser.getId(), TravelGroupStatus.ACTIVE);
    }

    private List<TravelGroup> findAllGroupsForFiltering(String originalUserId, TravelGroupFilterCriteria criteria) {
        if (shouldFilterByFriendMembership(criteria)) {
            if (!StringUtils.hasText(originalUserId)) {
                throw new BadRequestException("Authentication is required to filter by friends");
            }

            User currentUser = getUserByOriginalId(originalUserId);
            return travelGroupRepository.findByStatusWithFriendMembers(
                    currentUser.getId(),
                    TravelGroupStatus.ACTIVE,
                    RelationStatus.FRIENDS
            );
        }

        return travelGroupRepository.findByStatusOrderById(TravelGroupStatus.ACTIVE);
    }

    private List<TravelGroup> findActivityGroupsForFiltering(Long activityId, String originalUserId, TravelGroupFilterCriteria criteria) {
        if (shouldFilterByFriendMembership(criteria)) {
            if (!StringUtils.hasText(originalUserId)) {
                throw new BadRequestException("Authentication is required to filter by friends");
            }

            User currentUser = getUserByOriginalId(originalUserId);
            return travelGroupRepository.findByActivityAndStatusWithFriendMembers(
                    activityId,
                    currentUser.getId(),
                    TravelGroupStatus.ACTIVE,
                    RelationStatus.FRIENDS
            );
        }

        return travelGroupRepository.findByActivityIdAndStatusOrderById(activityId, TravelGroupStatus.ACTIVE);
    }

    private List<TravelGroupResponse> filterAndMap(List<TravelGroup> candidateGroups, TravelGroupFilterCriteria criteria) {
        return candidateGroups
                .stream()
                .filter(group -> matches(criteria, group))
                .map(this::toResponse)
                .toList();
    }

    private List<TravelGroupResponse> filterAndMap(List<TravelGroup> candidateGroups, TravelGroupFilterCriteria criteria, String sortOption) {
        return candidateGroups
                .stream()
                .filter(group -> matches(criteria, group))
                .sorted(travelGroupComparator(sortOption))
                .map(this::toResponse)
                .toList();
    }

    private Comparator<TravelGroup> travelGroupComparator(String sortOption) {
        return switch (sortOption) {
            case "dateDesc" -> meetupTimeComparator(false);
            case "nameAsc" -> titleComparator(true);
            case "nameDesc" -> titleComparator(false);
            default -> meetupTimeComparator(true);
        };
    }

    private Comparator<TravelGroup> meetupTimeComparator(boolean ascending) {
        Comparator<LocalDateTime> meetupTimeComparator = ascending
                ? Comparator.nullsLast(Comparator.naturalOrder())
                : Comparator.nullsLast(Comparator.reverseOrder());

        return Comparator
                .comparing(TravelGroup::getMeetupTime, meetupTimeComparator)
                .thenComparing(TravelGroup::getId);
    }

    private Comparator<TravelGroup> titleComparator(boolean ascending) {
        Comparator<String> titleComparator = ascending
                ? Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                : Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER.reversed());

        return Comparator
                .comparing(TravelGroup::getTitle, titleComparator)
                .thenComparing(TravelGroup::getId);
    }

    private boolean shouldFilterByFriendMembership(TravelGroupFilterCriteria criteria) {
        return Boolean.TRUE.equals(criteria.friendsOnly());
    }

    private Comparator<TravelGroupMemberItem> memberComparator() {
        return Comparator.comparing(TravelGroupMemberItem::friend).reversed()
                .thenComparing(Comparator.comparing(TravelGroupMemberItem::creator).reversed())
                .thenComparing(TravelGroupMemberItem::username, String.CASE_INSENSITIVE_ORDER);
    }

    private TravelGroupMemberItem toMemberItem(TravelGroupMembership membership,
                                               Long currentUserId,
                                               Set<Long> friendIds,
                                               Long creatorId) {
        User user = membership.getUser();
        return new TravelGroupMemberItem(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfilePictureUrl(),
                membership.getJoinedAt(),
                Objects.equals(user.getId(), currentUserId),
                friendIds.contains(user.getId()),
                Objects.equals(user.getId(), creatorId)
        );
    }

    private Set<Long> getFriendIds(Long userId) {
        return userRelationRepository.findUserFriends(userId, RelationStatus.FRIENDS).stream()
                .map(relation -> otherUserId(relation, userId))
                .collect(Collectors.toSet());
    }

    private Long otherUserId(UserRelation relation, Long userId) {
        Long userOneId = relation.getUserOne().getId();
        return userOneId.equals(userId) ? relation.getUserTwo().getId() : userOneId;
    }

    private boolean matches(TravelGroupFilterCriteria criteria, TravelGroup group) {
        return matchesKeyword(criteria.keyword(), group)
                && matchesActivity(criteria.activityId(), group)
                && matchesTransportMode(criteria, group)
                && matchesGroupSize(criteria.groupSize(), group)
                && matchesStartProximity(criteria, group)
                && matchesEndProximity(criteria, group)
                && matchesMeetupTimeRange(criteria.meetupStart(), criteria.meetupEnd(), group);
    }

    private boolean matchesKeyword(String keyword, TravelGroup group) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }

        String normalizedKeyword = normalizeForSearch(keyword);
        return contains(group.getTitle(), normalizedKeyword)
                || contains(group.getDescription(), normalizedKeyword)
                || (group.getActivity() != null && contains(group.getActivity().getTitle(), normalizedKeyword));
    }

    private boolean matchesActivity(Long activityId, TravelGroup group) {
        return activityId == null || (group.getActivity() != null && group.getActivity().getId().equals(activityId));
    }

    private boolean matchesTransportMode(TravelGroupFilterCriteria criteria, TravelGroup group) {
        return criteria.transportationMode() == null || group.getTransportationMode() == criteria.transportationMode();
    }

    private boolean matchesGroupSize(Integer groupSize, TravelGroup group) {
        return groupSize == null || group.getMaxSize().equals(groupSize);
    }

    private boolean matchesStartProximity(TravelGroupFilterCriteria criteria, TravelGroup group) {
        return matchesProximity(
                criteria.preferredStartLatitude(),
                criteria.preferredStartLongitude(),
                criteria.startProximityMeters(),
                group.getStartPointLatitude(),
                group.getStartPointLongitude()
        );
    }

    private boolean matchesEndProximity(TravelGroupFilterCriteria criteria, TravelGroup group) {
        return matchesProximity(
                criteria.preferredEndLatitude(),
                criteria.preferredEndLongitude(),
                criteria.endProximityMeters(),
                group.getEndPointLatitude(),
                group.getEndPointLongitude()
        );
    }

    private boolean matchesProximity(Double preferredLatitude,
                                     Double preferredLongitude,
                                     Double proximityMeters,
                                     Double actualLatitude,
                                     Double actualLongitude) {
        if (preferredLatitude == null || preferredLongitude == null) {
            return true;
        }

        double effectiveProximityMeters = proximityMeters != null ? proximityMeters : 50.0;

        return coordinateDistanceCalculator.distanceInMeters(
                preferredLatitude,
                preferredLongitude,
                actualLatitude,
                actualLongitude
        ) <= effectiveProximityMeters;
    }

    private boolean matchesMeetupTimeRange(LocalDateTime meetupStart, LocalDateTime meetupEnd, TravelGroup group) {
        if (meetupStart == null && meetupEnd == null) {
            return true;
        }

        if (meetupStart != null && meetupEnd != null && meetupStart.isAfter(meetupEnd)) {
            throw new BadRequestException("Meetup start must be before meetup end");
        }

        LocalDateTime groupMeetupTime = group.getMeetupTime();
        if (groupMeetupTime == null) {
            return false;
        }

        boolean startsOnOrAfterLowerBound = meetupStart == null || !groupMeetupTime.isBefore(meetupStart);
        boolean endsOnOrBeforeUpperBound = meetupEnd == null || !groupMeetupTime.isAfter(meetupEnd);

        return startsOnOrAfterLowerBound && endsOnOrBeforeUpperBound;
    }

    private boolean contains(String value, String normalizedNeedle) {
        return value != null && normalizeForSearch(value).contains(normalizedNeedle);
    }

    private String normalizeForSearch(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void reconcileParticipation(Activity activity, User user) {
        if (activity == null) {
            return;
        }

        boolean hasActiveTravelPlan = travelGroupRepository.existsByActivityIdAndUserIdAndStatus(
                activity.getId(),
                user.getId(),
                TravelGroupStatus.ACTIVE
        );
        activityService.synchronizeParticipation(activity, user, hasActiveTravelPlan);
    }

    private List<User> getParticipants(TravelGroup group) {
        LinkedHashMap<Long, User> participants = new LinkedHashMap<>();
        participants.put(group.getCreatedByUser().getId(), group.getCreatedByUser());
        group.getMemberships().forEach(membership -> participants.put(membership.getUser().getId(), membership.getUser()));
        return participants.values().stream().toList();
    }

    private boolean sameActivity(Activity left, Activity right) {
        return Objects.equals(left != null ? left.getId() : null, right != null ? right.getId() : null);
    }

    private User getUserByOriginalId(String originalUserId) {
        return userRepository.findByOriginalId(originalUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private Activity resolveActivity(Long activityId) {
        if (activityId == null) {
            return null;
        }

        return activityRepository.findById(activityId)
                .orElseThrow(() -> new BadRequestException("Invalid activityId"));
    }

    private void ensureActivityExists(Long activityId) {
        if (!activityRepository.existsById(activityId)) {
            throw new BadRequestException("Invalid activityId");
        }
    }

    private TravelGroup getActiveGroup(Long groupId) {
        return travelGroupRepository.findById(groupId)
                .filter(group -> group.getStatus() == TravelGroupStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Travel group not found"));
    }

    private TravelGroup getOwnedActiveGroup(Long groupId, Long userId) {
        return travelGroupRepository.findByIdAndCreatedByUserIdAndStatus(groupId, userId, TravelGroupStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Travel group not found"));
    }

    private void validateRequest(TravelGroupRequest request) {
        if (request.getStartPointLatitude() == null
                || request.getStartPointLongitude() == null
                || request.getEndPointLatitude() == null
                || request.getEndPointLongitude() == null
                || request.getTransportationMode() == null
                || request.getMaxSize() == null) {
            throw new BadRequestException("Missing required travel group fields");
        }
    }

    private void syncPublicTransportSelection(TravelGroupRequest request, TravelGroup group) {
        boolean hadSelection = group.getPublicTransportSelection() != null;
        group.clearPublicTransportSelection();
        if (hadSelection) {
            // Delete the previous selection before inserting a replacement, otherwise
            // the new row clashes with the unique travel_group_id constraint.
            publicTransportSelectionService.flushPendingChanges();
        }
        if (request.getTransportationMode() != TransportMode.PUBLIC_TRANSPORT) {
            return;
        }

        try {
            TransitSuggestionResult suggestion = transitRouteSuggestionService.suggestTransitRouteWithMetadata(
                    TransitRouteQuery.from(request)
            );
            SuggestedRoute route = selectedRoute(suggestion, request.getSelectedPublicTransportRouteIndex());
            if (route == null) {
                return;
            }

            group.setPublicTransportSelection(publicTransportSelectionService.fromSuggestedRoute(group, route));
        } catch (RuntimeException ex) {
            logger.warn("Could not persist public transport route snapshot for travel group: {}", ex.getMessage());
        }

        if (group.getPublicTransportSelection() == null && hasCompleteSelectedRouteSnapshot(request)) {
            group.setPublicTransportSelection(publicTransportSelectionService.fromRequestSnapshot(group, request));
        }
    }

    private SuggestedRoute selectedRoute(TransitSuggestionResult suggestion, Integer routeIndex) {
        List<SuggestedRoute> routes = suggestion.routes();
        if (routes.isEmpty()) {
            return suggestion.route();
        }

        int safeIndex = routeIndex != null && routeIndex >= 0 && routeIndex < routes.size()
                ? routeIndex
                : 0;
        return routes.get(safeIndex);
    }

    private boolean hasCompleteSelectedRouteSnapshot(TravelGroupRequest request) {
        return StringUtils.hasText(normalize(request.getSelectedRouteLineNumber()))
                && StringUtils.hasText(normalize(request.getSelectedRouteStartStopId()))
                && StringUtils.hasText(normalize(request.getSelectedRouteEndStopId()))
                && request.getSelectedRouteDepartureTime() != null
                && request.getSelectedRouteEstimatedArrivalTime() != null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TravelGroupResponse toResponse(TravelGroup group) {
        return new TravelGroupResponse(
                group.getId(),
                group.getVersion(),
                group.getActivity() != null ? group.getActivity().getId() : null,
                group.getActivity() != null ? group.getActivity().getTitle() : null,
                group.getCreatedByUser().getId(),
                group.getTitle(),
                group.getDescription(),
                group.getStartPointLatitude(),
                group.getStartPointLongitude(),
                group.getEndPointLatitude(),
                group.getEndPointLongitude(),
                group.getTransportationMode(),
                group.getDistanceKm(),
                group.getMeetupTime(),
                group.getMaxSize(),
                group.getMemberCount(),
                group.getContributionRequired(),
                group.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public TravelGroup getTravelGroupById(Long id) {
        return travelGroupRepository.findByIdWithCreatedByUser(id).orElse(null);
    }
}
