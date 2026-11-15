package be.kdg.integration4.spring_backend.controller.api;

import be.kdg.integration4.spring_backend.controller.api.dto.chat.ChatMessageRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.chat.ChatMessageResponse;
import be.kdg.integration4.spring_backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @MessageMapping("/group/{groupId}/send")
    @SendTo("/topic/group/{groupId}")
    public ChatMessageResponse sendMessage(@DestinationVariable Long groupId, ChatMessageRequest request, Principal principal) {
        return chatService.saveAndReturnMessage(groupId, request.getContent(), principal.getName());
    }
}