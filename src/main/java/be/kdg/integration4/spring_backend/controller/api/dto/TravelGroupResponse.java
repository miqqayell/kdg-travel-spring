package be.kdg.integration4.spring_backend.controller.api.dto;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;

import java.time.LocalDateTime;

public record TravelGroupResponse(
        Long id,
        Long version,
        Long activityId,
        String activityTitle,
        Long createdByUserId,
        String title,
        String description,
        Double startPointLatitude,
        Double startPointLongitude,
        Double endPointLatitude,
        Double endPointLongitude,
        TransportMode transportationMode,
        Double distanceKm,
        LocalDateTime meetupTime,
        Integer maxSize,
        Long currentMemberCount,
        Boolean contributionRequired,
        TravelGroupStatus status
) {
}
