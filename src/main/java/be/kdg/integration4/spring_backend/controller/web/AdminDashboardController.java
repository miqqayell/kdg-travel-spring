package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.controller.web.dto.AdminEventsDto;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import be.kdg.integration4.spring_backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;


@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminReportService adminReportService;
    private final AdminEventService adminEventService;
    private final AdminUserService adminUserService;
    private final UserService userService;
    private final PermissionService permissionService;

    @GetMapping
    public String redirectToOverview() {
        return "redirect:/admin/overview";
    }

    @GetMapping("/overview")
    public String showOverview(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        User currentUser = getAllowedAdminUser(oidcUser);

        model.addAttribute("overview", adminDashboardService.getOverview());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeAdminPage", "overview");

        return "admin/overview";
    }

    @GetMapping("/reports")
    public String showReports(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        User currentUser = getAllowedAdminUser(oidcUser);

        model.addAttribute("reports", adminReportService.getOpenReports());
        model.addAttribute("openReportCount", adminReportService.countOpenReports());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeAdminPage", "reports");

        return "admin/reports";
    }

    @GetMapping("/reports/{id}")
    public String showReportDetail(@PathVariable Long id,
                                   @AuthenticationPrincipal OidcUser oidcUser,
                                   Model model) {
        User currentUser = getAllowedAdminUser(oidcUser);

        model.addAttribute("report", adminReportService.getReportAndMarkInReview(id, oidcUser.getSubject()));
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeAdminPage", "reports");

        return "admin/report-detail";
    }

    @PostMapping("/reports/{id}/dismiss")
    public String dismissReport(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminReportService.dismissReport(id, oidcUser.getSubject());
        return "redirect:/admin/reports/" + id;
    }

    @PostMapping("/reports/{id}/resolve")
    public String resolveReport(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminReportService.resolveReport(id, oidcUser.getSubject());
        return "redirect:/admin/reports/" + id;
    }

    @PostMapping("/reports/{id}/warn")
    public String warnUser(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminReportService.warnUser(id, oidcUser.getSubject());
        return "redirect:/admin/reports/" + id;
    }

    @PostMapping("/reports/{id}/suspend")
    public String suspendUserFromReport(@PathVariable Long id,
                                        @RequestParam int days,
                                        @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminReportService.suspendUser(id, oidcUser.getSubject(), days);
        return "redirect:/admin/reports/" + id;
    }

    @PostMapping("/reports/{id}/ban")
    public String banUserFromReport(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminReportService.banUser(id, oidcUser.getSubject());
        return "redirect:/admin/reports/" + id;
    }

    @GetMapping("/events")
    public String showEvents(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        User currentUser = getAllowedAdminUser(oidcUser);

        model.addAttribute("events", new AdminEventsDto(
                adminEventService.countPendingEvents(),
                adminEventService.countVerifiedEvents(),
                adminEventService.countTotalEvents(),
                adminEventService.getPendingEvents(),
                adminEventService.getAllEvents()
        ));

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeAdminPage", "events");

        return "admin/events";
    }

    @GetMapping("/events/{id}")
    public String showEventDetail(@PathVariable Long id,
                                  @AuthenticationPrincipal OidcUser oidcUser,
                                  Model model) {
        User currentUser = getAllowedAdminUser(oidcUser);

        model.addAttribute("activity", adminEventService.getEventById(id));
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("activeAdminPage", "events");

        return "admin/event-detail";
    }

    @PostMapping("/events/{id}/verify")
    public String verifyEvent(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminEventService.verifyEvent(id);
        return "redirect:/admin/events/" + id;
    }

    @PostMapping("/events/{id}/reject")
    public String rejectEvent(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminEventService.rejectEvent(id);
        return "redirect:/admin/events/" + id;
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminEventService.deleteEvent(id);
        return "redirect:/admin/events";
    }

    @GetMapping("/users")
    public String showUsers(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        User currentUser = getAllowedAdminUser(oidcUser);

        model.addAttribute("users", adminUserService.getAllUsers());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("roles", Role.values());
        model.addAttribute("activeAdminPage", "users");

        return "admin/users";
    }

    @PostMapping("/users/{id}/suspend")
    public String suspendUser(@PathVariable Long id,
                              @RequestParam int days,
                              @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminUserService.suspendUser(id, days, oidcUser.getSubject());
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/ban")
    public String banUser(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminUserService.banUser(id, oidcUser.getSubject());
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminUserService.activateUser(id, oidcUser.getSubject());
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam Role role,
                             @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminUserService.changeRole(id, role, oidcUser.getSubject());
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteAdminOrModerator(@PathVariable Long id,
                                         @AuthenticationPrincipal OidcUser oidcUser) {
        getAllowedAdminUser(oidcUser);
        adminUserService.deleteAdminOrModerator(id, oidcUser.getSubject());
        return "redirect:/admin/users";
    }

    private User getAllowedAdminUser(OidcUser oidcUser) {
        User currentUser = userService.getCurrentUser(oidcUser.getSubject());

        if (!permissionService.canAccessAdminDashboard(currentUser)) {
            throw new AccessDeniedException("You are not allowed to access the admin dashboard.");
        }

        return currentUser;
    }
}
