package be.kdg.integration4.spring_backend.controller.web.dto;


import be.kdg.integration4.spring_backend.domain.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {
    private Long id;
    private String title;
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private Double locationLatitude;
    private Double locationLongitude;
    private String locationAddress;
    private VerificationStatus verificationStatus;
    private String imageUrl;
    private String createdByUsername;
}