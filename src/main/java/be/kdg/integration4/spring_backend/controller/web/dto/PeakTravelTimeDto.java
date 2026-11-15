package be.kdg.integration4.spring_backend.controller.web.dto;

public record PeakTravelTimeDto(
        String timeLabel,
        long tripCount
) {
}
