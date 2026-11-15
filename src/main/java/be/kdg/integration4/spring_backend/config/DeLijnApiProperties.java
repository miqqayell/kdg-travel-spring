package be.kdg.integration4.spring_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "delijn.api")
public record DeLijnApiProperties(
        String gtfsStaticUrl,
        String gtfsStaticSubscriptionKey,
        String gtfsCacheDirectory,
        int maxWalkingDistanceMeters
) {
    public DeLijnApiProperties {
        if (gtfsStaticUrl == null || gtfsStaticUrl.isBlank()) {
            gtfsStaticUrl = "https://api.delijn.be/gtfs/static/v3/gtfs_transit.zip";
        }
        if (gtfsCacheDirectory == null || gtfsCacheDirectory.isBlank()) {
            gtfsCacheDirectory = ".cache/delijn";
        }
        if (maxWalkingDistanceMeters <= 0) {
            maxWalkingDistanceMeters = 1200;
        }
    }

    public boolean hasGtfsStaticSubscriptionKey() {
        return gtfsStaticSubscriptionKey != null && !gtfsStaticSubscriptionKey.isBlank();
    }
}
