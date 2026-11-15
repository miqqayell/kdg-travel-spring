package be.kdg.integration4.spring_backend.controller.web.dto;

import be.kdg.integration4.spring_backend.domain.Activity;

import java.util.List;

public record AdminEventsDto(
        long pendingEvents,
        long verifiedEvents,
        long totalEvents,
        List<Activity> pendingActivities,
        List<Activity> allActivities
) {
}
