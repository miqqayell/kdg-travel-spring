package be.kdg.integration4.spring_backend.dto.transit;

import java.time.LocalDateTime;
import java.util.List;

public record TransitLeg(
        TransitLegType type,
        String routeShortName,
        String routeLongName,
        Integer routeType,
        String tripId,
        String fromStopId,
        String fromStopName,
        String toStopId,
        String toStopName,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        String headsign,
        double distanceMeters,
        List<GeoPoint> geometry
) {
    public TransitLeg {
        geometry = geometry == null ? List.of() : List.copyOf(geometry);
    }

    public static TransitLeg walk(String fromStopId,
                                  String fromStopName,
                                  String toStopId,
                                  String toStopName,
                                  LocalDateTime departureTime,
                                  LocalDateTime arrivalTime,
                                  double distanceMeters,
                                  List<GeoPoint> geometry) {
        return new TransitLeg(
                TransitLegType.WALK,
                null,
                null,
                null,
                null,
                fromStopId,
                fromStopName,
                toStopId,
                toStopName,
                departureTime,
                arrivalTime,
                null,
                distanceMeters,
                geometry
        );
    }

    public static TransitLeg transit(String routeShortName,
                                     String routeLongName,
                                     Integer routeType,
                                     String tripId,
                                     String fromStopId,
                                     String fromStopName,
                                     String toStopId,
                                     String toStopName,
                                     LocalDateTime departureTime,
                                     LocalDateTime arrivalTime,
                                     String headsign,
                                     List<GeoPoint> geometry) {
        return new TransitLeg(
                TransitLegType.TRANSIT,
                routeShortName,
                routeLongName,
                routeType,
                tripId,
                fromStopId,
                fromStopName,
                toStopId,
                toStopName,
                departureTime,
                arrivalTime,
                headsign,
                0,
                geometry
        );
    }
}
