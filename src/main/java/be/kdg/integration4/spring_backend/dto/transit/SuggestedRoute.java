package be.kdg.integration4.spring_backend.dto.transit;

import java.time.LocalDateTime;
import java.util.List;

public record SuggestedRoute(
        String startStopId,
        String startStopName,
        GeoPoint startGeoCoordinate,
        String endStopId,
        String endStopName,
        GeoPoint endGeoCoordinate,
        String primaryLineLabel,
        String routeSummary,
        String headsign,
        LocalDateTime expectedDeparture,
        LocalDateTime expectedArrival,
        String lineColor,
        int transferCount,
        int walkingMinutes,
        List<TransitLeg> legs
) {
    public SuggestedRoute {
        legs = legs == null ? List.of() : List.copyOf(legs);
    }
}
