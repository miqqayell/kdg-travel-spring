package be.kdg.integration4.spring_backend.controller.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalkingDistanceRequest {
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @NotNull
    private Double startPointLatitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @NotNull
    private Double startPointLongitude;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @NotNull
    private Double endPointLatitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @NotNull
    private Double endPointLongitude;
}
