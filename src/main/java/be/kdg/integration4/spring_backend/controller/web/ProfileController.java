package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final FriendService friendService;

    @GetMapping("/profile")
    public String showProfilePage(Model model, @AuthenticationPrincipal OidcUser oidcUser) {
        User currentUser = userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new IllegalStateException("Logged in user not found in database"));

        model.addAttribute("currentUserRole", currentUser.getRole());
        model.addAttribute("currentUserStatus", currentUser.getStatus());

        model.addAttribute("bio", currentUser.getBio());
        model.addAttribute("badges", currentUser.getBadges());
        model.addAttribute("frequentBuddies", friendService.getFrequentBuddies(oidcUser.getSubject()));

        return "profile";
    }

    @PostMapping("/profile/bio")
    public String updateBio(@RequestParam("bio") String newBio, @AuthenticationPrincipal OidcUser oidcUser) {
        User currentUser = userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new IllegalStateException("Logged in user not found in database"));

        currentUser.setBio(newBio);
        userRepository.save(currentUser);

        return "redirect:/profile";
    }
}