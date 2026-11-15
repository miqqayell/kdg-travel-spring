package be.kdg.integration4.spring_backend.service.transit.planning;

import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.service.routing.PedestrianRoutingService.WalkingRoute;
import org.onebusaway.gtfs.model.Stop;

import java.util.List;

/**
 * Stop with the walking data needed to reach it.
 */
record NearbyStop(Stop stop, double distanceMeters, int walkingSeconds, List<GeoPoint> geometry) {
    NearbyStop {
        geometry = geometry == null ? List.of() : List.copyOf(geometry);
    }

    NearbyStop(Stop stop, double distanceMeters, int walkingSeconds) {
        this(stop, distanceMeters, walkingSeconds, List.of());
    }

    NearbyStop(Stop stop, double distanceMeters) {
        this(stop, distanceMeters, TransitWalkingTime.secondsForMeters(distanceMeters), List.of());
    }
}

/**
 * One in-vehicle part of a suggested route.
 */
record TransitSegment(
        String tripId,
        int boardSequence,
        int alightSequence,
        double transferWalkMeters,
        int transferWalkingSeconds,
        List<GeoPoint> transferWalkGeometry
) {
    TransitSegment {
        transferWalkGeometry = transferWalkGeometry == null ? List.of() : List.copyOf(transferWalkGeometry);
    }

    TransitSegment(String tripId, int boardSequence, int alightSequence) {
        this(tripId, boardSequence, alightSequence, 0, 0, List.of());
    }

    TransitSegment(String tripId, int boardSequence, int alightSequence, WalkingRoute transferWalk) {
        this(tripId, boardSequence, alightSequence, transferWalk.distanceMeters(), transferWalk.durationSeconds(), transferWalk.geometry());
    }

    TransitSegment(String tripId, int boardSequence, int alightSequence, NearbyStop transferWalk) {
        this(tripId, boardSequence, alightSequence, transferWalk.distanceMeters(), transferWalk.walkingSeconds(), transferWalk.geometry());
    }
}

/**
 * Shared walking-time estimates used when no routed walking duration is available.
 */
final class TransitWalkingTime {
    private static final int WALKING_METERS_PER_MINUTE = 80;

    private TransitWalkingTime() {
    }

    static int secondsForMeters(double meters) {
        return (int) Math.ceil(meters / WALKING_METERS_PER_MINUTE * 60.0);
    }

    static int roundedSecondsForMeters(double meters) {
        return (int) Math.round(meters / WALKING_METERS_PER_MINUTE * 60.0);
    }

    static int minutesForSeconds(int seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }
}
