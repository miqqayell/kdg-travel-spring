package be.kdg.integration4.spring_backend.controller.web.dto;

public record ActivityPopularityDto(
        String activityTitle,
        long groupCount
) {
}
