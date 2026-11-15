package be.kdg.integration4.spring_backend.controller.web.dto;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;

public record SuggestedTravelFriendDto(
        Long userId,
        String username,
        String profilePictureUrl,
        TransportMode transportMode,
        Double distanceKm,
        boolean sameTransportMode,
        String matchReason
) {
}