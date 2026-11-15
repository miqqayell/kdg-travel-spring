package be.kdg.integration4.spring_backend.controller.web.dto;

import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.Report;

import java.util.List;

public record AdminOverviewDto(
        long pendingReports,
        long pendingEvents,
        long totalUsers,
        long suspendedUsers,
        long bannedUsers,
        List<Report> openReports,
        List<Activity> pendingActivities
) {
}
