package be.kdg.integration4.spring_backend.controller.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserListItem {
    private Long id;
    private String username;
    private String email;
    private String profilePictureUrl;
    private boolean isCurrentUser;

    private String relationStatus;
}