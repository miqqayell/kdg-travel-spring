package be.kdg.integration4.spring_backend.service;

import java.time.LocalDateTime;

public record ActivityFilterCriteria(
        String keyword,
        LocalDateTime startsAfter,
        LocalDateTime startsBefore,
        String locationAddress,
        Double locationLatitude,
        Double locationLongitude,
        Double locationProximityMeters,
        Boolean friendsOnly
) {
}
