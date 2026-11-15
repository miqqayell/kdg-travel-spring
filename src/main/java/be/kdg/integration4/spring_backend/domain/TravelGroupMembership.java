package be.kdg.integration4.spring_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "travel_group_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_travel_group_membership_user_group",
                        columnNames = {"user_id", "travel_group_id"}
                )
        }
)
@Getter
@NoArgsConstructor
public class TravelGroupMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "travel_group_id", nullable = false)
    private TravelGroup travelGroup;

    private LocalDateTime joinedAt;
    private Boolean locationSharingEnabled;
    private Long getOnAtStopId;

    public static TravelGroupMembership create(User user, TravelGroup travelGroup, LocalDateTime joinedAt) {
        TravelGroupMembership membership = new TravelGroupMembership();
        membership.user = user;
        membership.travelGroup = travelGroup;
        membership.joinedAt = joinedAt;
        membership.locationSharingEnabled = Boolean.FALSE;
        return membership;
    }
}