package be.kdg.integration4.spring_backend.service.transit.planning;

import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.dto.transit.SuggestedRoute;
import be.kdg.integration4.spring_backend.dto.transit.TransitLeg;
import be.kdg.integration4.spring_backend.dto.transit.TransitLegType;
import be.kdg.integration4.spring_backend.service.transit.gtfs.GtfsStaticData;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class SuggestedRouteMapper {
    private static final String START_LOCATION = "Start location";
    private static final String DESTINATION = "Destination";

    /**
     * Converts planner segments to the route format used by the frontend.
     */
    SuggestedRoute toSuggestedRoute(GtfsStaticData staticData,
                                    LocalDate serviceDate,
                                    NearbyStop origin,
                                    NearbyStop destination,
                                    List<TransitSegment> transitSegments) {
        List<TransitLeg> legs = new ArrayList<>();

        int firstDepartureSeconds = requireStopTime(
                staticData,
                transitSegments.getFirst().tripId(),
                transitSegments.getFirst().boardSequence()
        ).getDepartureTime();
        int walkingSeconds = addOriginWalk(
                legs,
                staticData.stopId(origin.stop()),
                staticData.stopName(origin.stop()),
                dateTime(serviceDate, firstDepartureSeconds),
                origin.distanceMeters(),
                origin.walkingSeconds(),
                origin.geometry()
        );

        // Add the legs in the same order the user will travel them.
        for (int index = 0; index < transitSegments.size(); index++) {
            TransitSegment segment = transitSegments.get(index);
            legs.add(toTransitLeg(staticData, serviceDate, segment));
            if (index < transitSegments.size() - 1) {
                walkingSeconds += addTransferWalkBetweenSegments(staticData, serviceDate, legs, segment, transitSegments.get(index + 1));
            }
        }

        int finalTransitArrivalSeconds = requireStopTime(
                staticData,
                transitSegments.getLast().tripId(),
                transitSegments.getLast().alightSequence()
        ).getArrivalTime();
        LocalDateTime finalTransitArrivalTime = dateTime(serviceDate, finalTransitArrivalSeconds);
        LocalDateTime destinationArrival = finalTransitArrivalTime.plusSeconds(destination.walkingSeconds());
        walkingSeconds += addDestinationWalk(
                legs,
                staticData.stopId(destination.stop()),
                staticData.stopName(destination.stop()),
                finalTransitArrivalTime,
                destination.distanceMeters(),
                destination.walkingSeconds(),
                reversed(destination.geometry())
        );

        TransitLeg firstTransitLeg = legs.stream()
                .filter(leg -> leg.type() == TransitLegType.TRANSIT)
                .findFirst()
                .orElseThrow();
        TransitLeg lastTransitLeg = legs.stream()
                .filter(leg -> leg.type() == TransitLegType.TRANSIT)
                .reduce((ignored, current) -> current)
                .orElseThrow();

        return new SuggestedRoute(
                staticData.stopId(origin.stop()),
                staticData.stopName(origin.stop()),
                new GeoPoint(origin.stop().getLat(), origin.stop().getLon()),
                staticData.stopId(destination.stop()),
                staticData.stopName(destination.stop()),
                new GeoPoint(destination.stop().getLat(), destination.stop().getLon()),
                routeLabel(firstTransitLeg),
                routeSummary(legs),
                lastTransitLeg.headsign(),
                firstTransitLeg.departureTime(),
                destinationArrival,
                staticData.routeColor(firstRoute(staticData, transitSegments)),
                transitSegments.size() - 1,
                TransitWalkingTime.minutesForSeconds(walkingSeconds),
                legs
        );
    }

    private TransitLeg toTransitLeg(GtfsStaticData staticData,
                                    LocalDate serviceDate,
                                    TransitSegment segment) {
        StopTime board = requireStopTime(staticData, segment.tripId(), segment.boardSequence());
        StopTime alight = requireStopTime(staticData, segment.tripId(), segment.alightSequence());
        Trip trip = staticData.trip(segment.tripId());
        Route route = staticData.routeForTrip(trip);
        String boardStopId = staticData.stopId(board);
        String alightStopId = staticData.stopId(alight);

        return TransitLeg.transit(
                staticData.routeShortName(route),
                staticData.routeLongName(route),
                route == null ? null : route.getType(),
                staticData.tripId(trip),
                boardStopId,
                staticData.stopName(staticData.stop(boardStopId)),
                alightStopId,
                staticData.stopName(staticData.stop(alightStopId)),
                dateTime(serviceDate, board.getDepartureTime()),
                dateTime(serviceDate, alight.getArrivalTime()),
                trip.getTripHeadsign(),
                transitGeometry(staticData, board, alight)
        );
    }

    private int addTransferWalkBetweenSegments(GtfsStaticData staticData,
                                               LocalDate serviceDate,
                                               List<TransitLeg> legs,
                                               TransitSegment segment,
                                               TransitSegment nextSegment) {
        if (segment.transferWalkMeters() <= 0) {
            return 0;
        }

        StopTime alight = requireStopTime(staticData, segment.tripId(), segment.alightSequence());
        StopTime nextBoard = requireStopTime(staticData, nextSegment.tripId(), nextSegment.boardSequence());
        String alightStopId = staticData.stopId(alight);
        String nextBoardStopId = staticData.stopId(nextBoard);

        return addTransferWalk(
                legs,
                alightStopId,
                staticData.stopName(staticData.stop(alightStopId)),
                nextBoardStopId,
                staticData.stopName(staticData.stop(nextBoardStopId)),
                dateTime(serviceDate, alight.getArrivalTime()),
                segment.transferWalkMeters(),
                segment.transferWalkingSeconds(),
                segment.transferWalkGeometry()
        );
    }

    private int addOriginWalk(List<TransitLeg> legs,
                              String stopId,
                              String stopName,
                              LocalDateTime arrivalTime,
                              double distanceMeters,
                              int walkingSeconds,
                              List<GeoPoint> geometry) {
        return addWalk(
                legs,
                null,
                START_LOCATION,
                stopId,
                stopName,
                arrivalTime.minusSeconds(walkingSeconds),
                arrivalTime,
                distanceMeters,
                walkingSeconds,
                geometry
        );
    }

    private int addDestinationWalk(List<TransitLeg> legs,
                                   String stopId,
                                   String stopName,
                                   LocalDateTime departureTime,
                                   double distanceMeters,
                                   int walkingSeconds,
                                   List<GeoPoint> geometry) {
        return addWalk(
                legs,
                stopId,
                stopName,
                null,
                DESTINATION,
                departureTime,
                departureTime.plusSeconds(walkingSeconds),
                distanceMeters,
                walkingSeconds,
                geometry
        );
    }

    private int addTransferWalk(List<TransitLeg> legs,
                                String fromStopId,
                                String fromStopName,
                                String toStopId,
                                String toStopName,
                                LocalDateTime departureTime,
                                double distanceMeters,
                                int walkingSeconds,
                                List<GeoPoint> geometry) {
        return addWalk(
                legs,
                fromStopId,
                fromStopName,
                toStopId,
                toStopName,
                departureTime,
                departureTime.plusSeconds(walkingSeconds),
                distanceMeters,
                walkingSeconds,
                geometry
        );
    }

    private int addWalk(List<TransitLeg> legs,
                        String fromStopId,
                        String fromStopName,
                        String toStopId,
                        String toStopName,
                        LocalDateTime departureTime,
                        LocalDateTime arrivalTime,
                        double distanceMeters,
                        int walkingSeconds,
                        List<GeoPoint> geometry) {
        if (walkingSeconds <= 0) {
            return 0;
        }
        legs.add(TransitLeg.walk(
                fromStopId,
                fromStopName,
                toStopId,
                toStopName,
                departureTime,
                arrivalTime,
                distanceMeters,
                geometry
        ));
        return walkingSeconds;
    }

    private StopTime requireStopTime(GtfsStaticData staticData, String tripId, int sequence) {
        StopTime stopTime = staticData.stopTimeAtSequence(tripId, sequence);
        if (stopTime == null) {
            throw new IllegalStateException("Missing stop time for trip " + tripId + " at sequence " + sequence);
        }
        return stopTime;
    }

    private List<GeoPoint> transitGeometry(GtfsStaticData staticData,
                                           StopTime board,
                                           StopTime alight) {
        return stopGeometry(staticData, staticData.stopId(board), staticData.stopId(alight));
    }

    private List<GeoPoint> stopGeometry(GtfsStaticData staticData, String fromStopId, String toStopId) {
        Stop fromStop = staticData.stop(fromStopId);
        Stop toStop = staticData.stop(toStopId);
        if (fromStop == null || toStop == null) {
            return List.of();
        }
        return List.of(
                new GeoPoint(fromStop.getLat(), fromStop.getLon()),
                new GeoPoint(toStop.getLat(), toStop.getLon())
        );
    }

    private List<GeoPoint> reversed(List<GeoPoint> geometry) {
        List<GeoPoint> reversed = new ArrayList<>(geometry);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private Route firstRoute(GtfsStaticData staticData, List<TransitSegment> transitSegments) {
        return staticData.routeForTrip(transitSegments.getFirst().tripId());
    }

    private String routeLabel(TransitLeg leg) {
        return leg.routeShortName() == null || leg.routeShortName().isBlank() ? "-" : leg.routeShortName();
    }

    private String routeSummary(List<TransitLeg> legs) {
        return legs.stream()
                .filter(leg -> leg.type() == TransitLegType.TRANSIT)
                .map(this::routeLabel)
                .distinct()
                .reduce((left, right) -> left + " -> " + right)
                .orElse("Public transport");
    }

    private LocalDateTime dateTime(LocalDate serviceDate, int seconds) {
        // GTFS can use times after 24:00 for trips that continue after midnight.
        return serviceDate.atStartOfDay().plusSeconds(seconds);
    }
}
