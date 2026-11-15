package be.kdg.integration4.spring_backend.service.routing;

import be.kdg.integration4.spring_backend.config.PedestrianRoutingProperties;
import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.exception.PedestrianRoutingException;
import be.kdg.integration4.spring_backend.service.routing.PedestrianRoutingService.WalkingRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OsrmPedestrianRoutingService implements PedestrianRoutingService {
    private static final Logger logger = LoggerFactory.getLogger(OsrmPedestrianRoutingService.class);

    private final HttpClient httpClient;
    private final PedestrianRoutingProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, WalkingRoute> cache = new ConcurrentHashMap<>();

    public OsrmPedestrianRoutingService(HttpClient httpClient,
                                        PedestrianRoutingProperties properties,
                                        ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Caches walking routes because the same stop pairs are often checked more than once.
     */
    @Override
    public Optional<WalkingRoute> route(GeoPoint from, GeoPoint to) {
        String key = cacheKey(from, to);
        WalkingRoute cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        return fetchRoute(from, to)
                .map(route -> {
                    cache.put(key, route);
                    return route;
                });
    }

    private Optional<WalkingRoute> fetchRoute(GeoPoint from, GeoPoint to) {
        HttpRequest request = HttpRequest.newBuilder(uri(from, to))
                .timeout(properties.requestTimeout())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new PedestrianRoutingException("Pedestrian routing returned HTTP " + response.statusCode());
            }
            return parseRoute(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PedestrianRoutingException("Pedestrian routing was interrupted", ex);
        } catch (PedestrianRoutingException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.debug("Pedestrian routing failed: {}", ex.getMessage());
            throw new PedestrianRoutingException("Pedestrian routing failed", ex);
        }
    }

    /**
     * Reads the distance, duration and geometry from an OSRM route response.
     */
    Optional<WalkingRoute> parseRoute(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"Ok".equals(root.path("code").asString(""))) {
                return Optional.empty();
            }
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty() || !routes.get(0).isObject()) {
                return Optional.empty();
            }
            JsonNode route = routes.get(0);
            JsonNode distance = route.path("distance");
            JsonNode duration = route.path("duration");
            if (!isNonNegativeNumber(distance) || !isNonNegativeNumber(duration)) {
                return Optional.empty();
            }
            return Optional.of(new WalkingRoute(
                    distance.asDouble(),
                    (int) Math.ceil(duration.asDouble()),
                    geometry(route)
            ));
        } catch (JacksonException ex) {
            throw new PedestrianRoutingException("Pedestrian routing response could not be parsed", ex);
        }
    }

    private boolean isNonNegativeNumber(JsonNode node) {
        return node.isNumber() && node.asDouble() >= 0;
    }

    private List<GeoPoint> geometry(JsonNode route) {
        JsonNode coordinates = route.path("geometry").path("coordinates");
        if (!coordinates.isArray()) {
            return List.of();
        }
        List<GeoPoint> points = new ArrayList<>();
        for (JsonNode coordinate : coordinates) {
            if (isCoordinatePair(coordinate)) {
                // OSRM GeoJSON stores longitude first. GeoPoint stores latitude first.
                points.add(new GeoPoint(coordinate.get(1).asDouble(), coordinate.get(0).asDouble()));
            }
        }
        return points;
    }

    private boolean isCoordinatePair(JsonNode coordinate) {
        return coordinate.isArray()
                && coordinate.size() >= 2
                && coordinate.get(0).isNumber()
                && coordinate.get(1).isNumber();
    }

    private URI uri(GeoPoint from, GeoPoint to) {
        String coordinates = coordinate(from) + ";" + coordinate(to);
        String encodedProfile = URLEncoder.encode(properties.profile(), StandardCharsets.UTF_8);
        String path = "%s/route/v1/%s/%s?overview=full&geometries=geojson&alternatives=false&steps=false"
                .formatted(properties.baseUrl().replaceAll("/+$", ""), encodedProfile, coordinates);
        return URI.create(path);
    }

    private String coordinate(GeoPoint point) {
        return String.format(Locale.ROOT, "%.6f,%.6f", point.longitude(), point.latitude());
    }

    private String cacheKey(GeoPoint from, GeoPoint to) {
        return coordinate(from) + ";" + coordinate(to);
    }
}
