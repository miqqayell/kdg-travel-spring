package be.kdg.integration4.spring_backend.service.transit.gtfs;

import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed GTFS data with indexes used by the route planner.
 */
public final class GtfsStaticData {
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;
    private static final double SPATIAL_CELL_DEGREES = 0.01;
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final Map<String, Stop> stops;
    private final Map<String, Route> routes;
    private final Map<String, Trip> trips;
    private final Map<String, List<StopTime>> stopTimesByTrip;
    private final Map<String, List<StopDeparture>> departuresByStop;
    private final List<ServiceCalendar> calendars;
    private final List<ServiceCalendarDate> calendarDates;
    private final Map<Long, List<Stop>> spatialStopIndex;

    public GtfsStaticData(GtfsDaoImpl store) {
        this.stops = stops(store);
        this.routes = routes(store);
        this.trips = trips(store);
        this.stopTimesByTrip = stopTimesByTrip(store);
        this.departuresByStop = departuresByStop(stopTimesByTrip);
        this.calendars = calendars(store);
        this.calendarDates = calendarDates(store);
        this.spatialStopIndex = indexStopsByGrid(this.stops.values());
    }

    public Stop stop(String stopId) {
        return stops.get(stopId);
    }

    public Route route(String routeId) {
        return routes.get(routeId);
    }

    public Trip trip(String tripId) {
        return trips.get(tripId);
    }

    public Route routeForTrip(String tripId) {
        return routeForTrip(trip(tripId));
    }

    public Route routeForTrip(Trip trip) {
        return route(tripRouteId(trip));
    }

    public Stop stopForStopTime(StopTime stopTime) {
        return stop(stopId(stopTime));
    }

    /**
     * Finds the service ids that are active on the given date.
     */
    public Set<String> activeServiceIds(LocalDate date) {
        Set<String> activeServiceIds = new HashSet<>();
        calendars.stream()
                .filter(calendar -> activeOn(calendar, date))
                .map(calendar -> id(calendar.getServiceId()))
                .forEach(activeServiceIds::add);

        for (ServiceCalendarDate calendarDate : calendarDates) {
            if (!date(calendarDate.getDate()).equals(date)) {
                continue;
            }
            String serviceId = id(calendarDate.getServiceId());
            if (calendarDate.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_ADD) {
                activeServiceIds.add(serviceId);
            } else if (calendarDate.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_REMOVE) {
                activeServiceIds.remove(serviceId);
            }
        }

        // Some feeds only list special service days in calendar_dates.
        if (activeServiceIds.isEmpty()) {
            calendarDates.stream()
                    .filter(calendarDate -> date(calendarDate.getDate()).equals(date))
                    .filter(calendarDate -> calendarDate.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_ADD)
                    .map(calendarDate -> id(calendarDate.getServiceId()))
                    .forEach(activeServiceIds::add);
        }

        return activeServiceIds;
    }

    /**
     * Finds departures from one stop within a time window.
     */
    public List<StopDeparture> departuresInWindow(String stopId, int earliestSeconds, int latestSeconds, int limit) {
        List<StopDeparture> departures = departuresByStop.getOrDefault(stopId, List.of());
        if (departures.isEmpty() || limit <= 0) {
            return List.of();
        }

        int start = lowerBoundDeparture(departures, earliestSeconds);
        List<StopDeparture> matches = new ArrayList<>(Math.min(limit, departures.size() - start));
        for (int index = start; index < departures.size() && matches.size() < limit; index++) {
            StopDeparture departure = departures.get(index);
            if (departure.departureSeconds() > latestSeconds) {
                break;
            }
            matches.add(departure);
        }
        return matches;
    }

    public List<StopDeparture> departuresInWindow(String stopId, int earliestSeconds, int latestSeconds) {
        return departuresInWindow(stopId, earliestSeconds, latestSeconds, Integer.MAX_VALUE);
    }

    public List<StopTime> stopTimesForTrip(String tripId) {
        return stopTimesByTrip.getOrDefault(tripId, List.of());
    }

    /**
     * Gets the stop time for a specific sequence number on a trip.
     */
    public StopTime stopTimeAtSequence(String tripId, int sequence) {
        for (StopTime stopTime : stopTimesForTrip(tripId)) {
            if (stopTime.getStopSequence() == sequence) {
                return stopTime;
            }
            if (stopTime.getStopSequence() > sequence) {
                return null;
            }
        }
        return null;
    }

