package be.kdg.integration4.spring_backend.controller.web.dto;

import java.util.List;

public record UserAnalyticsDto(
        long createdTrips,
        long joinedTrips,
        double co2SavingsKg,
        List<Co2SavingOverTimeDto> co2SavingsOverTime
) {
}
