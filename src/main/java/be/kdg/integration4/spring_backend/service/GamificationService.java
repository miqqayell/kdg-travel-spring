package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Badge;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService; // Assuming this calculates their total trips/CO2

    @Transactional
    public void checkAndAwardBadges(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();

        // Let's pretend we have these methods in AnalyticsService to get their stats
        // (You might need to adjust these based on how you calculate stats)
        int totalTrips = analyticsService.getTotalTripsForUser(userId);
        double totalCo2Saved = analyticsService.getTotalCo2SavedForUser(userId);
        int transitTrips = analyticsService.getTotalTransitTripsForUser(userId);

        // Rule 1: First Trip
        if (totalTrips >= 1 && !user.hasBadge(Badge.FIRST_TRIP)) {
            awardBadge(user, Badge.FIRST_TRIP);
        }

        // Rule 2: 5 Trips
        if (totalTrips >= 5 && !user.hasBadge(Badge.ECO_WARRIOR)) {
            awardBadge(user, Badge.ECO_WARRIOR);
        }

        // Rule 3: 10 Transit Trips
        if (transitTrips >= 10 && !user.hasBadge(Badge.TRANSIT_MASTER)) {
            awardBadge(user, Badge.TRANSIT_MASTER);
        }

        // Rule 4: CO2 Saved
        if (totalCo2Saved >= 50.0 && !user.hasBadge(Badge.CO2_CHAMPION)) {
            awardBadge(user, Badge.CO2_CHAMPION);
        }
    }

    private void awardBadge(User user, Badge badge) {
        log.info("User {} earned badge: {}", user.getUsername(), badge.name());
        user.addBadge(badge);
        userRepository.save(user);

        // Send them a notification!
        notificationService.notifyBadgeEarned(user, badge);
    }
}