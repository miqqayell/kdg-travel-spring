package be.kdg.integration4.spring_backend.controller.web.dto;

import java.time.LocalDateTime;

public record TravelGroupMemberItem(
        Long id,
        String username,
        String email,
        String profilePictureUrl,
        LocalDateTime joinedAt,
        boolean currentUser,
        boolean friend,
        boolean creator
) {
}
