package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@RequiredArgsConstructor
@ControllerAdvice
public class GlobalControllerAdvice {


    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(@AuthenticationPrincipal OAuth2User user) {
        return user != null;
    }

    @ModelAttribute("userName")
    public String userName(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) return null;
        return user.getAttribute("name"); // Google's profile name
    }

    @ModelAttribute("userAvatar")
    public String userAvatar(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) return null;
        return user.getAttribute("picture"); // Google's profile picture
    }

    @ModelAttribute("userEmail")
    public String userEmail(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) return null;
        // Extracts the email address from the Google Profile
        return user.getAttribute("email");
    }

    @ModelAttribute("canAccessAdminDashboard")
    public boolean canAccessAdminDashboard(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            return false;
        }

        return userRepository.findByOriginalId(oidcUser.getSubject())
                .map(permissionService::canAccessAdminDashboard)
                .orElse(false);
    }
}