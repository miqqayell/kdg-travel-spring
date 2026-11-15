package be.kdg.integration4.spring_backend.controller.api.dto;

public record UserLocationDto(
        Long userId,
        String username,
        String avatarUrl,
        Double latitude,
        Double longitude,
        String lastUpdated
) {}