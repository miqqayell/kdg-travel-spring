package be.kdg.integration4.spring_backend.config;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final TravelGroupRepository travelGroupRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        log.info("Seeding database with dummy data");

        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setOriginalId("dev-user-1");
        user.setUsername("AlexJohnson99");
        user.setEmail("student@student.kdg.be");
        user.setProfilePictureUrl(null);
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLogin(now);
        user.setLastKnownLocation(now);
        user.setLastKnownLocationLatitude(40.7128);
        user.setLastKnownLocationLongitude(-74.0060);

        User savedUser = userRepository.save(user);

        Activity musicFest = new Activity();
        musicFest.setTitle("Music Festival");
        musicFest.setDescription("Annual outdoor music festival.");
        musicFest.setStartTime(now.plusDays(5));
        musicFest.setEndTime(now.plusDays(5).plusHours(8));
        musicFest.setLocationAddress("Central Park, NY");
        musicFest.setLocationLatitude(40.7812);
        musicFest.setLocationLongitude(-73.9665);
        musicFest.setVerificationStatus(VerificationStatus.VERIFIED);
        musicFest.setCreatedByUser(savedUser);

        Activity savedMusicFest = activityRepository.save(musicFest);

        Activity hiking = new Activity();
        hiking.setTitle("Hiking Trip");
        hiking.setDescription("Group hike up the mountain.");
        hiking.setStartTime(now.plusDays(10));
        hiking.setEndTime(now.plusDays(10).plusHours(4));
        hiking.setLocationAddress("Bear Mountain");
        hiking.setLocationLatitude(41.3126);
        hiking.setLocationLongitude(-74.0071);
        hiking.setVerificationStatus(VerificationStatus.PENDING);
        hiking.setCreatedByUser(savedUser);

        activityRepository.save(hiking);

        TravelGroup travelGroup = TravelGroup.create(
                "Festival Bike Buddies",
                "Cycling together to the festival.",
                40.730610,
                -73.935242,
                savedMusicFest.getLocationLatitude(),
                savedMusicFest.getLocationLongitude(),
                TransportMode.BIKE,
                8.4,
                now.plusHours(2),
                6,
                false,
                savedUser,
                savedMusicFest,
                now
        );

        travelGroupRepository.save(travelGroup);

        log.info("Database seeded successfully");
    }
}