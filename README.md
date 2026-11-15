# KdG Travel — Spring Backend (Integration 4)

A web application that helps KdG students travel together to events. Users can
discover activities, form travel groups, share live locations on the map,
invite friends, and get notified of buddy matches based on overlapping plans.

## Team

* Mikayel Ghalumyan (team lead)
* Teammate 1
* Teammate 2
* Teammate 3
* Teammate 4

## Features

* **Activities & travel groups** — create, discover and join events; form
  travel groups per activity with a chosen transport mode and route.
* **Public-transport routing** — suggested De Lijn routes (GTFS static +
  realtime), saved per group with route snapshot for later display.
* **Live location radar** — group members opt in to share GPS; everyone on the
  group page sees one another's avatars on the route map in real time.
* **Friends & social graph** — friend requests, accept/decline, block, and a
  per-user "frequent travel buddies" shortlist on the profile page.
* **Suggestions & invites** — on an activity, see friends you could invite to
  travel together; one-click sends them a notification linking to the event.
* **Buddy-match notifications** — when a friend joins an upcoming activity you
  are already on (or vice versa), both sides get notified automatically.
* **Notifications** — bell dropdown polled from `/api/notifications` with
  context-aware deep links (activity, group, profile).
* **Admin dashboard** — verification, reports, user moderation, analytics.

## Tech stack

* **Java 21**, **Spring Boot 4** (Web MVC, Data JPA, Security, OAuth2 client,
  WebSocket)
* **Thymeleaf** templates + Bootstrap 5
* **PostgreSQL** (via Docker for dev)
* **Leaflet** for maps, **OSRM** for pedestrian/bike routing,
  **OneBusAway GTFS** for transit data, **De Lijn API** for realtime

## Prerequisites

* Java 21
* Docker (for the dev PostgreSQL container)
* A Google OAuth client (set `client-id` and `client-secret` for the `google`
  registration in `application-{dev,prod}.yaml` — sign-in is OIDC-only)

## Build

```bash
./gradlew clean build
```

## Run — development profile

1. Start the database:
   ```bash
   docker compose up -d
   ```
2. Run the application:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

App: <http://localhost:8080> · Dev database:

| | |
|-|-|
| Host | `localhost` |
| Port | `5435` |
| Database | `integration4` |
| Username / password | `spring` / `spring` |
| JDBC URL | `jdbc:postgresql://localhost:5435/integration4` |

On first boot, `DataSeeder` populates a demo user and a couple of activities.

## Run — production profile

```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## Tests

```bash
./gradlew test
```

## Stop the dev database

```bash
docker compose down
```

Reset the database (also deletes the data volume):

```bash
docker compose down -v
```

## Project layout

```
src/main/java/be/kdg/integration4/spring_backend/
├── config/           Spring config (security, websocket, seeding, startup hooks)
├── controller/
│   ├── api/          REST endpoints (JSON, used by JS)
│   └── web/          Thymeleaf-backed page controllers
├── domain/           JPA entities + enums
├── repository/       Spring Data JPA repositories
├── service/          Business logic
│   ├── routing/      OSRM pedestrian routing
│   └── transit/      GTFS + De Lijn transit suggestions
└── mappers/          Entity ↔ DTO mappers
```

Templates live in `src/main/resources/templates/`, static assets in
`src/main/resources/static/`.
