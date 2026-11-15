package be.kdg.integration4.spring_backend.controller.api;

import be.kdg.integration4.spring_backend.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendRestController {

    private final FriendService friendService;

    @PostMapping("/request/{recipientId}")
    public ResponseEntity<String> sendRequest(@PathVariable Long recipientId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.sendFriendRequest(oidcUser.getSubject(), recipientId);
        return ResponseEntity.ok("Request sent");
    }

    @PostMapping("/accept/{requestId}")
    public ResponseEntity<String> acceptRequest(@PathVariable Long requestId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.acceptFriendRequest(oidcUser.getSubject(), requestId);
        return ResponseEntity.ok("Request accepted");
    }

    @PostMapping("/decline/{requestId}")
    public ResponseEntity<String> declineRequest(@PathVariable Long requestId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.declineFriendRequest(oidcUser.getSubject(), requestId);
        return ResponseEntity.ok("Request declined");
    }

    @PostMapping("/remove/{targetUserId}")
    public ResponseEntity<String> removeFriend(@PathVariable Long targetUserId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.manageRelation(oidcUser.getSubject(), targetUserId, "REMOVE");
        return ResponseEntity.ok("Friend removed");
    }

    @PostMapping("/block/{targetUserId}")
    public ResponseEntity<String> blockUser(@PathVariable Long targetUserId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.manageRelation(oidcUser.getSubject(), targetUserId, "BLOCK");
        return ResponseEntity.ok("User blocked");
    }

    @PostMapping("/frequent/{targetUserId}")
    public ResponseEntity<String> markFrequentBuddy(@PathVariable Long targetUserId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.markFrequentBuddy(oidcUser.getSubject(), targetUserId);
        return ResponseEntity.ok("Travel buddy saved");
    }

    @PostMapping("/frequent/{targetUserId}/remove")
    public ResponseEntity<String> removeFrequentBuddy(@PathVariable Long targetUserId, @AuthenticationPrincipal OidcUser oidcUser) {
        friendService.removeFrequentBuddy(oidcUser.getSubject(), targetUserId);
        return ResponseEntity.ok("Travel buddy removed");
    }
}
