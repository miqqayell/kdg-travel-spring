package be.kdg.integration4.spring_backend.service.transit.planning;

import be.kdg.integration4.spring_backend.dto.transit.SuggestedRoute;
import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.service.transit.TransitRouteQuery;
import be.kdg.integration4.spring_backend.service.transit.gtfs.GtfsStaticData;
import be.kdg.integration4.spring_backend.service.transit.gtfs.GtfsStaticData.StopDeparture;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plans routes from the static GTFS timetable.
 */
@Service
public class StaticGtfsRoutePlanner {
    private static final int MAX_ORIGIN_DEPARTURES_PER_STOP = 10;
    private static final int MAX_TRANSFER_DEPARTURES_PER_STOP = 6;
    private static final int MAX_FIRST_LEG_TRANSFER_STOPS = 60;
    private static final int MAX_TRANSFER_WALK_METERS = 300;
    private static final int MAX_TRANSFER_WALK_STOPS = 8;
    private static final int MIN_TRANSFER_SECONDS = 180;
    private static final int MAX_SEARCH_SECONDS = 3 * 60 * 60;
    private static final int MAX_SUGGESTED_ROUTES = 3;
    private static final int TRANSFER_PENALTY_SECONDS = 10 * 60;
    private static final int WALKING_PENALTY_PER_MINUTE_SECONDS = 30;

    private final SuggestedRouteMapper suggestedRouteMapper;
    private final TransitStopFinder transitStopFinder;

    public StaticGtfsRoutePlanner(SuggestedRouteMapper suggestedRouteMapper,
                                  TransitStopFinder transitStopFinder) {
        this.suggestedRouteMapper = suggestedRouteMapper;
        this.transitStopFinder = transitStopFinder;
    }

    Optional<SuggestedRoute> suggestTransitRoute(TransitRouteQuery query, GtfsStaticData staticData) {
        return suggestTransitRoutes(query, staticData).stream().findFirst();
    }

    /**
     * Searches direct and one-transfer routes within the planner's time window.
     */
    public List<SuggestedRoute> suggestTransitRoutes(TransitRouteQuery query, GtfsStaticData staticData) {
        LocalDate serviceDate = query.meetupTime().toLocalDate();
        Set<String> activeServiceIds = staticData.activeServiceIds(serviceDate);
        if (activeServiceIds.isEmpty()) {
            return List.of();
        }

        List<NearbyStop> originStops = transitStopFinder.originStops(staticData, query);
        List<NearbyStop> destinationStops = transitStopFinder.destinationStops(staticData, query);
        if (originStops.isEmpty() || destinationStops.isEmpty()) {
            return List.of();
        }

        int targetSeconds = secondsSinceServiceDate(query.meetupTime(), serviceDate);
        int latestArrivalSeconds = targetSeconds + MAX_SEARCH_SECONDS;

        // The group time is treated as the time when the user wants to leave.
        return findBestCandidates(staticData, activeServiceIds, originStops, destinationStops, targetSeconds, latestArrivalSeconds)
                .stream()
                .map(candidate -> suggestedRouteMapper.toSuggestedRoute(
                        staticData,
                        serviceDate,
                        candidate.origin(),
                        candidate.destination(),
                        candidate.segments()
                ))
                .toList();
    }

    private List<RouteCandidate> findBestCandidates(GtfsStaticData staticData,
                                                    Set<String> activeServiceIds,
                                                    List<NearbyStop> originStops,
                                                    List<NearbyStop> destinationStops,
                                                    int targetSeconds,
                                                    int latestArrivalSeconds) {
        Map<String, NearbyStop> destinationStopsById = destinationStopsById(staticData, destinationStops);
        List<RouteCandidate> candidates = new ArrayList<>();

        // Limit the stops and departures so the search stays fast enough for a web request.
        for (NearbyStop origin : originStops) {
            int readySeconds = targetSeconds + origin.walkingSeconds();
            for (StopDeparture departure : activeDeparturesInWindow(
                    staticData,
                    activeServiceIds,
                    staticData.stopId(origin.stop()),
                    readySeconds,
                    latestArrivalSeconds,
                    MAX_ORIGIN_DEPARTURES_PER_STOP
            )) {
                addDestinationCandidates(
                        staticData,
                        destinationStopsById,
                        candidates,
                        origin,
                        List.of(),
                        departure,
                        latestArrivalSeconds
                );
                addOneTransferCandidates(staticData, activeServiceIds, destinationStopsById, candidates, origin, departure, latestArrivalSeconds);
            }
        }

        Set<String> lineKeys = new LinkedHashSet<>();
        // Avoid showing the same line combination multiple times.
        return candidates.stream()
                .sorted(candidateComparator())
                .filter(candidate -> lineKeys.add(lineKey(staticData, candidate)))
                .limit(MAX_SUGGESTED_ROUTES)
                .toList();
    }

