package be.kdg.integration4.spring_backend.service.transit;

import be.kdg.integration4.spring_backend.dto.transit.SuggestedRoute;
import be.kdg.integration4.spring_backend.exception.DeLijnApiException;
import be.kdg.integration4.spring_backend.exception.PedestrianRoutingException;
import be.kdg.integration4.spring_backend.service.transit.planning.StaticGtfsRoutePlanner;
import be.kdg.integration4.spring_backend.service.transit.gtfs.GtfsStaticCacheService;
import be.kdg.integration4.spring_backend.service.transit.gtfs.GtfsStaticData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransitRouteSuggestionService {
    private static final Logger logger = LoggerFactory.getLogger(TransitRouteSuggestionService.class);

    private final GtfsStaticCacheService gtfsStaticCacheService;
    private final StaticGtfsRoutePlanner staticGtfsRoutePlanner;

    public TransitRouteSuggestionService(GtfsStaticCacheService gtfsStaticCacheService,
                                         StaticGtfsRoutePlanner staticGtfsRoutePlanner) {
        this.gtfsStaticCacheService = gtfsStaticCacheService;
        this.staticGtfsRoutePlanner = staticGtfsRoutePlanner;
    }

    /**
     * Suggests routes and reports whether missing suggestions are caused by missing data.
     */
    public TransitSuggestionResult suggestTransitRouteWithMetadata(TransitRouteQuery query) {
        try {
            GtfsStaticData staticData = gtfsStaticCacheService.getStaticData(query.meetupTime().toLocalDate());
            List<SuggestedRoute> routes = staticGtfsRoutePlanner.suggestTransitRoutes(query, staticData);
            return routes.isEmpty()
                    ? TransitSuggestionResult.empty()
                    : new TransitSuggestionResult(routes);
        } catch (DeLijnApiException ex) {
            logger.warn("Could not load De Lijn GTFS data for transit route suggestion: {}", ex.getMessage());
            return TransitSuggestionResult.dataUnavailable();
        } catch (PedestrianRoutingException ex) {
            logger.warn("Could not load pedestrian routing data for transit route suggestion: {}", ex.getMessage());
            return TransitSuggestionResult.dataUnavailable();
        }
    }
}
