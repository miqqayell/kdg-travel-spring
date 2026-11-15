package be.kdg.integration4.spring_backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "frequent_buddies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "buddy_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class FrequentBuddy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(optional = false)
    @JoinColumn(name = "buddy_id", nullable = false)
    private User buddy;

    private LocalDateTime savedAt;
}
