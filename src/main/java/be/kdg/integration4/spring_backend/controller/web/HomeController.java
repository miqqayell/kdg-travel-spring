package be.kdg.integration4.spring_backend.controller.web;

import be.kdg.integration4.spring_backend.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ActivityService activityService;

    @GetMapping("/")
    public String home(Authentication authentication) {

        // If you are logged in
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {
            return "redirect:/activities";
        }

        // If you are not logged in

        return "landing";

    }
}