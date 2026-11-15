package be.kdg.integration4.spring_backend.controller.web.dto;

public record TransportModePopularityDto(
        String transportMode,
        long groupCount
) {
}
