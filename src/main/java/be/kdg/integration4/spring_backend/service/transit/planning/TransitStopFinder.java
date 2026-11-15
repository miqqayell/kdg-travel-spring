package be.kdg.integration4.spring_backend.service.transit.planning;

import be.kdg.integration4.spring_backend.config.DeLijnApiProperties;
import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.service.transit.TransitRouteQuery;
import be.kdg.integration4.spring_backend.service.transit.gtfs.GtfsStaticData;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TransitStopFinder {
    private static final int MAX_ORIGIN_STOPS = 24;
    private static final int MAX_DESTINATION_STOPS = 24;
    private static final int PREFILTER_MULTIPLIER = 3;

    private final DeLijnApiProperties deLijnApiProperties;

    public TransitStopFinder(DeLijnApiProperties deLijnApiProperties) {
        this.deLijnApiProperties = deLijnApiProperties;
    }

    /**
     * Uses the configured walking radius for the first walk to a stop.
     */
    List<NearbyStop> originStops(GtfsStaticData staticData, TransitRouteQuery query) {
        return nearbyStops(
                staticData,
                query.startPoint(),
                deLijnApiProperties.maxWalkingDistanceMeters(),
                MAX_ORIGIN_STOPS
        );
    }

    /**
     * Uses the configured walking radius for the final walk from a stop.
     */
    List<NearbyStop> destinationStops(GtfsStaticData staticData, TransitRouteQuery query) {
        return nearbyStops(
                staticData,
                query.endPoint(),
                deLijnApiProperties.maxWalkingDistanceMeters(),
                MAX_DESTINATION_STOPS
        );
    }

    private List<NearbyStop> nearbyStops(GtfsStaticData staticData, GeoPoint point, int radiusMeters, int limit) {
        // Fetch extra stops first because walking time can change the closest-stop order.
        return staticData.nearbyStops(point.latitude(), point.longitude(), radiusMeters, limit * PREFILTER_MULTIPLIER)
                .stream()
                .map(stop -> new NearbyStop(
                        stop.stop(),
                        stop.distanceMeters(),
                        TransitWalkingTime.secondsForMeters(stop.distanceMeters()),
                        List.of(point, new GeoPoint(stop.stop().getLat(), stop.stop().getLon()))
                ))
                .sorted(Comparator.comparingInt(NearbyStop::walkingSeconds))
                .limit(limit)
                .toList();
    }
}
