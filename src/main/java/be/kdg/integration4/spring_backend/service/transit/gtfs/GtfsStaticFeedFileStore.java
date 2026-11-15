package be.kdg.integration4.spring_backend.service.transit.gtfs;

import be.kdg.integration4.spring_backend.config.DeLijnApiProperties;
import be.kdg.integration4.spring_backend.exception.DeLijnApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class GtfsStaticFeedFileStore {
    private static final Logger logger = LoggerFactory.getLogger(GtfsStaticFeedFileStore.class);
    private static final String STATIC_FEED_FILE_NAME = "gtfs_transit.zip";
    private static final Duration STATIC_FEED_MAX_AGE = Duration.ofHours(24);

    private final HttpClient deLijnHttpClient;
    private final DeLijnApiProperties properties;

    /**
     * Makes sure there is a local GTFS zip before the parser runs.
     */
    Path ensureStaticFeedFile() {
        Path cacheDirectory = cacheDirectory();
        Path zipPath = cacheDirectory.resolve(STATIC_FEED_FILE_NAME);
        try {
            Files.createDirectories(cacheDirectory);
            if (isFreshZip(zipPath)) {
                return zipPath;
            }

            try {
                Files.write(zipPath, downloadStaticFeed());
            } catch (DeLijnApiException ex) {
                // A stale feed is still useful when the De Lijn API is temporarily unavailable.
                if (isUsableZip(zipPath)) {
                    logger.warn("Using stale De Lijn GTFS static zip because refresh failed: {}", ex.getMessage());
                    return zipPath;
                }
                throw ex;
            }
            return zipPath;
        } catch (IOException ex) {
            throw new DeLijnApiException("Could not prepare De Lijn GTFS static cache file", ex);
        }
    }

    private Path cacheDirectory() {
        return Path.of(properties.gtfsCacheDirectory());
    }

    private boolean isFreshZip(Path zipPath) throws IOException {
        return isUsableZip(zipPath)
                && Files.getLastModifiedTime(zipPath).toInstant()
                .plus(STATIC_FEED_MAX_AGE)
                .isAfter(java.time.Instant.now());
    }

    private boolean isUsableZip(Path zipPath) throws IOException {
        return Files.exists(zipPath) && Files.size(zipPath) > 0;
    }

    private byte[] downloadStaticFeed() {
        if (!properties.hasGtfsStaticSubscriptionKey()) {
            throw new DeLijnApiException("De Lijn GTFS static subscription key is not configured");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.gtfsStaticUrl()))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/zip")
                .header("Ocp-Apim-Subscription-Key", properties.gtfsStaticSubscriptionKey())
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = deLijnHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                logger.warn("De Lijn GTFS static zip download failed with HTTP {}", response.statusCode());
                throw new DeLijnApiException("De Lijn GTFS static API returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            logger.warn("De Lijn GTFS static zip download failed: {}", ex.getMessage());
            throw new DeLijnApiException("Could not download De Lijn GTFS static feed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("De Lijn GTFS static zip download was interrupted");
            throw new DeLijnApiException("De Lijn GTFS static request was interrupted", ex);
        }
    }
}
