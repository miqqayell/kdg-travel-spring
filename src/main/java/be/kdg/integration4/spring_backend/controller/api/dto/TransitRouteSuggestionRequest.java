package be.kdg.integration4.spring_backend.controller.api.dto;

import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class TransitRouteSuggestionRequest {
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double startPointLatitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double startPointLongitude;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double endPointLatitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double endPointLongitude;

    private TransportMode transportationMode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime meetupTime;
}
