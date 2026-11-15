package be.kdg.integration4.spring_backend.controller.web.dto;

public record Co2SavingOverTimeDto(
        String date,
        double co2SavedKg
) {
}
