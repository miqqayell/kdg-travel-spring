package be.kdg.integration4.spring_backend.service.transit.gtfs;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GtfsStaticCacheService {
    private static final Logger logger = LoggerFactory.getLogger(GtfsStaticCacheService.class);
    private static final int PREWARM_DAYS = 14;

    private final GtfsStaticFeedFileStore feedFileStore;
    private final GtfsStaticFeedParser parser;

    private volatile GtfsStaticData staticData;
    private final Map<LocalDate, GtfsStaticData> staticDataByServiceDate = new ConcurrentHashMap<>();

    /**
     * Loads the full feed once and reuses it for later calls.
     */
    public GtfsStaticData getStaticData() {
        GtfsStaticData cachedData = staticData;
        if (cachedData != null) {
            return cachedData;
        }

        synchronized (this) {
            // Check again inside the lock in case another request already loaded the feed.
            cachedData = staticData;
            if (cachedData != null) {
                return cachedData;
            }

            Path zipPath = feedFileStore.ensureStaticFeedFile();
            GtfsStaticData parsedData = parser.parseStaticData(zipPath);
            staticData = parsedData;
            return parsedData;
        }
    }

    /**
     * Loads feed data for one service date.
     */
    public GtfsStaticData getStaticData(LocalDate serviceDate) {
        if (serviceDate == null) {
            return getStaticData();
        }
        return staticDataByServiceDate.computeIfAbsent(serviceDate, this::parseStaticDataForDate);
    }

    @EventListener(ApplicationReadyEvent.class)
    void prewarmUpcomingWeek() {
        CompletableFuture.runAsync(() -> {
            LocalDate today = LocalDate.now();
            for (int day = 0; day < PREWARM_DAYS; day++) {
                LocalDate serviceDate = today.plusDays(day);
                // Load likely meetup dates early so the first route request is faster.
                try {
                    getStaticData(serviceDate);
                } catch (RuntimeException ex) {
                    logger.warn("Could not prewarm De Lijn GTFS static data for {}: {}", serviceDate, ex.getMessage());
                }
            }
        });
    }

    private GtfsStaticData parseStaticDataForDate(LocalDate serviceDate) {
        Path zipPath = feedFileStore.ensureStaticFeedFile();
        return parser.parseStaticData(zipPath, serviceDate);
    }
}
