package be.kdg.integration4.spring_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "routing.pedestrian")
public record PedestrianRoutingProperties(
        String baseUrl,
        String profile,
        Duration requestTimeout
) {
    public PedestrianRoutingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://routing.openstreetmap.de/routed-foot";
        }
        if (profile == null || profile.isBlank()) {
            profile = "foot";
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            requestTimeout = Duration.ofSeconds(3);
        }
    }
}
