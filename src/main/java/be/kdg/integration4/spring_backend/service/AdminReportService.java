package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.domain.Report;
import be.kdg.integration4.spring_backend.domain.User;
import be.kdg.integration4.spring_backend.domain.enums.ReportStatus;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.ReportRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<Report> getOpenReports() {
        return reportRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW)
        );
    }

    @Transactional(readOnly = true)
    public long countOpenReports() {
        return reportRepository.countByStatus(ReportStatus.OPEN)
                + reportRepository.countByStatus(ReportStatus.IN_REVIEW);
    }

    @Transactional
    public Report getReportAndMarkInReview(Long reportId, String moderatorOriginalId) {
        Report report = getReport(reportId);

        if (report.getStatus() == ReportStatus.OPEN) {
            User moderator = userRepository.findByOriginalId(moderatorOriginalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Moderator not found"));

            report.setStatus(ReportStatus.IN_REVIEW);
            report.setHandledByModerator(moderator);
        }

        return report;
    }

    @Transactional
    public void dismissReport(Long reportId, String moderatorOriginalId) {
        Report report = getReport(reportId);
        report.setStatus(ReportStatus.DISMISSED);
        report.setHandledByModerator(getModerator(moderatorOriginalId));
    }

    @Transactional
    public void resolveReport(Long reportId, String moderatorOriginalId) {
        Report report = getReport(reportId);

        report.setStatus(ReportStatus.RESOLVED);
        report.setHandledByModerator(getModerator(moderatorOriginalId));

        notificationService.notifyReportProcessed(report);
    }

    @Transactional
    public void warnUser(Long reportId, String moderatorOriginalId) {
        Report report = getReport(reportId);
        report.setHandledByModerator(getModerator(moderatorOriginalId));
        notificationService.notifyUserWarning(report.getReportedUser(), report);
    }

    @Transactional
    public void suspendUser(Long reportId, String moderatorOriginalId, int days) {
        Report report = getReport(reportId);
        User reportedUser = report.getReportedUser();

        reportedUser.setStatus(UserStatus.SUSPENDED);
        reportedUser.setSuspendedUntil(LocalDateTime.now().plusDays(days));

        report.setHandledByModerator(getModerator(moderatorOriginalId));

        userRepository.save(reportedUser);
    }

    @Transactional
    public void banUser(Long reportId, String moderatorOriginalId) {
        Report report = getReport(reportId);
        User reportedUser = report.getReportedUser();

        reportedUser.setStatus(UserStatus.BANNED);
        reportedUser.setSuspendedUntil(null);

        report.setHandledByModerator(getModerator(moderatorOriginalId));

        userRepository.save(reportedUser);
    }

    private Report getReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private User getModerator(String originalId) {
        return userRepository.findByOriginalId(originalId)
                .orElseThrow(() -> new ResourceNotFoundException("Moderator not found"));
    }
}