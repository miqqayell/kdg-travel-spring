package be.kdg.integration4.spring_backend.domain;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_participations")
@Getter
@Setter
@NoArgsConstructor
public class ActivityParticipation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Enumerated(EnumType.STRING)
    private TransportMode transportMode;

    private LocalDateTime joinedAt;
    private Double distanceKm;
}