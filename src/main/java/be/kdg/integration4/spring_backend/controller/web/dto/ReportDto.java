package be.kdg.integration4.spring_backend.controller.web.dto;

import be.kdg.integration4.spring_backend.domain.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportDto {
    @NotNull
    private ReportType type;

    @Size(max = 1000)
    private String reason;

    @Size(max = 3000)
    private String extraInformation;

    private Long reportedUserId;
    private Long reportedActivityId;
    private Long reportedTravelGroupId;
}