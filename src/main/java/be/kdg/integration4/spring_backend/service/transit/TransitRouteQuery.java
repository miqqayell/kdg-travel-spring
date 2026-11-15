package be.kdg.integration4.spring_backend.service.transit;

import be.kdg.integration4.spring_backend.controller.api.dto.TransitRouteSuggestionRequest;
import be.kdg.integration4.spring_backend.controller.api.dto.TravelGroupRequest;
import be.kdg.integration4.spring_backend.domain.enums.TransportMode;
import be.kdg.integration4.spring_backend.dto.transit.GeoPoint;
import be.kdg.integration4.spring_backend.exception.BadRequestException;

import java.time.LocalDateTime;
import java.util.Locale;

public record TransitRouteQuery(
        GeoPoint startPoint,
        GeoPoint endPoint,
        LocalDateTime meetupTime,
        TransportMode transportMode
) {
    public TransitRouteQuery {
        if (transportMode != TransportMode.PUBLIC_TRANSPORT) {
            throw new BadRequestException("Public transport route suggestion requires public transport mode");
        }
        if (startPoint == null || endPoint == null || meetupTime == null) {
            throw new BadRequestException("Public transport route suggestion requires start, destination and meetup time");
        }
        validateLatitude(startPoint.latitude(), "Start latitude");
        validateLongitude(startPoint.longitude(), "Start longitude");
        validateLatitude(endPoint.latitude(), "Destination latitude");
        validateLongitude(endPoint.longitude(), "Destination longitude");
    }

    /**
     * Used by the group form, where coordinates arrive as separate latitude and longitude fields.
     */
    public static TransitRouteQuery from(TravelGroupRequest request) {
        if (request == null) {
            throw new BadRequestException("Missing travel group request");
        }
        if (request.getStartPointLatitude() == null
                || request.getStartPointLongitude() == null
                || request.getEndPointLatitude() == null
                || request.getEndPointLongitude() == null) {
            throw new BadRequestException("Public transport route suggestion requires start and destination coordinates");
        }

        return fromValues(
                request.getStartPointLatitude(),
                request.getStartPointLongitude(),
                request.getEndPointLatitude(),
                request.getEndPointLongitude(),
                request.getMeetupTime(),
                request.getTransportationMode()
        );
    }

    /**
     * Used by the async route suggestion endpoint before the group is saved.
     */
    public static TransitRouteQuery fromSuggestionRequest(TransitRouteSuggestionRequest request) {
        if (request == null) {
            throw new BadRequestException("Missing route suggestion request");
        }
        if (request.getStartPointLatitude() == null
                || request.getStartPointLongitude() == null
                || request.getEndPointLatitude() == null
                || request.getEndPointLongitude() == null) {
            throw new BadRequestException("Public transport route suggestion requires start and destination coordinates");
        }

        return fromValues(
                request.getStartPointLatitude(),
                request.getStartPointLongitude(),
                request.getEndPointLatitude(),
                request.getEndPointLongitude(),
                request.getMeetupTime(),
                request.getTransportationMode()
        );
    }

    private static TransitRouteQuery fromValues(Double startLatitude,
                                                Double startLongitude,
                                                Double endLatitude,
                                                Double endLongitude,
                                                LocalDateTime meetupTime,
                                                TransportMode transportMode) {
        return new TransitRouteQuery(
                new GeoPoint(startLatitude, startLongitude),
                new GeoPoint(endLatitude, endLongitude),
                meetupTime,
                transportMode
        );
    }

    /**
     * Rounds coordinates so small formatting differences do not create new cache keys.
     */
    public String fingerprint() {
        return String.join("|",
                transportMode.name(),
                coordinate(startPoint.latitude()),
                coordinate(startPoint.longitude()),
                coordinate(endPoint.latitude()),
                coordinate(endPoint.longitude()),
                meetupTime.toString()
        );
    }

    private String coordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static void validateLatitude(double latitude, String label) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new BadRequestException(label + " must be between -90 and 90");
        }
    }

    private static void validateLongitude(double longitude, String label) {
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new BadRequestException(label + " must be between -180 and 180");
        }
    }
}