    /**
     * Finds nearby stops using the stop grid before checking exact distances.
     */
    public List<NearbyGtfsStop> nearbyStops(double latitude, double longitude, int radiusMeters, int limit) {
        if (limit <= 0 || radiusMeters <= 0) {
            return List.of();
        }

        double longitudeMeters = Math.max(1.0, METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(latitude)));
        int latitudeCells = (int) Math.ceil(radiusMeters / METERS_PER_LATITUDE_DEGREE / SPATIAL_CELL_DEGREES) + 1;
        int longitudeCells = (int) Math.ceil(radiusMeters / longitudeMeters / SPATIAL_CELL_DEGREES) + 1;
        int latitudeCell = cell(latitude);
        int longitudeCell = cell(longitude);

        List<NearbyGtfsStop> nearbyStops = new ArrayList<>();
        // The grid limits the search area. The distance check keeps the radius accurate.
        for (int lat = latitudeCell - latitudeCells; lat <= latitudeCell + latitudeCells; lat++) {
            for (int lon = longitudeCell - longitudeCells; lon <= longitudeCell + longitudeCells; lon++) {
                for (Stop stop : spatialStopIndex.getOrDefault(cellKey(lat, lon), List.of())) {
                    double distance = distanceInMeters(latitude, longitude, stop.getLat(), stop.getLon());
                    if (distance <= radiusMeters) {
                        nearbyStops.add(new NearbyGtfsStop(stop, distance));
                    }
                }
            }
        }

