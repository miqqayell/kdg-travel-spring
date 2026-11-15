package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.web.dto.AdminOverviewDto;
import be.kdg.integration4.spring_backend.domain.enums.ReportStatus;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import be.kdg.integration4.spring_backend.repository.ActivityRepository;
import be.kdg.integration4.spring_backend.repository.ReportRepository;
import be.kdg.integration4.spring_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final ReportRepository reportRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AdminReportService adminReportService;

    @Transactional(readOnly = true)
    public AdminOverviewDto getOverview() {
        List<ReportStatus> unfinishedStatuses = List.of(
                ReportStatus.OPEN,
                ReportStatus.IN_REVIEW
        );

        return new AdminOverviewDto(
                reportRepository.countByStatus(ReportStatus.OPEN)
                        + reportRepository.countByStatus(ReportStatus.IN_REVIEW),
                activityRepository.countByVerificationStatus(VerificationStatus.PENDING),
                userRepository.count(),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countByStatus(UserStatus.BANNED),
                reportRepository.findByStatusInOrderByCreatedAtDesc(unfinishedStatuses),
                activityRepository.findByVerificationStatusOrderByStartTimeAscIdAsc(VerificationStatus.PENDING)
        );
    }


}
