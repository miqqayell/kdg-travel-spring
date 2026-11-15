package be.kdg.integration4.spring_backend.controller.api;

import be.kdg.integration4.spring_backend.controller.api.dto.LocationUpdateRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.UserLocationDto;
import be.kdg.integration4.spring_backend.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationRestController {

    private final LocationService locationService;

    // 1. Toggle Sharing On/Off
    @PostMapping("/toggle")
    public ResponseEntity<Boolean> toggleSharing(@AuthenticationPrincipal OidcUser oidcUser) {
        boolean isNowEnabled = locationService.toggleLocationSharing(oidcUser.getSubject());
        return ResponseEntity.ok(isNowEnabled);
    }

    // 2. Update Live GPS Coordinates
    @PostMapping("/update")
    public ResponseEntity<Void> updateLocation(@RequestBody LocationUpdateRequest request,
                                               @AuthenticationPrincipal OidcUser oidcUser) {
        locationService.updateUserLocation(oidcUser.getSubject(), request.latitude(), request.longitude());
        return ResponseEntity.ok().build();
    }

    // 3. Fetch Live Locations of Group Members
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<UserLocationDto>> getGroupLocations(@PathVariable Long groupId) {
        List<UserLocationDto> locations = locationService.getActiveGroupLocations(groupId);
        return ResponseEntity.ok(locations);
    }
}