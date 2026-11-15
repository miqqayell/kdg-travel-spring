package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupResponse;
import be.kdg.integration4.spring_backend.controller.web.dto.ActivityFriendAttendanceSummary;
import be.kdg.integration4.spring_backend.controller.web.dto.TravelGroupForm;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.ActivityService;
import be.kdg.integration4.spring_backend.service.PermissionService;
import be.kdg.integration4.spring_backend.service.TravelGroupFilterCriteria;
import be.kdg.integration4.spring_backend.service.TravelGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/travel-groups")
public class TravelGroupsController {

    private final TravelGroupService travelGroupService;
    private final ActivityService activityService;
    private final ActivityRepository activityRepository;
    private final PermissionService permissionService;
    private final UserRepository userRepository;

    @GetMapping
    public String showMyGroups(@RequestParam(name = "keyword", required = false) String keyword,
                               @RequestParam(name = "activityId", required = false) Long activityId,
                               @RequestParam(name = "transportationMode", required = false) TransportMode transportationMode,
                               @RequestParam(name = "groupSize", required = false) Integer groupSize,
                               @RequestParam(name = "preferredStartAddress", required = false) String preferredStartAddress,
                               @RequestParam(name = "preferredStartLatitude", required = false) Double preferredStartLatitude,
                               @RequestParam(name = "preferredStartLongitude", required = false) Double preferredStartLongitude,
                               @RequestParam(name = "startProximityMeters", required = false) Double startProximityMeters,
                               @RequestParam(name = "preferredEndAddress", required = false) String preferredEndAddress,
                               @RequestParam(name = "preferredEndLatitude", required = false) Double preferredEndLatitude,
                               @RequestParam(name = "preferredEndLongitude", required = false) Double preferredEndLongitude,
                               @RequestParam(name = "endProximityMeters", required = false) Double endProximityMeters,
                               @RequestParam(name = "meetupStart", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime meetupStart,
                               @RequestParam(name = "meetupEnd", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime meetupEnd,
                               @RequestParam(name = "friendsOnly", defaultValue = "false") boolean friendsOnly,
                               @RequestParam(name = "allSort", defaultValue = "dateAsc") String allSort,
                               @RequestParam(name = "createdGroups", defaultValue = "false") boolean createdGroups,
                               @RequestParam(name = "joinedGroups", defaultValue = "false") boolean joinedGroups,
                               Model model,
                               @AuthenticationPrincipal OidcUser oidcUser,
                               Principal principal) {
        TravelGroupFilterCriteria criteria = new TravelGroupFilterCriteria(
                keyword,
                activityId,
                transportationMode,
                groupSize,
                preferredStartAddress,
                preferredStartLatitude,
                preferredStartLongitude,
                startProximityMeters,
                preferredEndAddress,
                preferredEndLatitude,
                preferredEndLongitude,
                endProximityMeters,
                meetupStart,
                meetupEnd,
                friendsOnly
        );

        String normalizedAllSort = normalizeSortOption(allSort);
        User currentUser = getCurrentUser(oidcUser);

        model.addAttribute("travelGroups", searchGroupsForView(oidcUser.getSubject(), criteria, normalizedAllSort, createdGroups, joinedGroups));
        model.addAttribute("filter", criteria);
        model.addAttribute("selectedActivityTitle", resolveActivityTitle(activityId));
        model.addAttribute("allSort", normalizedAllSort);
        model.addAttribute("createdGroups", createdGroups);
        model.addAttribute("joinedGroups", joinedGroups);
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("isLoggedIn", principal != null);
        return "travel-groups";
    }

    @GetMapping("/{id}")
    public String showGroupDetail(@PathVariable Long id,
                                  Model model,
                                  @AuthenticationPrincipal OidcUser oidcUser,
                                  Principal principal) {
        String originalUserId = oidcUser != null ? oidcUser.getSubject() : null;

        model.addAttribute("travelGroup", travelGroupService.getGroupById(id));
        model.addAttribute("groupMembers", travelGroupService.getGroupMembers(id, originalUserId));
        model.addAttribute("publicTransportSelection", travelGroupService.getPublicTransportSelection(id));
        model.addAttribute("isLoggedIn", principal != null);

        if (principal != null && oidcUser != null) {
            model.addAttribute("currentUserId", getCurrentUser(oidcUser).getId());
            model.addAttribute("isCreator", travelGroupService.isGroupCreator(id, oidcUser.getSubject()));
            model.addAttribute("isMember", travelGroupService.isUserMember(id, oidcUser.getSubject()));
        } else {
            model.addAttribute("currentUserId", null);
            model.addAttribute("isCreator", false);
            model.addAttribute("isMember", false);
        }

        return "travel-group-detail";
    }

    @PostMapping("/{id}/join")
    public String joinGroup(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        travelGroupService.joinGroup(id, oidcUser.getSubject());
        return "redirect:/travel-groups/" + id;
    }

    @PostMapping("/{id}/leave")
    public String leaveGroup(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        travelGroupService.leaveGroup(id, oidcUser.getSubject());
        return "redirect:/travel-groups/" + id;
    }

    @GetMapping("/activity/{activityId}")
    public String showGroupsForActivity(@PathVariable Long activityId,
                                        @RequestParam(name = "keyword", required = false) String keyword,
                                        @RequestParam(name = "transportationMode", required = false) TransportMode transportationMode,
                                        @RequestParam(name = "groupSize", required = false) Integer groupSize,
                                        @RequestParam(name = "preferredStartAddress", required = false) String preferredStartAddress,
                                        @RequestParam(name = "preferredStartLatitude", required = false) Double preferredStartLatitude,
                                        @RequestParam(name = "preferredStartLongitude", required = false) Double preferredStartLongitude,
                                        @RequestParam(name = "startProximityMeters", required = false) Double startProximityMeters,
                                        @RequestParam(name = "preferredEndAddress", required = false) String preferredEndAddress,
                                        @RequestParam(name = "preferredEndLatitude", required = false) Double preferredEndLatitude,
                                        @RequestParam(name = "preferredEndLongitude", required = false) Double preferredEndLongitude,
                                        @RequestParam(name = "endProximityMeters", required = false) Double endProximityMeters,
                                        @RequestParam(name = "meetupStart", required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime meetupStart,
                                        @RequestParam(name = "meetupEnd", required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime meetupEnd,
                                        @RequestParam(name = "friendsOnly", defaultValue = "false") boolean friendsOnly,
                                        @RequestParam(name = "sort", defaultValue = "dateAsc") String sort,
                                        Model model,
                                        @AuthenticationPrincipal OidcUser oidcUser,
                                        Principal principal) {
        TravelGroupFilterCriteria criteria = new TravelGroupFilterCriteria(
                keyword,
                activityId,
                transportationMode,
                groupSize,
                preferredStartAddress,
                preferredStartLatitude,
                preferredStartLongitude,
                startProximityMeters,
                preferredEndAddress,
                preferredEndLatitude,
                preferredEndLongitude,
                endProximityMeters,
                meetupStart,
                meetupEnd,
                friendsOnly
        );

        String normalizedSort = normalizeSortOption(sort);
        ActivityFriendAttendanceSummary friendAttendanceSummary = ActivityFriendAttendanceSummary.empty();

        if (oidcUser != null) {
            User currentUser = getCurrentUser(oidcUser);
            friendAttendanceSummary = activityService.getFriendAttendanceSummary(activityId, currentUser.getId());
        }

        model.addAttribute("travelGroups", travelGroupService.searchGroupsForActivity(activityId, oidcUser != null ? oidcUser.getSubject() : null, criteria, normalizedSort));
        model.addAttribute("filter", criteria);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("isLoggedIn", principal != null);
        model.addAttribute("friendAttendanceSummary", friendAttendanceSummary);

        activityRepository.findById(activityId).ifPresent(activity -> {
            model.addAttribute("activityTitle", activity.getTitle());
            model.addAttribute("activityId", activityId);
        });

        return "activity-groups";
    }

    @GetMapping("/create")
    public String showCreateForm(@RequestParam(required = false) Long activityId,
                                 Model model,
                                 Principal principal) {
        TravelGroupForm form = new TravelGroupForm();
        form.setActivityId(activityId);
        form.setSelectedPublicTransportRouteIndex(0);

        model.addAttribute("travelGroupForm", form);
        model.addAttribute("activities", activityRepository.findAll());
        model.addAttribute("transportModes", TransportMode.values());
        model.addAttribute("pageTitle", "Create Travel Group");
        model.addAttribute("formAction", "/travel-groups/create");
        model.addAttribute("submitLabel", "Create Travel Group");
        model.addAttribute("isEdit", false);
        model.addAttribute("isLoggedIn", principal != null);

        return "travel-group-form";
    }

    @PostMapping("/create")
    public String createGroup(@Valid @ModelAttribute("travelGroupForm") TravelGroupForm form,
                              BindingResult bindingResult,
                              Model model,
                              @AuthenticationPrincipal OidcUser oidcUser,
                              Principal principal) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, principal, false, null);
            return "travel-group-form";
        }

        try {
            TravelGroupResponse created = travelGroupService.createGroup(toRequest(form), oidcUser.getSubject());
            return "redirect:/travel-groups/" + created.id();
        } catch (BadRequestException | ResourceNotFoundException ex) {
            model.addAttribute("formError", ex.getMessage());
            populateFormModel(model, principal, false, null);
            return "travel-group-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id,
                               Model model,
                               @AuthenticationPrincipal OidcUser oidcUser,
                               Principal principal) {
        TravelGroupResponse group = travelGroupService.getMyGroupById(id, oidcUser.getSubject());
        User currentUser = getCurrentUser(oidcUser);
        TravelGroup travelGroup = travelGroupService.getTravelGroupById(id);

        if (!permissionService.canEditTravelGroup(currentUser, travelGroup)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not allowed to edit this travel group.");
        }

        TravelGroupForm form = new TravelGroupForm();
        form.setActivityId(group.activityId());
        form.setTitle(group.title());
        form.setDescription(group.description());
        form.setStartPointLatitude(group.startPointLatitude());
        form.setStartPointLongitude(group.startPointLongitude());
        form.setEndPointLatitude(group.endPointLatitude());
        form.setEndPointLongitude(group.endPointLongitude());
        form.setTransportationMode(group.transportationMode());
        form.setDistanceKm(group.distanceKm());
        form.setMeetupTime(group.meetupTime());
        form.setMaxSize(group.maxSize());
        form.setSelectedPublicTransportRouteIndex(0);
        form.setContributionRequired(group.contributionRequired());

        model.addAttribute("travelGroupForm", form);
        model.addAttribute("travelGroupId", id);
        model.addAttribute("activities", activityRepository.findAll());
        model.addAttribute("transportModes", TransportMode.values());
        model.addAttribute("pageTitle", "Edit Travel Group");
        model.addAttribute("formAction", "/travel-groups/" + id + "/edit");
        model.addAttribute("submitLabel", "Save Changes");
        model.addAttribute("isEdit", true);
        model.addAttribute("isLoggedIn", principal != null);

        return "travel-group-form";
    }

    @PostMapping("/{id}/edit")
    public String updateGroup(@PathVariable Long id,
                              @Valid @ModelAttribute("travelGroupForm") TravelGroupForm form,
                              BindingResult bindingResult,
                              Model model,
                              @AuthenticationPrincipal OidcUser oidcUser,
                              Principal principal) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("travelGroupId", id);
            populateFormModel(model, principal, true, id);
            return "travel-group-form";
        }

        User currentUser = getCurrentUser(oidcUser);
        TravelGroup travelGroup = travelGroupService.getTravelGroupById(id);

        if (!permissionService.canEditTravelGroup(currentUser, travelGroup)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not allowed to edit this travel group.");
        }

        try {
            travelGroupService.updateGroup(id, toRequest(form), oidcUser.getSubject());
            return "redirect:/travel-groups/" + id;
        } catch (BadRequestException | ResourceNotFoundException ex) {
            model.addAttribute("travelGroupId", id);
            model.addAttribute("formError", ex.getMessage());
            populateFormModel(model, principal, true, id);
            return "travel-group-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable Long id,
                              @AuthenticationPrincipal OidcUser oidcUser) {
        User currentUser = getCurrentUser(oidcUser);
        TravelGroup travelGroup = travelGroupService.getTravelGroupById(id);

        if (!permissionService.canDeleteTravelGroup(currentUser, travelGroup)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not allowed to delete this travel group.");
        }

        travelGroupService.softDeleteGroup(id, oidcUser.getSubject());
        return "redirect:/travel-groups";
    }

    @ModelAttribute("allActivities")
    public List<Activity> allActivities() {
        return activityRepository.findAll();
    }

    @ModelAttribute("allTransportModes")
    public List<TransportMode> allTransportModes() {
        return Arrays.asList(TransportMode.values());
    }

    private String resolveActivityTitle(Long activityId) {
        if (activityId == null) {
            return "";
        }

        return activityRepository.findById(activityId)
                .map(Activity::getTitle)
                .orElse("");
    }

    private String normalizeSortOption(String sortOption) {
        if (sortOption == null) {
            return "dateAsc";
        }

        return switch (sortOption) {
            case "desc", "dateDesc" -> "dateDesc";
            case "nameAsc" -> "nameAsc";
            case "nameDesc" -> "nameDesc";
            default -> "dateAsc";
        };
    }

    private List<TravelGroupResponse> searchGroupsForView(String originalUserId,
                                                          TravelGroupFilterCriteria criteria,
                                                          String sortOption,
                                                          boolean createdGroups,
                                                          boolean joinedGroups) {
        if (createdGroups && joinedGroups) {
            return travelGroupService.searchCreatedAndJoinedGroups(originalUserId, criteria, sortOption);
        }
        if (createdGroups) {
            return travelGroupService.searchCreatedGroups(originalUserId, criteria, sortOption);
        }
        if (joinedGroups) {
            return travelGroupService.searchJoinedGroupsExcludingCreatedGroups(originalUserId, criteria, sortOption);
        }
        return travelGroupService.searchAllGroups(originalUserId, criteria, sortOption);
    }

    private void populateFormModel(Model model, Principal principal, boolean isEdit, Long id) {
        model.addAttribute("activities", activityRepository.findAll());
        model.addAttribute("transportModes", TransportMode.values());
        model.addAttribute("pageTitle", isEdit ? "Edit Travel Group" : "Create Travel Group");
        model.addAttribute("formAction", isEdit ? "/travel-groups/" + id + "/edit" : "/travel-groups/create");
        model.addAttribute("submitLabel", isEdit ? "Save Changes" : "Create Travel Group");
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("isLoggedIn", principal != null);
    }

    private TravelGroupRequest toRequest(TravelGroupForm form) {
        TravelGroupRequest request = new TravelGroupRequest();
        request.setActivityId(form.getActivityId());
        request.setTitle(form.getTitle());
        request.setDescription(form.getDescription());
        request.setStartPointLatitude(form.getStartPointLatitude());
        request.setStartPointLongitude(form.getStartPointLongitude());
        request.setEndPointLatitude(form.getEndPointLatitude());
        request.setEndPointLongitude(form.getEndPointLongitude());
        request.setTransportationMode(form.getTransportationMode());
        request.setDistanceKm(form.getDistanceKm());
        request.setMeetupTime(form.getMeetupTime());
        request.setMaxSize(form.getMaxSize());
        request.setSelectedRouteToken(form.getSelectedRouteToken());
        request.setSelectedRouteStartStopId(form.getSelectedRouteStartStopId());
        request.setSelectedRouteStartStopName(form.getSelectedRouteStartStopName());
        request.setSelectedRouteEndStopId(form.getSelectedRouteEndStopId());
        request.setSelectedRouteEndStopName(form.getSelectedRouteEndStopName());
        request.setSelectedRouteLineNumber(form.getSelectedRouteLineNumber());
        request.setSelectedRouteDirection(form.getSelectedRouteDirection());
        request.setSelectedRouteDestination(form.getSelectedRouteDestination());
        request.setSelectedRouteDepartureTime(form.getSelectedRouteDepartureTime());
        request.setSelectedRouteEstimatedArrivalTime(form.getSelectedRouteEstimatedArrivalTime());
        request.setSelectedRouteRealTimeStatus(form.getSelectedRouteRealTimeStatus());
        request.setSelectedRouteEstimatedDelayMinutes(form.getSelectedRouteEstimatedDelayMinutes());
        request.setSelectedPublicTransportRouteIndex(form.getSelectedPublicTransportRouteIndex());
        request.setContributionRequired(form.getContributionRequired());
        return request;
    }

    private User getCurrentUser(OidcUser oidcUser) {
        return userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new RuntimeException("Logged in user not found in database"));
    }
}
