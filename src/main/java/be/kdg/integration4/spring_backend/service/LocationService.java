package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.api.dto.UserLocationDto;
import be.kdg.integration4.spring_backend.domain.TravelGroupMembership;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.repository.TravelGroupMembershipRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final UserRepository userRepository;
    private final TravelGroupMembershipRepository membershipRepository;

    @Transactional
    public boolean toggleLocationSharing(String originalUserId) {
        User user = userRepository.findByOriginalId(originalUserId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setLocationSharingEnabled(!user.isLocationSharingEnabled());
        userRepository.save(user);
        return user.isLocationSharingEnabled();
    }

    @Transactional
    public void updateUserLocation(String originalUserId, Double latitude, Double longitude) {
        User user = userRepository.findByOriginalId(originalUserId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (user.isLocationSharingEnabled()) {
            user.setLastKnownLocationLatitude(latitude);
            user.setLastKnownLocationLongitude(longitude);
            user.setLastKnownLocation(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public List<UserLocationDto> getActiveGroupLocations(Long groupId) {
        // FIXED TYPO: It is findByTravelGroupId, NOT findByTravelGroupIdx
        List<TravelGroupMembership> memberships = membershipRepository.findByTravelGroupId(groupId);

        return memberships.stream()
                .map(membership -> membership.getUser())
                .filter(Objects::nonNull)
                .filter(user -> user.isLocationSharingEnabled())
                .filter(user -> user.getLastKnownLocationLatitude() != null)
                .filter(user -> user.getLastKnownLocationLongitude() != null)
                .filter(user -> user.getLastKnownLocation() != null)
                .filter(user -> user.getLastKnownLocation().isAfter(LocalDateTime.now().minusHours(2)))
                .map(user -> new UserLocationDto(
                        user.getId(),
                        user.getUsername(),
                        user.getProfilePictureUrl(),
                        user.getLastKnownLocationLatitude(),
                        user.getLastKnownLocationLongitude(),
                        user.getLastKnownLocation().toString()
                ))
                .collect(Collectors.toList());
    }
}