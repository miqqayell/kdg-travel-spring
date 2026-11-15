package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.Report;
import be.kdg.integration4.spring_backend.domain.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    long countByStatus(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusInOrderByCreatedAtDesc(Collection<ReportStatus> statuses);
}