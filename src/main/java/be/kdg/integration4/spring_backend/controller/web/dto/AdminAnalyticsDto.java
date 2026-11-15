package be.kdg.integration4.spring_backend.controller.web.dto;

import java.util.List;

public record AdminAnalyticsDto(
        double averageGroupSize,
        List<ActivityPopularityDto> mostPopularActivities,
        List<TransportModePopularityDto> popularTransportModes,
        List<PeakTravelTimeDto> peakTravelTimes,
        List<Co2SavingOverTimeDto> co2SavingsOverTime
) {
}
