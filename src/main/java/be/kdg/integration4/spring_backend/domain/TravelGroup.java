package be.kdg.integration4.spring_backend.domain;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import be.kdg.integration4.spring_backend.exception.BusinessRuleViolationException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "travel_groups")
@Check(constraints = """
    max_size between 1 and 99
    and (distance_km is null or distance_km >= 0)
    and start_point_latitude between -90 and 90
    and start_point_longitude between -180 and 180
    and end_point_latitude between -90 and 90
    and end_point_longitude between -180 and 180
    """)
@Getter
@NoArgsConstructor
public class TravelGroup {
    private static final int MAX_GROUP_SIZE = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, name = "start_point_latitude")
    private Double startPointLatitude;

    @Column(nullable = false, name = "start_point_longitude")
    private Double startPointLongitude;

    @Column(nullable = false, name = "end_point_latitude")
    private Double endPointLatitude;

    @Column(nullable = false, name = "end_point_longitude")
    private Double endPointLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportMode transportationMode;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "meetup_time")
    private LocalDateTime meetupTime;

    @Column(nullable = false, name = "max_size")
    private Integer maxSize;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean contributionRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TravelGroupStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", unique = true)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @OneToOne(mappedBy = "travelGroup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PublicTransportSelection publicTransportSelection;

    @OneToMany(mappedBy = "travelGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TravelGroupMembership> memberships = new ArrayList<>();

    public static TravelGroup create(
            String title,
            String description,
            Double startPointLatitude,
            Double startPointLongitude,
            Double endPointLatitude,
            Double endPointLongitude,
            TransportMode transportationMode,
            Double distanceKm,
            LocalDateTime meetupTime,
            Integer maxSize,
            Boolean contributionRequired,
            User creator,
            Activity activity,
            LocalDateTime now
    ) {
        TravelGroup group = new TravelGroup();
        group.createdByUser = creator;
        group.activity = activity;
        group.status = TravelGroupStatus.ACTIVE;

        group.applyDetails(
                title,
                description,
                startPointLatitude,
                startPointLongitude,
                endPointLatitude,
                endPointLongitude,
                transportationMode,
                distanceKm,
                meetupTime,
                maxSize,
                contributionRequired
        );

        group.addMember(creator, now);
        return group;
    }

    public static TravelGroup create(
            String title,
            String description,
            Double startPointLatitude,
            Double startPointLongitude,
            Double endPointLatitude,
            Double endPointLongitude,
            TransportMode transportationMode,
            Double distanceKm,
            LocalDateTime meetupTime,
            Integer maxSize,
            User creator,
            Activity activity,
            LocalDateTime now
    ) {
        return create(
                title,
                description,
                startPointLatitude,
                startPointLongitude,
                endPointLatitude,
                endPointLongitude,
                transportationMode,
                distanceKm,
                meetupTime,
                maxSize,
                false,
                creator,
                activity,
                now
        );
    }

    public void updateDetails(
            String title,
            String description,
            Double startPointLatitude,
            Double startPointLongitude,
            Double endPointLatitude,
            Double endPointLongitude,
            TransportMode transportationMode,
            Double distanceKm,
            LocalDateTime meetupTime,
            Integer maxSize,
            Boolean contributionRequired,
            Activity activity
    ) {
        ensureActive();

        applyDetails(
                title,
                description,
                startPointLatitude,
                startPointLongitude,
                endPointLatitude,
                endPointLongitude,
                transportationMode,
                distanceKm,
                meetupTime,
                maxSize,
                contributionRequired
        );

        this.activity = activity;
    }

    public void markDeleted() {
        ensureActive();
        this.status = TravelGroupStatus.DELETED;
    }

    public void setPublicTransportSelection(PublicTransportSelection selection) {
        clearPublicTransportSelection();
        if (selection == null) {
            return;
        }

        this.publicTransportSelection = selection;
        selection.attachToTravelGroup(this);
    }

    public void clearPublicTransportSelection() {
        if (publicTransportSelection == null) {
            return;
        }

        publicTransportSelection.detachFromTravelGroup();
        publicTransportSelection = null;
    }

    public void addMember(User user, LocalDateTime joinedAt) {
        ensureActive();

        if (isFull()) {
            throw new BusinessRuleViolationException("Travel group is full");
        }

        if (hasMember(user.getId())) {
            throw new BusinessRuleViolationException("User is already a member of this travel group");
        }

        memberships.add(TravelGroupMembership.create(user, this, joinedAt));
    }

    public long getMemberCount() {
        return memberships.size();
    }

    private boolean isFull() {
        return getMemberCount() >= maxSize;
    }

    private boolean hasMember(Long userId) {
        return memberships.stream()
                .anyMatch(membership -> membership.getUser().getId().equals(userId));
    }

    private void ensureActive() {
        if (status != TravelGroupStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Deleted travel groups cannot be modified");
        }
    }

    private void validateMaxSize(Integer maxSize) {
        if (maxSize > MAX_GROUP_SIZE) {
            throw new BusinessRuleViolationException("maxSize cannot be greater than " + MAX_GROUP_SIZE);
        }

        if (maxSize < getMemberCount()) {
            throw new BusinessRuleViolationException("maxSize cannot be smaller than the current member count");
        }
    }

    private void applyDetails(
            String title,
            String description,
            Double startPointLatitude,
            Double startPointLongitude,
            Double endPointLatitude,
            Double endPointLongitude,
            TransportMode transportationMode,
            Double distanceKm,
            LocalDateTime meetupTime,
            Integer maxSize,
            Boolean contributionRequired

            ) {
        validateMaxSize(maxSize);

        this.title = title.trim();
        this.description = normalize(description);
        this.startPointLatitude = startPointLatitude;
        this.startPointLongitude = startPointLongitude;
        this.endPointLatitude = endPointLatitude;
        this.endPointLongitude = endPointLongitude;
        this.transportationMode = transportationMode;
        this.distanceKm = distanceKm;
        this.meetupTime = meetupTime;
        this.maxSize = maxSize;
        this.contributionRequired = transportationMode == TransportMode.CARPOOL && Boolean.TRUE.equals(contributionRequired);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
