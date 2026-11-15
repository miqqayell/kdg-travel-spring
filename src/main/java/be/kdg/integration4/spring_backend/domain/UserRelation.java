package be.kdg.integration4.spring_backend.domain;

import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_relations")
@Getter
@Setter
@NoArgsConstructor
public class UserRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_one_id", nullable = false)
    private User userOne;

    @ManyToOne
    @JoinColumn(name = "user_two_id", nullable = false)
    private User userTwo;

    private LocalDateTime since;

    @Enumerated(EnumType.STRING)
    private RelationStatus status;

    private Boolean locationSharingEnabled;

    @OneToOne
    @JoinColumn(name = "chat_id", unique = true)
    private Chat chat;
}