package be.kdg.integration4.spring_backend.controller.api;

import be.kdg.integration4.spring_backend.domain.Notification;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.NotificationType;
import be.kdg.integration4.spring_backend.repository.NotificationRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import be.kdg.integration4.spring_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationRestController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository; // Added for the test
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Notification>> getMyNotifications(@AuthenticationPrincipal OidcUser oidcUser) {
        User user = userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId()));
    }
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal OidcUser oidcUser) {
        User user = userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new RuntimeException("User not found"));

        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    // NEW TEST ENDPOINT
    @GetMapping("/test")
    public ResponseEntity<String> forceTestNotification(@AuthenticationPrincipal OidcUser oidcUser) {
        User user = userRepository.findByOriginalId(oidcUser.getSubject())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Force a fake notification into the database
        Notification testNote = new Notification();
        testNote.setRecipientUser(user);
        testNote.setTitle("System Test Successful");
        testNote.setDescription("If you can see this, the notification database is working perfectly!");
        testNote.setNotificationType(NotificationType.ACTIVITY_UPDATE);
        testNote.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(testNote);

        return ResponseEntity.ok("Test notification forced into database! Now go check /api/notifications");
    }
}