    private Comparator<RouteCandidate> candidateComparator() {
        // The score prefers early arrival. The tie-breakers prefer simpler routes.
        return Comparator.comparingInt(RouteCandidate::score)
                .thenComparingInt(RouteCandidate::finalArrivalSeconds)
                .thenComparingInt(candidate -> candidate.segments().size() - 1)
                .thenComparingInt(RouteCandidate::totalWalkingSeconds)
                .thenComparingInt(RouteCandidate::firstDepartureSeconds);
    }

    private String lineKey(GtfsStaticData staticData, RouteCandidate candidate) {
        return candidate.segments().stream()
                .map(segment -> lineLabel(staticData, segment.tripId()))
                .distinct()
                .collect(Collectors.joining(" -> "));
    }

    private String lineLabel(GtfsStaticData staticData, String tripId) {
        Trip trip = staticData.trip(tripId);
        if (trip == null) {
            return tripId;
        }
        var route = staticData.routeForTrip(trip);
        String shortName = staticData.routeShortName(route);
        if (route == null || shortName.isBlank()) {
            return staticData.tripRouteId(trip);
        }
        return shortName;
    }

    private List<StopDeparture> activeDeparturesInWindow(GtfsStaticData staticData,
                                                         Set<String> activeServiceIds,
                                                         String stopId,
                                                         int earliestSeconds,
                                                         int latestSeconds,
                                                         int limit) {
        return staticData.departuresInWindow(stopId, earliestSeconds, latestSeconds)
                .stream()
                .filter(departure -> isActiveDeparture(staticData, activeServiceIds, departure))
                .limit(limit)
                .toList();
    }

    private void addDestinationCandidates(GtfsStaticData staticData,
                                          Map<String, NearbyStop> destinationStopsById,
                                          List<RouteCandidate> candidates,
                                          NearbyStop origin,
                                          List<TransitSegment> prefixSegments,
                                          StopDeparture departure,
                                          int latestArrivalSeconds) {
        for (StopTime alight : downstreamStops(
                staticData.stopTimesForTrip(departure.tripId()),
                departure.sequence(),
                latestArrivalSeconds
        )) {
            NearbyStop destination = destinationStopsById.get(staticData.stopId(alight));
            if (destination == null) {
                continue;
            }
            TransitSegment segment = new TransitSegment(departure.tripId(), departure.sequence(), alight.getStopSequence());
            addCandidate(staticData, candidates, origin, destination, appended(prefixSegments, segment), latestArrivalSeconds);
        }
    }

    private void addOneTransferCandidates(GtfsStaticData staticData,
                                          Set<String> activeServiceIds,
                                          Map<String, NearbyStop> destinationStopsById,
                                          List<RouteCandidate> candidates,
                                          NearbyStop origin,
                                          StopDeparture departure,
                                          int latestArrivalSeconds) {
        // Only one transfer is checked to keep the search small enough for this project.
        for (StopTime transferAlight : downstreamStops(
                staticData.stopTimesForTrip(departure.tripId()),
                departure.sequence(),
                latestArrivalSeconds
        ).stream().limit(MAX_FIRST_LEG_TRANSFER_STOPS).toList()) {
            for (NearbyStop transferBoardStop : nearbyTransferStops(staticData, transferAlight)) {
                int secondLegReadySeconds = transferAlight.getArrivalTime()
                        + MIN_TRANSFER_SECONDS
                        + transferBoardStop.walkingSeconds();
                if (secondLegReadySeconds > latestArrivalSeconds) {
                    continue;
                }

                for (StopDeparture secondDeparture : activeDeparturesInWindow(
                        staticData,
                        activeServiceIds,
                        staticData.stopId(transferBoardStop.stop()),
                        secondLegReadySeconds,
                        latestArrivalSeconds,
                        MAX_TRANSFER_DEPARTURES_PER_STOP
                )) {
                    if (secondDeparture.tripId().equals(departure.tripId())) {
                        continue;
                    }
                    addDestinationCandidates(
                            staticData,
                            destinationStopsById,
                            candidates,
                            origin,
                            List.of(new TransitSegment(
                                    departure.tripId(),
                                    departure.sequence(),
                                    transferAlight.getStopSequence(),
                                    transferBoardStop
                            )),
                            secondDeparture,
                            latestArrivalSeconds
                    );
                }
            }
        }
    }