        return nearbyStops.stream()
                .sorted(Comparator.comparingDouble(NearbyGtfsStop::distanceMeters))
                .limit(limit)
                .toList();
    }

    public String id(AgencyAndId id) {
        if (id == null) {
            return null;
        }
        return hasText(id.getId()) ? id.getId() : id.toString();
    }

    public String stopId(Stop stop) {
        return stop == null ? null : id(stop.getId());
    }

    public String stopId(StopTime stopTime) {
        return stopTime == null || stopTime.getStopLocation() == null ? null : id(stopTime.getStopLocation().getId());
    }

    public String routeId(Route route) {
        return route == null ? null : id(route.getId());
    }

    public String tripId(Trip trip) {
        return trip == null ? null : id(trip.getId());
    }

    public String tripRouteId(Trip trip) {
        return trip == null || trip.getRoute() == null ? "" : routeId(trip.getRoute());
    }

    public String tripServiceId(Trip trip) {
        return trip == null ? null : id(trip.getServiceId());
    }

    public String shapeId(Trip trip) {
        return trip == null ? null : normalize(id(trip.getShapeId()));
    }

    public String stopName(Stop stop) {
        return firstText(stop == null ? null : stop.getName());
    }

    public String routeShortName(Route route) {
        return firstText(route == null ? null : route.getShortName(), routeId(route));
    }

    public String routeLongName(Route route) {
        return firstText(route == null ? null : route.getLongName());
    }

    public String routeColor(Route route) {
        String color = normalize(route == null ? null : route.getColor());
        return color == null ? null : "#" + color.replace("#", "");
    }

    private Map<String, Stop> stops(GtfsDaoImpl store) {
        Map<String, Stop> stops = new LinkedHashMap<>();
        for (Stop stop : store.getAllStops()) {
            String stopId = stopId(stop);
            if (hasText(stopId)) {
                stops.put(stopId, stop);
            }
        }
        return Map.copyOf(stops);
    }

    private Map<String, Route> routes(GtfsDaoImpl store) {
        Map<String, Route> routes = new LinkedHashMap<>();
        for (Route route : store.getAllRoutes()) {
            String routeId = routeId(route);
            if (hasText(routeId)) {
                routes.put(routeId, route);
            }
        }
        return Map.copyOf(routes);
    }

    private Map<String, Trip> trips(GtfsDaoImpl store) {
        Map<String, Trip> trips = new LinkedHashMap<>();
        for (Trip trip : store.getAllTrips()) {
            String tripId = tripId(trip);
            if (hasText(tripId)) {
                trips.put(tripId, trip);
            }
        }
        return Map.copyOf(trips);
    }

    private Map<String, List<StopTime>> stopTimesByTrip(GtfsDaoImpl store) {
        Map<String, List<StopTime>> stopTimesByTrip = new HashMap<>();
        for (StopTime stopTime : store.getAllStopTimes()) {
            String tripId = stopTime.getTrip() == null ? null : tripId(stopTime.getTrip());
            String stopId = stopId(stopTime);
            // The planner only uses real stops with fixed arrival and departure times.
            if (hasText(tripId) && hasText(stopId) && stopTime.isArrivalTimeSet() && stopTime.isDepartureTimeSet()) {
                stopTimesByTrip.computeIfAbsent(tripId, ignored -> new ArrayList<>()).add(stopTime);
            }
        }
        stopTimesByTrip.replaceAll((ignored, stopTimes) -> stopTimes.stream()
                .sorted(Comparator.comparingInt(StopTime::getStopSequence))
                .toList());
        return copyListMap(stopTimesByTrip);
    }

    private Map<String, List<StopDeparture>> departuresByStop(Map<String, List<StopTime>> stopTimesByTrip) {
        Map<String, List<StopDeparture>> departuresByStop = new HashMap<>();
        for (Map.Entry<String, List<StopTime>> entry : stopTimesByTrip.entrySet()) {
            for (StopTime stopTime : entry.getValue()) {
                departuresByStop.computeIfAbsent(stopId(stopTime), ignored -> new ArrayList<>())
                        .add(new StopDeparture(
                                entry.getKey(),
                                stopTime.getArrivalTime(),
                                stopTime.getDepartureTime(),
                                stopTime.getStopSequence()
                        ));
            }
        }
        departuresByStop.replaceAll((ignored, departures) -> departures.stream()
                .sorted(Comparator.comparingInt(StopDeparture::departureSeconds))
                .toList());
        return copyListMap(departuresByStop);
    }

    private List<ServiceCalendar> calendars(GtfsDaoImpl store) {
        return store.getAllCalendars().stream()
                .filter(calendar -> hasText(id(calendar.getServiceId())))
                .toList();
    }

    private List<ServiceCalendarDate> calendarDates(GtfsDaoImpl store) {
        return store.getAllCalendarDates().stream()
                .filter(calendarDate -> hasText(id(calendarDate.getServiceId())))
                .toList();
    }

    private boolean activeOn(ServiceCalendar calendar, LocalDate date) {
        LocalDate startDate = date(calendar.getStartDate());
        LocalDate endDate = date(calendar.getEndDate());
        return !date.isBefore(startDate)
                && !date.isAfter(endDate)
                && activeDays(calendar).contains(date.getDayOfWeek());
    }

    private Set<DayOfWeek> activeDays(ServiceCalendar calendar) {
        Set<DayOfWeek> days = new HashSet<>();
        addDay(days, calendar.getMonday(), DayOfWeek.MONDAY);
        addDay(days, calendar.getTuesday(), DayOfWeek.TUESDAY);
        addDay(days, calendar.getWednesday(), DayOfWeek.WEDNESDAY);
        addDay(days, calendar.getThursday(), DayOfWeek.THURSDAY);
        addDay(days, calendar.getFriday(), DayOfWeek.FRIDAY);
        addDay(days, calendar.getSaturday(), DayOfWeek.SATURDAY);
        addDay(days, calendar.getSunday(), DayOfWeek.SUNDAY);
        return days;
    }

    private void addDay(Set<DayOfWeek> days, int flag, DayOfWeek day) {
        if (flag == 1) {
            days.add(day);
        }
    }

    private LocalDate date(ServiceDate date) {
        if (date == null) {
            return LocalDate.MIN;
        }
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    private static <T> Map<String, List<T>> copyListMap(Map<String, List<T>> source) {
        Map<String, List<T>> copy = new HashMap<>(source.size());
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Long, List<Stop>> indexStopsByGrid(Iterable<Stop> stops) {
        Map<Long, List<Stop>> mutableIndex = new HashMap<>();
        for (Stop stop : stops) {
            mutableIndex.computeIfAbsent(cellKey(cell(stop.getLat()), cell(stop.getLon())), ignored -> new ArrayList<>()).add(stop);
        }

        Map<Long, List<Stop>> index = new HashMap<>();
        mutableIndex.forEach((key, value) -> index.put(key, List.copyOf(value)));
        return Map.copyOf(index);
    }

    private static int lowerBoundDeparture(List<StopDeparture> departures, int earliestSeconds) {
        // Departures are sorted during indexing, so binary search is enough here.
        int low = 0;
        int high = departures.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (departures.get(middle).departureSeconds() < earliestSeconds) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / SPATIAL_CELL_DEGREES);
    }

    private static long cellKey(int latitudeCell, int longitudeCell) {
        return (((long) latitudeCell) << 32) ^ (longitudeCell & 0xffffffffL);
    }

    private static double distanceInMeters(double firstLatitude,
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record StopDeparture(String tripId, int arrivalSeconds, int departureSeconds, int sequence) {
    }

    public record NearbyGtfsStop(Stop stop, double distanceMeters) {
    }
}
