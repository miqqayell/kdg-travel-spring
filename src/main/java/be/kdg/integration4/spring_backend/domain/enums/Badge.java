package be.kdg.integration4.spring_backend.domain.enums;

public enum Badge {
    FIRST_TRIP("Green Pioneer", "Completed your first sustainable trip!", "bi-leaf"),
    ECO_WARRIOR("Eco Warrior", "Completed 5 sustainable trips.", "bi-tree"),
    TRANSIT_MASTER("Transit Master", "Completed 10 public transport journeys.", "bi-train-front"),
    CO2_CHAMPION("CO2 Champion", "Saved over 50kg of CO2!", "bi-cloud-arrow-down");

    private final String displayName;
    private final String description;
    private final String iconClass;

    Badge(String displayName, String description, String iconClass) {
        this.displayName = displayName;
        this.description = description;
        this.iconClass = iconClass;
    }

    // --- MANUALLY ADDED GETTERS ---
    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getIconClass() {
        return iconClass;
    }
}