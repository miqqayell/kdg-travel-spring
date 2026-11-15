package be.kdg.integration4.spring_backend.controller.api.dto;

public record WalkingDistanceResponse(
        double distanceMeters,
        double distanceKm,
        int durationSeconds
) {
}
