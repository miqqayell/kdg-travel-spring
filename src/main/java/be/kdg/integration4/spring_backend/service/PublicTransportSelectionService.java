package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupRequest;
import be.kdg.integration4.spring_backend.domain.PublicTransportSelection;
import be.kdg.integration4.spring_backend.domain.TravelGroup;
import be.kdg.integration4.spring_backend.dto.transit.SuggestedRoute;
import be.kdg.integration4.spring_backend.repository.PublicTransportSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicTransportSelectionService {
    private final PublicTransportSelectionRepository publicTransportSelectionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<PublicTransportSelection> getSelectionForGroup(Long travelGroupId) {
        return publicTransportSelectionRepository.findByTravelGroupId(travelGroupId);
    }

    /**
     * Flushes pending changes so that an orphaned selection is deleted before a
     * replacement is inserted. Without this, Hibernate orders the new INSERT
     * before the orphan DELETE and the unique {@code travel_group_id} clashes.
     */
    @Transactional
    public void flushPendingChanges() {
        publicTransportSelectionRepository.flush();
    }

    /**
     * Saves the route that was suggested, so the group page can show the same choice later.
     */
    PublicTransportSelection fromSuggestedRoute(TravelGroup group, SuggestedRoute route) {
        return PublicTransportSelection.create(
                group,
                route.primaryLineLabel(),
                route.headsign(),
                route.headsign(),
                route.lineColor(),
                route.startStopId(),
                route.startStopName(),
                route.endStopId(),
                route.endStopName(),
                route.expectedDeparture(),
                route.expectedArrival(),
                LocalDateTime.now(),
                route.transferCount(),
                route.walkingMinutes(),
                route.routeSummary(),
                routeSnapshotJson(route)
        );
    }

    /**
     * Saves a selected route that was posted back with the form.
     */
    PublicTransportSelection fromRequestSnapshot(TravelGroup group, TravelGroupRequest request) {
        return PublicTransportSelection.create(
                group,
                request.getSelectedRouteLineNumber(),
                request.getSelectedRouteDirection(),
                request.getSelectedRouteDestination(),
                null,
                request.getSelectedRouteStartStopId(),
                request.getSelectedRouteStartStopName(),
                request.getSelectedRouteEndStopId(),
                request.getSelectedRouteEndStopName(),
                request.getSelectedRouteDepartureTime(),
                request.getSelectedRouteEstimatedArrivalTime(),
                LocalDateTime.now(),
                null,
                null,
                requestRouteSummary(request),
                routeSnapshotJson(request)
        );
    }

    private String requestRouteSummary(TravelGroupRequest request) {
        String line = normalize(request.getSelectedRouteLineNumber());
        String start = normalize(request.getSelectedRouteStartStopName());
        String end = normalize(request.getSelectedRouteEndStopName());
        if (line == null || start == null || end == null) {
            return null;
        }
        return "Line " + line + ": " + start + " to " + end;
    }

    private String routeSnapshotJson(SuggestedRoute route) {
        try {
            return objectMapper.writeValueAsString(route);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize public transport route snapshot", ex);
        }
    }

    private String routeSnapshotJson(TravelGroupRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize public transport route snapshot", ex);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
