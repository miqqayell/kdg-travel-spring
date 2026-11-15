package be.kdg.integration4.spring_backend.controller.api;

import be.kdg.integration4.spring_backend.controller.api.dto.TransitRouteSuggestionRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupResponse;
import be.kdg.integration4.spring_backend.controller.api.dto.WalkingDistanceRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.WalkingDistanceResponse;
import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.exception.PedestrianRoutingException;
import be.kdg.integration4.spring_backend.service.routing.PedestrianRoutingService;
import be.kdg.integration4.spring_backend.service.transit.TransitRouteSuggestionService;
import be.kdg.integration4.spring_backend.service.transit.TransitRouteQuery;
import be.kdg.integration4.spring_backend.service.transit.TransitSuggestionResult;
import be.kdg.integration4.spring_backend.service.TravelGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TravelGroupRestController {

    private final TravelGroupService travelGroupService;
    private final TransitRouteSuggestionService transitRouteSuggestionService;
    private final PedestrianRoutingService pedestrianRoutingService;

    @GetMapping({"/activities/{activityId}/groups", "/groups/activity/{activityId}"})
    public ResponseEntity<List<TravelGroupResponse>> getGroupsForActivity(@PathVariable Long activityId) {
        return ResponseEntity.ok(travelGroupService.getGroupsForActivity(activityId));
    }

    @GetMapping({"/groups/mine", "/users/me/groups"})
    public ResponseEntity<List<TravelGroupResponse>> getMyGroups(@AuthenticationPrincipal OidcUser oidcUser) {
        return ResponseEntity.ok(travelGroupService.getMyGroups(oidcUser.getSubject()));
    }

    @GetMapping({"/groups/mine/{id}", "/users/me/groups/{id}"})
    public ResponseEntity<TravelGroupResponse> getMyGroupById(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return ResponseEntity.ok(travelGroupService.getMyGroupById(id, oidcUser.getSubject()));
    }

    @PostMapping({"/groups", "/users/me/groups"})
    public ResponseEntity<TravelGroupResponse> createGroup(
            @Valid @RequestBody TravelGroupRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        TravelGroupResponse createdGroup = travelGroupService.createGroup(request, oidcUser.getSubject());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/groups/mine/{id}")
                .buildAndExpand(createdGroup.id())
                .toUri();

        return ResponseEntity.created(location).body(createdGroup);
    }

    @PostMapping("/groups/suggestions")
    public ResponseEntity<TransitSuggestionResult> suggestRoute(@Valid @RequestBody TransitRouteSuggestionRequest request) {
        TransitRouteQuery query = TransitRouteQuery.fromSuggestionRequest(request);
        TransitSuggestionResult result = transitRouteSuggestionService.suggestTransitRouteWithMetadata(query);
        return ResponseEntity.ok()
                .header("X-Transit-Suggestions-Status", result.status().name())
                .body(result);
    }

    @PostMapping("/groups/walking-distance")
    public ResponseEntity<WalkingDistanceResponse> walkingDistance(@Valid @RequestBody WalkingDistanceRequest request) {
        GeoPoint start = new GeoPoint(request.getStartPointLatitude(), request.getStartPointLongitude());
        GeoPoint end = new GeoPoint(request.getEndPointLatitude(), request.getEndPointLongitude());
        try {
            return pedestrianRoutingService.route(start, end)
                    .map(route -> ResponseEntity.ok(new WalkingDistanceResponse(
                            route.distanceMeters(),
                            route.distanceMeters() / 1000.0,
                            route.durationSeconds()
                    )))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
        } catch (PedestrianRoutingException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @PutMapping({"/groups/{id}", "/users/me/groups/{id}"})
    public ResponseEntity<TravelGroupResponse> updateGroup(
            @PathVariable Long id,
            @Valid @RequestBody TravelGroupRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return ResponseEntity.ok(travelGroupService.updateGroup(id, request, oidcUser.getSubject()));
    }

    @DeleteMapping({"/groups/{id}", "/users/me/groups/{id}"})
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        travelGroupService.softDeleteGroup(id, oidcUser.getSubject());
        return ResponseEntity.noContent().build();
    }
}
