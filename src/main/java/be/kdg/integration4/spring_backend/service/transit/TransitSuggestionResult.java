package be.kdg.integration4.spring_backend.service.transit;

import be.kdg.integration4.spring_backend.dto.transit.SuggestedRoute;

import java.util.List;

/**
 * Route suggestions with a status that explains an empty result.
 */
public record TransitSuggestionResult(
        SuggestedRoute route,
        List<SuggestedRoute> routes,
        Status status
) {
    public TransitSuggestionResult {
        routes = routes == null
                ? route == null ? List.of() : List.of(route)
                : List.copyOf(routes);
        if (route == null && !routes.isEmpty()) {
            route = routes.getFirst();
        }
        if (status == null) {
            status = route == null
                    ? Status.NO_ROUTE
                    : Status.OK;
        }
    }

    public TransitSuggestionResult(SuggestedRoute route) {
        this(route, null, null);
    }

    public TransitSuggestionResult(List<SuggestedRoute> routes) {
        this(null, routes, null);
    }

    public static TransitSuggestionResult empty() {
        return new TransitSuggestionResult(null, List.of(), null);
    }

    public static TransitSuggestionResult dataUnavailable() {
        return new TransitSuggestionResult(null, List.of(), Status.DATA_UNAVAILABLE);
    }

    public enum Status {
        OK,
        NO_ROUTE,
        DATA_UNAVAILABLE
    }
}
