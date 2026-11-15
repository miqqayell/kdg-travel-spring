package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.controller.web.dto.UserAnalyticsDto;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.service.AnalyticsService;
import be.kdg.integration4.spring_backend.service.PermissionService;
import be.kdg.integration4.spring_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final PermissionService permissionService;

    @GetMapping("/analytics")
    public String redirectToCorrectAnalytics(@AuthenticationPrincipal final OidcUser oidcUser) {
        User currentUser = userService.getCurrentUser(oidcUser.getSubject());

        if (permissionService.canViewAdminAnalytics(currentUser)) {
            return "redirect:/analytics/admin";
        }

        return "redirect:/analytics/user";
    }

    @GetMapping("/analytics/user")
    public String showUserAnalytics(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        UserAnalyticsDto analytics = analyticsService.getStatisticsForUser(oidcUser.getSubject());

        model.addAttribute("analytics", analytics);

        return "user-analytics";
    }

    @GetMapping("/analytics/admin")
    public String showAdminAnalytics(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        User currentUser = userService.getCurrentUser(oidcUser.getSubject());

        if (!permissionService.canViewAdminAnalytics(currentUser)) {
            throw new AccessDeniedException("You are not allowed to view admin analytics.");
        }

        model.addAttribute("analytics", analyticsService.getAdminAnalytics() );

        return "admin-analytics";
    }

}

