package be.kdg.integration4.spring_backend.domain;

import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(length = 1000)
    private String description;

    private Double locationLatitude;
    private Double locationLongitude;
    private String locationAddress;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Column(length = 2000)
    private String imageUrl;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @JsonIgnore
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TravelGroup> travelGroups = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActivityParticipation> participations = new ArrayList<>();
}