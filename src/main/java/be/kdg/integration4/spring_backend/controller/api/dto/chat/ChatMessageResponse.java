package be.kdg.integration4.spring_backend.controller.api.dto.chat;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long messageId,
        Long senderId,
        String content,
        String senderName,
        String senderAvatar,
        LocalDateTime time,
        boolean isCurrentUser
) {}
