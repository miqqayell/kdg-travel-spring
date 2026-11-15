package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.controller.web.dto.ActivityOverviewItem;
import be.kdg.integration4.spring_backend.controller.web.dto.SuggestedTravelFriendDto;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import be.kdg.integration4.spring_backend.mappers.ActivityMapper;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.ActivityFilterCriteria;
import be.kdg.integration4.spring_backend.service.ActivityService;
import be.kdg.integration4.spring_backend.service.FriendSuggestionService;
import be.kdg.integration4.spring_backend.service.NotificationService;
import be.kdg.integration4.spring_backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ActivitiesController {

    private final ActivityService activityService;
    private final UserRepository userRepository;
    private final ActivityMapper activityMapper;
    private final PermissionService permissionService;
    private final FriendSuggestionService friendSuggestionService;
    private final NotificationService notificationService;

    @GetMapping("/activities")
    public String activities(@RequestParam(name = "keyword", required = false) String keyword,
                             @RequestParam(name = "startsAfter", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAfter,
                             @RequestParam(name = "startsBefore", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsBefore,
                             @RequestParam(name = "locationAddress", required = false) String locationAddress,
                             @RequestParam(name = "locationLatitude", required = false) Double locationLatitude,
                             @RequestParam(name = "locationLongitude", required = false) Double locationLongitude,
                             @RequestParam(name = "locationProximityMeters", required = false) Double locationProximityMeters,
                             @RequestParam(name = "friendsOnly", defaultValue = "false") boolean friendsOnly,
                             @RequestParam(name = "createdEvents", defaultValue = "false") boolean createdEvents,
                             @RequestParam(name = "joinedGroupEvents", defaultValue = "false") boolean joinedGroupEvents,
                             Model model,
                             Principal principal,
                             @AuthenticationPrincipal OidcUser oidcUser) {
        Long currentUserId = oidcUser != null ? getCurrentUser(oidcUser).getId() : null;
        ActivityFilterCriteria criteria = new ActivityFilterCriteria(
                keyword,
                startsAfter,
                startsBefore,
                locationAddress,
                locationLatitude,
                locationLongitude,
                locationProximityMeters,
                friendsOnly
        );
        List<ActivityOverviewItem> allActivities = activityService.searchUpcomingActivities(
                currentUserId,
                criteria,
                createdEvents,
                joinedGroupEvents
        );

        Map<String, Object> filter = new HashMap<>();
        filter.put("keyword", keyword);
        filter.put("startsAfter", startsAfter);
        filter.put("startsBefore", startsBefore);
        filter.put("locationAddress", locationAddress);
        filter.put("locationLatitude", locationLatitude);
        filter.put("locationLongitude", locationLongitude);
        filter.put("locationProximityMeters", locationProximityMeters);
        filter.put("friendsOnly", friendsOnly);

        model.addAttribute("allActivities", allActivities);
        model.addAttribute("filter", filter);
        model.addAttribute("createdEvents", createdEvents);
        model.addAttribute("joinedGroupEvents", joinedGroupEvents);
        model.addAttribute("isLoggedIn", principal != null);
        return "activities";
    }

    @GetMapping("/activities/{id}")
    public String activities(@PathVariable Long id,
                             Model model,
                             Principal principal,
                             @AuthenticationPrincipal OidcUser oidcUser) {
        Activity activity = activityService.getActivityById(id);

        boolean canEdit = false;
        boolean canDelete = false;
        List<SuggestedTravelFriendDto> suggestedFriends = List.of();

        if (oidcUser != null) {
            User currentUser = getCurrentUser(oidcUser);
            canEdit = permissionService.canEditActivity(currentUser, activity);
            canDelete = permissionService.canDeleteActivity(currentUser, activity);
            suggestedFriends = friendSuggestionService.suggestForActivity(currentUser, activity);
        }

        model.addAttribute("activity", activityMapper.toDto(activity));
        model.addAttribute("isLoggedIn", principal != null);
        model.addAttribute("canEditActivity", canEdit);
        model.addAttribute("canDeleteActivity", canDelete);
        model.addAttribute("suggestedFriends", suggestedFriends);

        return "activity-detail";
    }

    @GetMapping("/map")
    public String showMapPage(Model model, Principal principal) {
        model.addAttribute("isLoggedIn", principal != null);
        return "map";
    }

    @GetMapping("/activities/create")
    public String showCreateForm(Model model) {
        model.addAttribute("activity", new Activity());
        return "create-activity";
    }

    // UPDATED: Now using OidcUser instead of OAuth2User
    @PostMapping("/activities/create")
    public String submitCreateForm(@ModelAttribute Activity activity, @AuthenticationPrincipal OidcUser oidcUser) {

        // Find the real database User using their Google ID
        User creator = userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new RuntimeException("Logged in user not found in database"));

        // Attach the user to the activity
        activity.setCreatedByUser(creator);

        // Set status to pending by default
        activity.setVerificationStatus(VerificationStatus.PENDING);

        // Save it
        activityService.createActivity(activity);

        return "redirect:/map";
    }

    @GetMapping("/activities/{id}/edit")
    public String showEditForm(@PathVariable Long id,
                               Model model,
                               Principal principal,
                               @AuthenticationPrincipal OidcUser oidcUser) {
        Activity activity = activityService.getActivityById(id);
        User currentUser = getCurrentUser(oidcUser);

        if (!permissionService.canEditActivity(currentUser, activity)) {
            // could replace with logger in the future
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to edit this activity."
            );
        }

        model.addAttribute("activity", activity);
        model.addAttribute("isLoggedIn", principal != null);
        model.addAttribute("pageTitle", "Edit Activity");
        model.addAttribute("formAction", "/activities/" + id + "/edit");
        model.addAttribute("submitLabel", "Save Changes");
        model.addAttribute("isEdit", true);
        return "create-activity";
    }

    @PostMapping("/activities/{id}/edit")
    public String updateActivity(@PathVariable Long id,
                                 @ModelAttribute Activity updatedActivity,
                                 @AuthenticationPrincipal OidcUser oidcUser) {
        Activity existingActivity = activityService.getActivityById(id);
        User currentUser = getCurrentUser(oidcUser);

        if (!permissionService.canEditActivity(currentUser, existingActivity)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to edit this activity."
            );
        }

        existingActivity.setTitle(updatedActivity.getTitle());
        existingActivity.setType(updatedActivity.getType());
        existingActivity.setStartTime(updatedActivity.getStartTime());
        existingActivity.setEndTime(updatedActivity.getEndTime());
        existingActivity.setDescription(updatedActivity.getDescription());
        existingActivity.setLocationLatitude(updatedActivity.getLocationLatitude());
        existingActivity.setLocationLongitude(updatedActivity.getLocationLongitude());
        existingActivity.setLocationAddress(updatedActivity.getLocationAddress());
        existingActivity.setVerificationStatus(updatedActivity.getVerificationStatus());
        existingActivity.setImageUrl(updatedActivity.getImageUrl());

        activityService.updateActivity(existingActivity);

        return "redirect:/activities/" + id;
    }

    @PostMapping("/activities/{id}/invite/{friendId}")
    public String inviteFriendToActivity(@PathVariable Long id,
                                         @PathVariable Long friendId,
                                         @AuthenticationPrincipal OidcUser oidcUser,
                                         RedirectAttributes redirectAttributes) {
        User sender = getCurrentUser(oidcUser);
        User recipient = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Friend not found"));
        Activity activity = activityService.getActivityById(id);

        notificationService.notifyActivityInvite(sender, recipient, activity);
        redirectAttributes.addFlashAttribute("successMessage",
                "Invite sent to " + recipient.getUsername() + ".");
        return "redirect:/activities/" + id;
    }

    @PostMapping("/activities/{id}/delete")
    public String deleteActivity(@PathVariable Long id,
                                 @AuthenticationPrincipal OidcUser oidcUser,
                                 RedirectAttributes redirectAttributes) { // Added this!
        try {
            Activity activity = activityService.getActivityById(id);
            User currentUser = getCurrentUser(oidcUser);

            if (!permissionService.canDeleteActivity(currentUser, activity)) {
                redirectAttributes.addFlashAttribute("error", "You are not allowed to delete this activity.");
                return "redirect:/activities/" + id;
            }

            activityService.deleteActivityById(id);
            return "redirect:/activities";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete this activity because there are active travel groups or notifications tied to it.");
            return "redirect:/activities/" + id;
        }
    }

    private User getCurrentUser(OidcUser oidcUser) {
        return userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new RuntimeException("Logged in user not found in database"));
    }

}


