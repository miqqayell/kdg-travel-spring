package be.kdg.integration4.spring_backend.controller.api;

import be.kdg.integration4.spring_backend.controller.api.dto.chat.ChatMessageResponse;
import be.kdg.integration4.spring_backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/chat")
@RequiredArgsConstructor
public class ChatRestController {
    private final ChatService chatService;

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @PathVariable Long groupId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        return ResponseEntity.ok(chatService.getChatHistory(groupId, oidcUser.getSubject()));
    }
}