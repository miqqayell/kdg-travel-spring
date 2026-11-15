package be.kdg.integration4.spring_backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "public_transport_selections")
@org.hibernate.annotations.Check(constraints = """
        (transfer_count is null or transfer_count >= 0)
        and (walking_minutes is null or walking_minutes >= 0)
        """)
@Getter
@NoArgsConstructor
public class PublicTransportSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_id", nullable = false)
    private String lineId;

    private String direction;

    @Column(name = "destination")
    private String destination;

    @Column(name = "line_color")
    private String lineColor;

    @Column(name = "start_stop_id", nullable = false)
    private String startStopId;

    @Column(name = "start_stop_name")
    private String startStopName;

    @Column(name = "end_stop_id", nullable = false)
    private String endStopId;

    @Column(name = "end_stop_name")
    private String endStopName;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "estimated_arrival_time", nullable = false)
    private LocalDateTime estimatedArrivalTime;

    @Column(name = "fetched_realtime")
    private LocalDateTime fetchedRealtime;

    @Column(name = "transfer_count")
    private Integer transferCount;

    @Column(name = "walking_minutes")
    private Integer walkingMinutes;

    @Column(name = "route_summary")
    private String routeSummary;

    @Lob
    @Column(name = "route_snapshot_json")
    private String routeSnapshotJson;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "travel_group_id", nullable = false, unique = true)
    private TravelGroup travelGroup;

    public static PublicTransportSelection create(
            TravelGroup travelGroup,
            String lineId,
            String direction,
            String destination,
            String lineColor,
            String startStopId,
            String startStopName,
            String endStopId,
            String endStopName,
            LocalDateTime departureTime,
            LocalDateTime estimatedArrivalTime,
            LocalDateTime fetchedRealtime,
            Integer transferCount,
            Integer walkingMinutes,
            String routeSummary
    ) {
        return create(
                travelGroup,
                lineId,
                direction,
                destination,
                lineColor,
                startStopId,
                startStopName,
                endStopId,
                endStopName,
                departureTime,
                estimatedArrivalTime,
                fetchedRealtime,
                transferCount,
                walkingMinutes,
                routeSummary,
                null
        );
    }

    public static PublicTransportSelection create(
            TravelGroup travelGroup,
            String lineId,
            String direction,
            String destination,
            String lineColor,
            String startStopId,
            String startStopName,
            String endStopId,
            String endStopName,
            LocalDateTime departureTime,
            LocalDateTime estimatedArrivalTime,
            LocalDateTime fetchedRealtime,
            Integer transferCount,
            Integer walkingMinutes,
            String routeSummary,
            String routeSnapshotJson
    ) {
        PublicTransportSelection selection = new PublicTransportSelection();
        selection.travelGroup = requireNonNull(
                travelGroup,
                "Travel group is required for suggested public transport route"
        );
        selection.lineId = requireText(lineId, "Suggested route line is required");
        selection.direction = normalize(direction);
        selection.destination = normalize(destination);
        selection.lineColor = normalize(lineColor);
        selection.startStopId = requireText(startStopId, "Suggested route start stop is required");
        selection.startStopName = normalize(startStopName);
        selection.endStopId = requireText(endStopId, "Suggested route end stop is required");
        selection.endStopName = normalize(endStopName);
        if (departureTime == null || estimatedArrivalTime == null) {
            throw new IllegalArgumentException("Suggested route timing is required");
        }
        validateMetrics(transferCount, walkingMinutes);
        selection.departureTime = departureTime;
        selection.estimatedArrivalTime = estimatedArrivalTime;
        selection.fetchedRealtime = fetchedRealtime;
        selection.transferCount = transferCount;
        selection.walkingMinutes = walkingMinutes;
        selection.routeSummary = normalize(routeSummary);
        selection.routeSnapshotJson = normalize(routeSnapshotJson);
        return selection;
    }

    void attachToTravelGroup(TravelGroup travelGroup) {
        this.travelGroup = travelGroup;
    }

    void detachFromTravelGroup() {
        this.travelGroup = null;
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateMetrics(Integer transferCount, Integer walkingMinutes) {
        requireNonNegative(transferCount, "Suggested route transfer count must be non-negative");
        requireNonNegative(walkingMinutes, "Suggested route walking minutes must be non-negative");
    }

    private static void requireNonNegative(Integer value, String message) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
