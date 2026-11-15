package be.kdg.integration4.spring_backend.domain;

import be.kdg.integration4.spring_backend.domain.enums.Badge;
import be.kdg.integration4.spring_backend.domain.enums.Role;
import be.kdg.integration4.spring_backend.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;




    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_badges", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "badge")
    private Set<Badge> badges = new HashSet<>();

    // Add these methods so the HTML can read the list!
    public Set<Badge> getBadges() {
        return badges;
    }

    public void setBadges(Set<Badge> badges) {
        this.badges = badges;
    }

    public void addBadge(Badge badge) {
        this.badges.add(badge);
    }

    public boolean hasBadge(Badge badge) {
        return this.badges.contains(badge);
    }

    // SSO / Google Login ID
    @Column(unique = true)
    private String originalId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    private String profilePictureUrl;

    @Column(length = 500)
    private String bio;

    private LocalDateTime lastLogin;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime suspendedUntil;

    // --- Location Tracking Fields---
    private LocalDateTime lastKnownLocation;
    private Double lastKnownLocationLatitude;
    private Double lastKnownLocationLongitude;

    // Privacy toggle for location sharing
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean locationSharingEnabled = false;

    // Add these explicitly so the compiler stops complaining!
    public boolean isLocationSharingEnabled() {
        return locationSharingEnabled;
    }

    public void setLocationSharingEnabled(boolean locationSharingEnabled) {
        this.locationSharingEnabled = locationSharingEnabled;
    }


    // --- Group Relations ---
    @OneToMany(mappedBy = "user")
    private List<TravelGroupMembership> groupMemberships;

    // SSO Email Validation
    public static boolean isEmailValid(String email) {
        return email != null && !email.isBlank() &&
                (email.endsWith("@student.kdg.be") || email.endsWith("@kdg.be"));
    }

}