    private List<NearbyStop> nearbyTransferStops(GtfsStaticData staticData, StopTime transferAlight) {
        var alightStop = staticData.stopForStopTime(transferAlight);
        if (alightStop == null) {
            return List.of();
        }
        GeoPoint alightPoint = new GeoPoint(alightStop.getLat(), alightStop.getLon());
        // A transfer can use a nearby stop because stations often have multiple platforms.
        return staticData.nearbyStops(
                        alightStop.getLat(),
                        alightStop.getLon(),
                        MAX_TRANSFER_WALK_METERS,
                        MAX_TRANSFER_WALK_STOPS * 2
                )
                .stream()
                .map(stop -> new NearbyStop(
                        stop.stop(),
                        stop.distanceMeters(),
                        TransitWalkingTime.secondsForMeters(stop.distanceMeters()),
                        List.of(alightPoint, new GeoPoint(stop.stop().getLat(), stop.stop().getLon()))
                ))
                .sorted(Comparator.comparingInt(NearbyStop::walkingSeconds))
                .limit(MAX_TRANSFER_WALK_STOPS)
                .toList();
    }

    private List<TransitSegment> appended(List<TransitSegment> prefixSegments, TransitSegment segment) {
        if (prefixSegments.isEmpty()) {
            return List.of(segment);
        }
        return List.of(prefixSegments.getFirst(), segment);
    }

    private void addCandidate(GtfsStaticData staticData,
                              List<RouteCandidate> candidates,
                              NearbyStop origin,
                              NearbyStop destination,
                              List<TransitSegment> segments,
                              int latestArrivalSeconds) {
        int firstDepartureSeconds = staticData.stopTimeAtSequence(
                segments.getFirst().tripId(),
                segments.getFirst().boardSequence()
        ).getDepartureTime();
        int finalTransitArrivalSeconds = staticData.stopTimeAtSequence(
                segments.getLast().tripId(),
                segments.getLast().alightSequence()
        ).getArrivalTime();
        int finalArrivalSeconds = finalTransitArrivalSeconds + destination.walkingSeconds();
        if (finalArrivalSeconds > latestArrivalSeconds) {
            return;
        }
        int totalWalkingSeconds = origin.walkingSeconds()
                + destination.walkingSeconds()
                + segments.stream().mapToInt(TransitSegment::transferWalkingSeconds).sum();
        int transferCount = segments.size() - 1;
        int walkingMinutes = TransitWalkingTime.minutesForSeconds(totalWalkingSeconds);
        // Add transfer and walking penalties so awkward routes do not win only because they arrive earlier.
        int score = finalArrivalSeconds
                + transferCount * TRANSFER_PENALTY_SECONDS
                + walkingMinutes * WALKING_PENALTY_PER_MINUTE_SECONDS;

        candidates.add(new RouteCandidate(
                origin,
                destination,
                segments,
                finalArrivalSeconds,
                totalWalkingSeconds,
                firstDepartureSeconds,
                score
        ));
    }

    private boolean isActiveDeparture(GtfsStaticData staticData,
                                      Set<String> activeServiceIds,
                                      StopDeparture departure) {
        Trip trip = staticData.trip(departure.tripId());
        return trip != null && activeServiceIds.contains(staticData.tripServiceId(trip));
    }

    private List<StopTime> downstreamStops(List<StopTime> tripStopTimes, int sequence, int latestArrivalSeconds) {
        return tripStopTimes.stream()
                .filter(stopTime -> stopTime.getStopSequence() > sequence && stopTime.getArrivalTime() <= latestArrivalSeconds)
                .toList();
    }

    private Map<String, NearbyStop> destinationStopsById(GtfsStaticData staticData, List<NearbyStop> destinationStops) {
        return destinationStops.stream()
                .collect(java.util.stream.Collectors.toMap(
                        stop -> staticData.stopId(stop.stop()),
                        stop -> stop,
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
    }

    private int secondsSinceServiceDate(LocalDateTime dateTime, LocalDate serviceDate) {
        return (int) ChronoUnit.SECONDS.between(serviceDate.atStartOfDay(), dateTime);
    }

    private record RouteCandidate(
            NearbyStop origin,
            NearbyStop destination,
            List<TransitSegment> segments,
            int finalArrivalSeconds,
            int totalWalkingSeconds,
            int firstDepartureSeconds,
            int score
    ) {
    }
}
