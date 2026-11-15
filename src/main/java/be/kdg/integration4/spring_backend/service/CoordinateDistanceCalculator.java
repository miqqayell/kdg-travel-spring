package be.kdg.integration4.spring_backend.service;

import org.springframework.stereotype.Component;

@Component
public class CoordinateDistanceCalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    public double distanceInMeters(double firstLatitude,
                                   double firstLongitude,
                                   double secondLatitude,
                                   double secondLongitude) {
        double firstLatitudeRadians = Math.toRadians(firstLatitude);
        double secondLatitudeRadians = Math.toRadians(secondLatitude);
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);

        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));

        return EARTH_RADIUS_METERS * centralAngle;
    }
}
