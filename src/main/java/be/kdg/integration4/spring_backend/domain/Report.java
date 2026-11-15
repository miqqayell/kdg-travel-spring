package be.kdg.integration4.spring_backend.domain;

import be.kdg.integration4.spring_backend.domain.enums.ReportStatus;
import be.kdg.integration4.spring_backend.domain.enums.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType type;

    @Column(length = 1000)
    private String reason;

    @Column(length = 3000)
    private String extraInformation;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @ManyToOne
    @JoinColumn(name = "reported_user_id") // Fix: Removed nullable
    private User reportedUser;

    @ManyToOne
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporterUser;

    @ManyToOne
    @JoinColumn(name = "reported_activity_id")
    private Activity reportedActivity;

    @ManyToOne
    @JoinColumn(name = "reported_travel_group_id")
    private TravelGroup reportedTravelGroup;

    @ManyToOne
    @JoinColumn(name = "handled_by_moderator_id")
    private User handledByModerator;
}