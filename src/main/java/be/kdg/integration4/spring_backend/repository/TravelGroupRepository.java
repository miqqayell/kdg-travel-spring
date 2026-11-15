package be.kdg.integration4.spring_backend.repository;

import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.domain.enums.RelationStatus;
import be.kdg.integration4.spring_backend.domain.enums.TravelGroupStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TravelGroupRepository extends JpaRepository<TravelGroup, Long> {

    interface ActivityTravelGroupCount {
        Long getActivityId();

        long getGroupCount();
    }

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    List<TravelGroup> findByActivityIdAndStatusOrderById(Long activityId, TravelGroupStatus status);

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    List<TravelGroup> findByCreatedByUserIdAndStatusOrderById(Long createdByUserId, TravelGroupStatus status);

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    List<TravelGroup> findByStatusOrderById(TravelGroupStatus status);

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    @Query("""
            SELECT DISTINCT tg
            FROM TravelGroup tg
            JOIN tg.memberships membership
            WHERE membership.user.id = :userId
              AND tg.createdByUser.id <> :userId
              AND tg.status = :status
            ORDER BY tg.id
            """)
    List<TravelGroup> findJoinedGroupsExcludingCreatedGroups(
            @Param("userId") Long userId,
            @Param("status") TravelGroupStatus status
    );

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    @Query("""
            SELECT DISTINCT tg
            FROM TravelGroup tg
            JOIN tg.memberships currentMembership
            WHERE currentMembership.user.id = :currentUserId
              AND tg.createdByUser.id <> :currentUserId
              AND tg.status = :status
              AND EXISTS (
                  SELECT 1
                  FROM TravelGroupMembership membership, UserRelation relation
                  WHERE membership.travelGroup = tg
                    AND relation.status = :relationStatus
                    AND (
                        (relation.userOne.id = :currentUserId AND relation.userTwo.id = membership.user.id)
                        OR (relation.userTwo.id = :currentUserId AND relation.userOne.id = membership.user.id)
                    )
              )
            ORDER BY tg.id
            """)
    List<TravelGroup> findJoinedGroupsExcludingCreatedGroupsWithFriendMembers(
            @Param("currentUserId") Long currentUserId,
            @Param("status") TravelGroupStatus status,
            @Param("relationStatus") RelationStatus relationStatus
    );

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    @Query("""
            SELECT DISTINCT tg
            FROM TravelGroup tg
            WHERE tg.createdByUser.id = :createdByUserId
              AND tg.status = :status
              AND EXISTS (
                  SELECT 1
                  FROM TravelGroupMembership membership, UserRelation relation
                  WHERE membership.travelGroup = tg
                    AND relation.status = :relationStatus
                    AND (
                        (relation.userOne.id = :currentUserId AND relation.userTwo.id = membership.user.id)
                        OR (relation.userTwo.id = :currentUserId AND relation.userOne.id = membership.user.id)
                    )
              )
            ORDER BY tg.id
            """)
    List<TravelGroup> findByCreatorAndStatusWithFriendMembers(
            @Param("createdByUserId") Long createdByUserId,
            @Param("currentUserId") Long currentUserId,
            @Param("status") TravelGroupStatus status,
            @Param("relationStatus") RelationStatus relationStatus
    );

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    @Query("""
            SELECT DISTINCT tg
            FROM TravelGroup tg
            WHERE tg.activity.id = :activityId
              AND tg.status = :status
              AND EXISTS (
                  SELECT 1
                  FROM TravelGroupMembership membership, UserRelation relation
                  WHERE membership.travelGroup = tg
                    AND relation.status = :relationStatus
                    AND (
                        (relation.userOne.id = :currentUserId AND relation.userTwo.id = membership.user.id)
                        OR (relation.userTwo.id = :currentUserId AND relation.userOne.id = membership.user.id)
                    )
              )
            ORDER BY tg.id
            """)
    List<TravelGroup> findByActivityAndStatusWithFriendMembers(
            @Param("activityId") Long activityId,
            @Param("currentUserId") Long currentUserId,
            @Param("status") TravelGroupStatus status,
            @Param("relationStatus") RelationStatus relationStatus
    );

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    @Query("""
            SELECT DISTINCT tg
            FROM TravelGroup tg
            WHERE tg.status = :status
              AND EXISTS (
                  SELECT 1
                  FROM TravelGroupMembership membership, UserRelation relation
                  WHERE membership.travelGroup = tg
                    AND relation.status = :relationStatus
                    AND (
                        (relation.userOne.id = :currentUserId AND relation.userTwo.id = membership.user.id)
                        OR (relation.userTwo.id = :currentUserId AND relation.userOne.id = membership.user.id)
                    )
              )
            ORDER BY tg.id
            """)
    List<TravelGroup> findByStatusWithFriendMembers(
            @Param("currentUserId") Long currentUserId,
            @Param("status") TravelGroupStatus status,
            @Param("relationStatus") RelationStatus relationStatus
    );

    @EntityGraph(attributePaths = {"activity", "createdByUser", "memberships"})
    Optional<TravelGroup> findByIdAndCreatedByUserIdAndStatus(Long id, Long createdByUserId, TravelGroupStatus status);

    @EntityGraph(attributePaths = {"createdByUser"})
    @Query("""
            SELECT tg
            FROM TravelGroup tg
            WHERE tg.id = :id
            """)
    Optional<TravelGroup> findByIdWithCreatedByUser(@Param("id") Long id);

    @EntityGraph(attributePaths = {"createdByUser", "memberships", "memberships.user"})
    @Query("""
            SELECT tg
            FROM TravelGroup tg
            WHERE tg.id = :id
            """)
    Optional<TravelGroup> findByIdWithMembers(@Param("id") Long id);

    @Query("""
            SELECT tg.activity.id AS activityId, COUNT(tg) AS groupCount
            FROM TravelGroup tg
            WHERE tg.status = :status
              AND tg.activity.id IN :activityIds
            GROUP BY tg.activity.id
            """)
    List<ActivityTravelGroupCount> countByActivityIdsAndStatus(
            @Param("activityIds") List<Long> activityIds,
            @Param("status") TravelGroupStatus status
    );

    @Query("""
            SELECT CASE WHEN COUNT(DISTINCT tg) > 0 THEN true ELSE false END
            FROM TravelGroup tg
            LEFT JOIN tg.memberships membership
            WHERE tg.activity.id = :activityId
              AND tg.status = :status
              AND (
                  tg.createdByUser.id = :userId
                  OR membership.user.id = :userId
              )
            """)
    boolean existsByActivityIdAndUserIdAndStatus(
            @Param("activityId") Long activityId,
            @Param("userId") Long userId,
            @Param("status") TravelGroupStatus status
    );

    @Query("""
            SELECT DISTINCT tg.activity.id
            FROM TravelGroup tg
            LEFT JOIN tg.memberships membership
            WHERE tg.status = :status
              AND tg.activity IS NOT NULL
              AND (
                  tg.createdByUser.id = :userId
                  OR membership.user.id = :userId
              )
            """)
    List<Long> findActivityIdsForUserAndStatus(
            @Param("userId") Long userId,
            @Param("status") TravelGroupStatus status
    );

    @Query("""
            SELECT AVG(SIZE(tg.memberships))
            FROM TravelGroup tg
            WHERE tg.status = :status
            """)
    Double findAverageGroupSizeByStatus(@Param("status") TravelGroupStatus status);

    @Query("""
            SELECT tg.activity.title, COUNT(tg)
            FROM TravelGroup tg
            WHERE tg.status = :status
              AND tg.activity IS NOT NULL
            GROUP BY tg.activity.title
            ORDER BY COUNT(tg) DESC, tg.activity.title ASC
            """)
    List<Object[]> findMostPopularActivities(@Param("status") TravelGroupStatus status);

    @Query("""
            SELECT tg.transportationMode, COUNT(tg)
            FROM TravelGroup tg
            WHERE tg.status = :status
            GROUP BY tg.transportationMode
            ORDER BY COUNT(tg) DESC, tg.transportationMode ASC
            """)
    List<Object[]> findPopularTransportModes(@Param("status") TravelGroupStatus status);

    @EntityGraph(attributePaths = {"memberships"})
    @Query("""
            SELECT tg
            FROM TravelGroup tg
            WHERE tg.status = :status
            ORDER BY tg.id
            """)
    List<TravelGroup> findGroupsForCo2Analytics(@Param("status") TravelGroupStatus status);

    long countByCreatedByUserIdAndStatus(Long userId, TravelGroupStatus status);

    @EntityGraph(attributePaths = {"memberships"})
    List<TravelGroup> findByStatusAndMeetupTimeBeforeOrderByMeetupTime(
            TravelGroupStatus status,
            LocalDateTime now
    );

    @Query("""
        SELECT HOUR(tg.meetupTime), COUNT(tg)
        FROM TravelGroup tg
        WHERE tg.status = :status
          AND tg.meetupTime < :now
        GROUP BY HOUR(tg.meetupTime)
        ORDER BY HOUR(tg.meetupTime)
        """)
    List<Object[]> findPeakTravelTimes(
            @Param("status") TravelGroupStatus status,
            @Param("now") LocalDateTime now
    );


}
