package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;

import java.time.LocalDateTime;

public record TravelGroupFilterCriteria(
        String keyword,
        Long activityId,
        TransportMode transportationMode,
        Integer groupSize,
        String preferredStartAddress,
        Double preferredStartLatitude,
        Double preferredStartLongitude,
        Double startProximityMeters,
        String preferredEndAddress,
        Double preferredEndLatitude,
        Double preferredEndLongitude,
        Double endProximityMeters,
        LocalDateTime meetupStart,
        LocalDateTime meetupEnd,
        Boolean friendsOnly
) {
}
