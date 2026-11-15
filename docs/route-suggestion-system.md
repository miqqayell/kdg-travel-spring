# Route Suggestion System

## Main Flow

The system is used in two places:

1. The travel group form calls `POST /api/groups/suggestions` to show route previews before the group is saved.
2. `TravelGroupService` recalculates the suggestions when the group is created or updated and stores the selected route as a `PublicTransportSelection`.

The preview request does not store anything. It only returns route options to the browser.
The actual saved route is created during the travel group create/update flow.
The form sends the selected route index, and the backend uses that index against a freshly
calculated list of route suggestions. If the selected index is missing or no longer valid, the first route is stored.

## Main Components

The route suggestion code is split across these main parts:

| Component | Responsibility |
| --- | --- |
| `TravelGroupRestController` | Exposes the suggestion endpoint used by the form. |
| `TransitRouteQuery` | Validates coordinates, meetup time, and transport mode. |
| `TransitRouteSuggestionService` | Loads GTFS data and coordinates the route planner. |
| `GtfsStaticCacheService` | Loads and caches parsed De Lijn GTFS data in memory per service date and prewarms upcoming dates after startup. |
| `GtfsStaticFeedFileStore` | Downloads and caches the raw GTFS zip file on disk. |
| `GtfsStaticFeedParser` | Streams the required GTFS text files from the zip into OneBusAway model objects. |
| `GtfsStaticData` | Provides indexed access to stops, trips, routes, calendars, and departures. |
| `TransitStopFinder` | Finds nearby origin and destination stops using the local GTFS stop index and straight-line distance estimates. |
| `StaticGtfsRoutePlanner` | Searches for direct and one-transfer public transport routes. |
| `SuggestedRouteMapper` | Converts internal planner results into response DTOs. |
| `PublicTransportSelectionService` | Converts a selected suggestion into a persisted route snapshot and serializes the full route with Jackson. |

## Data Sources

The system uses De Lijn's static GTFS feed. The feed contains scheduled public transport data such as stops,
routes, trips, stop times, service calendars, and route shapes. Route suggestions use the stop and stop-time
data for planning.

The raw GTFS zip file is stored in:

```text
<gtfs-cache-directory>/gtfs_transit.zip
```

By default, the cache directory is `.cache/delijn`. The zip file is considered fresh for 24 hours.
If the file is stale, the application tries to download a new version. If the download fails but an
older non-empty zip file exists, the older file is still used.

After the application starts, `GtfsStaticCacheService` starts a background prewarm for today and the
next thirteen days. Each date is parsed separately and cached in memory. Route suggestion requests
use the cache for the request's `meetupTime` service date. Dates outside the prewarmed window are parsed on demand.

The parser streams only the files needed for route selection:
`agency.txt`, `stops.txt`, `routes.txt`, `trips.txt`, `stop_times.txt`, `calendar.txt`,
and `calendar_dates.txt`. When a service date is known, it keeps only trips and stop times for
active services on that date. The application then wraps that parsed data in `GtfsStaticData`,
which adds the indexes needed by the planner.

Walking distances and walking durations inside route suggestions are local estimates based on GTFS stop coordinates.
`PedestrianRoutingService` is still used by the separate walking-distance endpoint,
but not by transit route suggestion planning.

## API Contract

The suggestion endpoint is:

```http
POST /api/groups/suggestions
Content-Type: application/json
```

Example request:

```json
{
  "startPointLatitude": 51.1711037,
  "startPointLongitude": 4.3672267,
  "endPointLatitude": 51.2651828,
  "endPointLongitude": 4.3650809,
  "transportationMode": "PUBLIC_TRANSPORT",
  "meetupTime": "2026-05-15T13:10:00"
}
```

The endpoint always returns HTTP `200` for valid requests.
The actual route status is returned in the `X-Transit-Suggestions-Status` response header and in the response body.

Possible statuses:

| Status | Meaning |
| --- | --- |
| `OK` | One or more routes were found. |
| `NO_ROUTE` | GTFS data was available, but no supported route matched the request. |
| `DATA_UNAVAILABLE` | GTFS data was unavailable. |

The response contains both `route` and `routes`. `route` is the first suggestion and exists for compatibility.
The form preview uses `routes` so it can show multiple alternatives.

## Planning Logic

The system searches for public transport routes that can be started after the requested `meetupTime`,
including the walk from the start point to the first stop.

The planner supports:

- Direct routes
- Routes with one transfer

The planner roughly follows these steps:

1. Determine the service date from `meetupTime`.
2. Find active GTFS service ids for that date using `calendar.txt` and `calendar_dates.txt`.
3. Find nearby origin stops and destination stops.
4. Estimate walking legs to those stops and filter by maximum walking distance.
5. Search active departures from each origin stop within a three-hour window.
6. Check whether each trip reaches one of the nearby destination stops directly.
7. Also check one-transfer options from stops further along the first trip.
8. Score all route candidates.
9. Keep the best candidate for each visible line sequence.
10. Return up to three route suggestions.

For transfer routes, the planner allows a short estimated walk between nearby stops.

## Route Scoring

Each route candidate is scored mainly by arrival time. Transfers and walking add extra penalty time:

final arrival time + transfer penalty + walking penalty

## Route Response

Each suggested route includes:

- Start and end stop details
- Primary line label
- Route summary, for example `9` or `9 -> 12`
- Headsign
- Expected departure and arrival times
- Line color
- Transfer count
- Walking minutes
- Ordered route legs

The route legs can be walking legs or transit legs. Walking legs include estimated distance, duration,
and simple point-to-point geometry. Transit legs include route, trip, stop, timing, headsign, and
straight stop-to-stop geometry from the boarding stop to the alighting stop.

## Persistence

When a public transport travel group is saved, the backend recalculates the suggestions and stores one selected route as `PublicTransportSelection`.

The stored selection contains summary columns such as:

- Line id
- Direction and destination
- Start and end stop names
- Departure and estimated arrival time
- Transfer count
- Walking minutes
- Route summary

It also stores `routeSnapshotJson`, which is a JSON snapshot of the selected
`SuggestedRoute`. `PublicTransportSelectionService` creates this snapshot with the
application's Jackson `ObjectMapper`. The snapshot contains the ordered legs and geometry,
so it can be used for map rendering without recalculating the route.

## Group Detail Map

The travel group detail page renders its Leaflet map from the saved group data.
The page reads `routeSnapshotJson` and draws each saved leg geometry.

## Error Handling

For the preview endpoint:

- Invalid input is rejected before route planning starts.
- GTFS loading failures return `DATA_UNAVAILABLE`.
- Missing routes return `NO_ROUTE`.
- Found routes return `OK`.

For travel group create/update:

- Route suggestion errors are logged.
- The travel group is still saved.
- No public transport selection is stored if no route can be calculated.

## Current Limitations

- Does not include realtime delays or cancellations.
- Supports direct and one-transfer routes only.

## Summary

The route suggestion system is a static GTFS-based public transport planner. It loads De Lijn schedule data, prewarms upcoming service dates, finds nearby stops, estimates walking legs locally, searches for direct and one-transfer trips, scores the possible routes, and returns up to three alternatives. The user can select one of these routes in the travel group form, and the selected route is stored as a snapshot when the group is saved.
