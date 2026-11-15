package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.web.dto.ReportDto;
import be.kdg.integration4.spring_backend.domain.Activity;
import be.kdg.integration4.spring_backend.domain.Report;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.ReportStatus;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.ReportRepository;
import be.kdg.integration4.spring_backend.repository.TravelGroupRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final TravelGroupRepository travelGroupRepository;

    @Transactional
    public void createReport(ReportDto request, String reporterOriginalId) {
        User reporter = userRepository.findByOriginalId(reporterOriginalId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found"));

        Activity reportedActivity = null;
        TravelGroup reportedTravelGroup = null;
        User reportedUser = null;

        if (request.getReportedActivityId() != null) {
            reportedActivity = activityRepository.findById(request.getReportedActivityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
            // Checks if a creator exists before assigning it
            if (reportedActivity.getCreatedByUser() != null) {
                reportedUser = reportedActivity.getCreatedByUser();
            }
        }

        if (request.getReportedTravelGroupId() != null) {
            reportedTravelGroup = travelGroupRepository.findById(request.getReportedTravelGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Travel group not found"));
            // Checks if a creator exists before assigning it
            if (reportedTravelGroup.getCreatedByUser() != null) {
                reportedUser = reportedTravelGroup.getCreatedByUser();
            }
        }

        if (request.getReportedUserId() != null) {
            reportedUser = userRepository.findById(request.getReportedUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reported user not found"));
        }

        if (reportedUser == null && reportedActivity == null && reportedTravelGroup == null) {
            throw new BadRequestException("A report must target a user, activity, or travel group.");
        }

        if (reportedUser != null && reportedUser.getId().equals(reporter.getId())) {
            throw new BadRequestException("You cannot report yourself.");
        }

        Report report = new Report();
        report.setReporterUser(reporter);
        report.setReportedUser(reportedUser);
        report.setReportedActivity(reportedActivity);
        report.setReportedTravelGroup(reportedTravelGroup);
        report.setType(request.getType());
        report.setReason(request.getReason());
        report.setExtraInformation(request.getExtraInformation());
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(LocalDateTime.now());

        reportRepository.save(report);
    }
}