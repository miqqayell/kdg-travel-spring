package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.UserRelation;
import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRelationRepository extends JpaRepository<UserRelation, Long> {
    // Finds active friends for a given user
    @Query("SELECT ur FROM UserRelation ur WHERE (ur.userOne.id = :userId OR ur.userTwo.id = :userId) AND ur.status = :status")
    List<UserRelation> findUserFriends(@Param("userId") Long userId, @Param("status") RelationStatus status);

    // Checks if a relation already exists between two specific users
    @Query("SELECT ur FROM UserRelation ur WHERE (ur.userOne.id = :id1 AND ur.userTwo.id = :id2) OR (ur.userOne.id = :id2 AND ur.userTwo.id = :id1)")
    Optional<UserRelation> findByUsers(@Param("id1") Long id1, @Param("id2") Long id2);
}