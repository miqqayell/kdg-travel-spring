package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.FriendService;
import be.kdg.integration4.spring_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class UsersController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendService friendService;

    @GetMapping("/users")
    public String showUsersPage(@RequestParam(name = "q", required = false) String query,
                                @AuthenticationPrincipal OidcUser oidcUser,
                                Model model) {
        model.addAttribute("pageTitle", "Find friends");
        model.addAttribute("searchQuery", query == null ? "" : query.trim());
        model.addAttribute("users", userService.getUsersForDirectory(query, oidcUser.getSubject()));
        return "users";
    }

    @GetMapping("/users/{id}")
    public String viewUserProfile(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser, Model model) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fetch the exact status: "NONE", "PENDING_SENT", "FRIENDS", etc.
        String relationStatus = userService.getRelationStatus(oidcUser.getSubject(), targetUser.getId());

        model.addAttribute("targetUser", targetUser);
        model.addAttribute("relationStatus", relationStatus);
        model.addAttribute("isFriend", "FRIENDS".equals(relationStatus));
        model.addAttribute("isFrequentBuddy", friendService.isFrequentBuddy(oidcUser.getSubject(), targetUser.getId()));

        return "user-profile";
    }
}