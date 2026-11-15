package be.kdg.integration4.spring_backend.controller.web.dto;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class TravelGroupForm {
    private Long activityId;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double startPointLatitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double startPointLongitude;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double endPointLatitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double endPointLongitude;

    @NotNull
    private TransportMode transportationMode;

    @DecimalMin(value = "0.0")
    private Double distanceKm;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime meetupTime;

    @NotNull
    @Min(1)
    @Max(99)
    private Integer maxSize;

    @Size(max = 4096)
    private String selectedRouteToken;

    @Size(max = 100)
    private String selectedRouteStartStopId;

    @Size(max = 255)
    private String selectedRouteStartStopName;

    @Size(max = 100)
    private String selectedRouteEndStopId;

    @Size(max = 255)
    private String selectedRouteEndStopName;

    @Size(max = 100)
    private String selectedRouteLineNumber;

    @Size(max = 100)
    private String selectedRouteDirection;

    @Size(max = 255)
    private String selectedRouteDestination;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime selectedRouteDepartureTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime selectedRouteEstimatedArrivalTime;

    @Size(max = 100)
    private String selectedRouteRealTimeStatus;

    @Min(0)
    @Max(1440)
    private Integer selectedRouteEstimatedDelayMinutes;

    @Min(0)
    private Integer selectedPublicTransportRouteIndex = 0;

    private Boolean contributionRequired = false;
}
