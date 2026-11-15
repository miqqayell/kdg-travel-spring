package be.kdg.integration4.spring_backend.service.routing;

import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;

import java.util.List;
import java.util.Optional;

public interface PedestrianRoutingService {
    /**
     * Calculates a walking route between two coordinates when the provider can find one.
     */
    Optional<WalkingRoute> route(GeoPoint from, GeoPoint to);

    record WalkingRoute(double distanceMeters, int durationSeconds, List<GeoPoint> geometry) {
        public WalkingRoute {
            geometry = geometry == null ? List.of() : List.copyOf(geometry);
        }

        public WalkingRoute(double distanceMeters, int durationSeconds) {
            this(distanceMeters, durationSeconds, List.of());
        }
    }
}
