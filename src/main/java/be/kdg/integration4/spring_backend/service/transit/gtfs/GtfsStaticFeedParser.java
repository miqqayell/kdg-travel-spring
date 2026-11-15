package be.kdg.integration4.spring_backend.service.transit.gtfs;

import be.kdg.integration4.spring_backend.exception.DeLijnApiException;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class GtfsStaticFeedParser {
    private static final Logger logger = LoggerFactory.getLogger(GtfsStaticFeedParser.class);
    private static final String DEFAULT_AGENCY_ID = "DL";

    /**
     * Parses the complete feed without filtering by service date.
     */
    public GtfsStaticData parseStaticData(Path zipPath) {
        return parseStaticData(zipPath, null);
    }

    /**
     * Parses the feed and keeps only trips for one service date when a date is provided.
     */
    public GtfsStaticData parseStaticData(Path zipPath, LocalDate serviceDate) {
        try {
            GtfsDaoImpl store = readFeed(zipPath, serviceDate);
            return new GtfsStaticData(store);
        } catch (IOException | RuntimeException ex) {
            logger.warn("Could not parse De Lijn GTFS static feed {}: {}", zipPath, ex.getMessage());
            throw new DeLijnApiException("Could not parse De Lijn GTFS static feed", ex);
        }
    }

    private GtfsDaoImpl readFeed(Path zipPath, LocalDate serviceDate) throws IOException {
        GtfsDaoImpl store = new GtfsDaoImpl();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Map<String, Agency> agencies = readAgencies(zip, store);
            Map<String, Stop> stops = readStops(zip, store);
            Map<String, Route> routes = readRoutes(zip, store, agencies);
            Map<String, ServiceCalendar> calendars = readCalendars(zip, store);
            Map<String, ServiceCalendarDate> calendarDates = readCalendarDates(zip, store);
            Set<String> activeServiceIds = serviceDate == null
                    ? Set.of()
                    : activeServiceIds(calendars, calendarDates, serviceDate);
            // Filter trips before reading stop_times to keep the parsed data smaller.
            Map<String, Trip> trips = readTrips(zip, store, routes, activeServiceIds, serviceDate == null);
            readStopTimes(zip, store, stops, trips);
        }
        return store;
    }

    private Map<String, Agency> readAgencies(ZipFile zip, GtfsDaoImpl store) throws IOException {
        Map<String, Agency> agencies = new LinkedHashMap<>();
        readRows(zip, "agency.txt", (header, row) -> {
            String agencyId = text(value(header, row, "agency_id"), DEFAULT_AGENCY_ID);
            Agency agency = new Agency();
            agency.setId(agencyId);
            agency.setName(value(header, row, "agency_name"));
            agency.setUrl(value(header, row, "agency_url"));
            agency.setTimezone(value(header, row, "agency_timezone"));
            agencies.put(agencyId, agency);
            store.saveEntity(agency);
        });
        if (agencies.isEmpty()) {
            Agency agency = new Agency();
            agency.setId(DEFAULT_AGENCY_ID);
            agencies.put(DEFAULT_AGENCY_ID, agency);
            store.saveEntity(agency);
        }
        return agencies;
    }

    private Map<String, Stop> readStops(ZipFile zip, GtfsDaoImpl store) throws IOException {
        Map<String, Stop> stops = new LinkedHashMap<>();
        readRows(zip, "stops.txt", (header, row) -> {
            String stopId = value(header, row, "stop_id");
            Double latitude = decimal(value(header, row, "stop_lat"));
            Double longitude = decimal(value(header, row, "stop_lon"));
            if (!hasText(stopId) || latitude == null || longitude == null) {
                return;
            }
            Stop stop = new Stop();
            stop.setId(id(stopId));
            stop.setName(value(header, row, "stop_name"));
            stop.setLat(latitude);
            stop.setLon(longitude);
            stops.put(stopId, stop);
            store.saveEntity(stop);
        });
        return stops;
    }

    private Map<String, Route> readRoutes(ZipFile zip, GtfsDaoImpl store, Map<String, Agency> agencies) throws IOException {
        Map<String, Route> routes = new LinkedHashMap<>();
        readRows(zip, "routes.txt", (header, row) -> {
            String routeId = value(header, row, "route_id");
            if (!hasText(routeId)) {
                return;
            }
            Route route = new Route();
            route.setId(id(routeId));
            route.setAgency(agency(agencies, value(header, row, "agency_id")));
            route.setShortName(value(header, row, "route_short_name"));
            route.setLongName(value(header, row, "route_long_name"));
            route.setColor(value(header, row, "route_color"));
            Integer routeType = integer(value(header, row, "route_type"));
            if (routeType != null) {
                route.setType(routeType);
            }
            routes.put(routeId, route);
            store.saveEntity(route);
        });
        return routes;
    }

    private Map<String, ServiceCalendar> readCalendars(ZipFile zip, GtfsDaoImpl store) throws IOException {
        Map<String, ServiceCalendar> calendars = new LinkedHashMap<>();
        readRows(zip, "calendar.txt", (header, row) -> {
            String serviceId = value(header, row, "service_id");
            if (!hasText(serviceId)) {
                return;
            }
            ServiceCalendar calendar = new ServiceCalendar();
            calendar.setId(serviceId.hashCode());
            calendar.setServiceId(id(serviceId));
            calendar.setMonday(flag(header, row, "monday"));
            calendar.setTuesday(flag(header, row, "tuesday"));
            calendar.setWednesday(flag(header, row, "wednesday"));
            calendar.setThursday(flag(header, row, "thursday"));
            calendar.setFriday(flag(header, row, "friday"));
            calendar.setSaturday(flag(header, row, "saturday"));
            calendar.setSunday(flag(header, row, "sunday"));
            calendar.setStartDate(serviceDate(value(header, row, "start_date")));
            calendar.setEndDate(serviceDate(value(header, row, "end_date")));
            calendars.put(serviceId, calendar);
            store.saveEntity(calendar);
        });
        return calendars;
    }

    private Map<String, ServiceCalendarDate> readCalendarDates(ZipFile zip, GtfsDaoImpl store) throws IOException {
        Map<String, ServiceCalendarDate> calendarDates = new LinkedHashMap<>();
        int[] id = {1};
        readRows(zip, "calendar_dates.txt", (header, row) -> {
            String serviceId = value(header, row, "service_id");
            if (!hasText(serviceId)) {
                return;
            }
            ServiceCalendarDate calendarDate = new ServiceCalendarDate();
            calendarDate.setId(id[0]++);
            calendarDate.setServiceId(id(serviceId));
            calendarDate.setDate(serviceDate(value(header, row, "date")));
            Integer exceptionType = integer(value(header, row, "exception_type"));
            calendarDate.setExceptionType(exceptionType == null ? 0 : exceptionType);
            calendarDates.put(serviceId + ":" + value(header, row, "date") + ":" + calendarDate.getExceptionType(), calendarDate);
            store.saveEntity(calendarDate);
        });
        return calendarDates;
    }

    private Map<String, Trip> readTrips(ZipFile zip,
                                        GtfsDaoImpl store,
                                        Map<String, Route> routes,
                                        Set<String> activeServiceIds,
                                        boolean includeAllServices) throws IOException {
        Map<String, Trip> trips = new LinkedHashMap<>();
        readRows(zip, "trips.txt", (header, row) -> {
            String tripId = value(header, row, "trip_id");
            String routeId = value(header, row, "route_id");
            String serviceId = value(header, row, "service_id");
            if (!hasText(tripId) || !hasText(routeId) || !hasText(serviceId)) {
                return;
            }
            if (!includeAllServices && !activeServiceIds.contains(serviceId)) {
                return;
            }
            Route route = routes.get(routeId);
            if (route == null) {
                return;
            }
            Trip trip = new Trip();
            trip.setId(id(tripId));
            trip.setRoute(route);
            trip.setServiceId(id(serviceId));
            trip.setTripHeadsign(value(header, row, "trip_headsign"));
            trip.setDirectionId(value(header, row, "direction_id"));
            String shapeId = value(header, row, "shape_id");
            if (hasText(shapeId)) {
                trip.setShapeId(id(shapeId));
            }
            trips.put(tripId, trip);
            store.saveEntity(trip);
        });
        return trips;
    }

    private void readStopTimes(ZipFile zip,
                               GtfsDaoImpl store,
                               Map<String, Stop> stops,
                               Map<String, Trip> trips) throws IOException {
        int[] id = {1};
        readRows(zip, "stop_times.txt", (header, row) -> {
            String tripId = value(header, row, "trip_id");
            Trip trip = trips.get(tripId);
            Stop stop = stops.get(value(header, row, "stop_id"));
            Integer sequence = integer(value(header, row, "stop_sequence"));
            Integer arrival = time(value(header, row, "arrival_time"));
            Integer departure = time(value(header, row, "departure_time"));
            if (trip == null || stop == null || sequence == null || arrival == null || departure == null) {
                return;
            }
            StopTime stopTime = new StopTime();
            stopTime.setId(id[0]++);
            stopTime.setTrip(trip);
            stopTime.setStop(stop);
            stopTime.setStopSequence(sequence);
            stopTime.setArrivalTime(arrival);
            stopTime.setDepartureTime(departure);
            Double shapeDistance = decimal(value(header, row, "shape_dist_traveled"));
            if (shapeDistance != null) {
                stopTime.setShapeDistTraveled(shapeDistance);
            }
            store.saveEntity(stopTime);
        });
    }

    private void readRows(ZipFile zip, String fileName, BiConsumer<Map<String, Integer>, ArrayList<String>> rowConsumer)
            throws IOException {
        ZipEntry entry = zip.getEntry(fileName);
        if (entry == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            Map<String, Integer> header = header(parseCsvLine(headerLine));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    // Some GTFS names contain commas, so the line needs real CSV parsing.
                    rowConsumer.accept(header, parseCsvLine(line));
                }
            }
        }
    }

    private Map<String, Integer> header(ArrayList<String> columns) {
        Map<String, Integer> header = new HashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            if (index == 0 && column.startsWith("\uFEFF")) {
                column = column.substring(1);
            }
            header.put(column, index);
        }
        return header;
    }

    private ArrayList<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        values.add(value.toString());
        return values;
    }

    private Set<String> activeServiceIds(Map<String, ServiceCalendar> calendars,
                                         Map<String, ServiceCalendarDate> calendarDates,
                                         LocalDate date) {
        Set<String> activeServiceIds = new HashSet<>();
        calendars.forEach((serviceId, calendar) -> {
            if (activeOn(calendar, date)) {
                activeServiceIds.add(serviceId);
            }
        });
        calendarDates.values().forEach(calendarDate -> {
            if (!date(calendarDate.getDate()).equals(date)) {
                return;
            }
            String serviceId = calendarDate.getServiceId().getId();
            if (calendarDate.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_ADD) {
                activeServiceIds.add(serviceId);
            } else if (calendarDate.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_REMOVE) {
                activeServiceIds.remove(serviceId);
            }
        });
        return activeServiceIds;
    }

    private boolean activeOn(ServiceCalendar calendar, LocalDate date) {
        return !date.isBefore(date(calendar.getStartDate()))
                && !date.isAfter(date(calendar.getEndDate()))
                && switch (date.getDayOfWeek()) {
                    case MONDAY -> calendar.getMonday() == 1;
                    case TUESDAY -> calendar.getTuesday() == 1;
                    case WEDNESDAY -> calendar.getWednesday() == 1;
                    case THURSDAY -> calendar.getThursday() == 1;
                    case FRIDAY -> calendar.getFriday() == 1;
                    case SATURDAY -> calendar.getSaturday() == 1;
                    case SUNDAY -> calendar.getSunday() == 1;
                };
    }

    private Agency agency(Map<String, Agency> agencies, String agencyId) {
        if (hasText(agencyId) && agencies.containsKey(agencyId)) {
            return agencies.get(agencyId);
        }
        return agencies.values().stream().findFirst().orElse(null);
    }

    private AgencyAndId id(String id) {
        return new AgencyAndId(DEFAULT_AGENCY_ID, id);
    }

    private String value(Map<String, Integer> header, ArrayList<String> row, String column) {
        Integer index = header.get(column);
        return index == null || index >= row.size() ? "" : row.get(index);
    }

    private String text(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private Integer flag(Map<String, Integer> header, ArrayList<String> row, String column) {
        Integer value = integer(value(header, row, column));
        return value == null ? 0 : value;
    }

    private Integer integer(String value) {
        return hasText(value) ? Integer.parseInt(value) : null;
    }

    private Double decimal(String value) {
        return hasText(value) ? Double.parseDouble(value) : null;
    }

    private Integer time(String value) {
        if (!hasText(value)) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length != 3) {
            return null;
        }
        return Integer.parseInt(parts[0]) * 3600
                + Integer.parseInt(parts[1]) * 60
                + Integer.parseInt(parts[2]);
    }

    private ServiceDate serviceDate(String value) {
        if (!hasText(value) || value.length() != 8) {
            return null;
        }
        return new ServiceDate(
                Integer.parseInt(value.substring(0, 4)),
                Integer.parseInt(value.substring(4, 6)),
                Integer.parseInt(value.substring(6, 8))
        );
    }

    private LocalDate date(ServiceDate date) {
        if (date == null) {
            return LocalDate.MIN;
        }
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
