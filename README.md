# Board Game Platform Prototype

This repository contains a prototype implementation of the Android board game platform MVP described in the design plan.

- `server/`: Spring Boot modular-monolith prototype with guest login, party, room, matchmaking, realtime play, Azul rules, rankings, and minimal ops endpoints.
- `android-app/`: Android Compose client prototype with lobby, room, live Azul match view, move selection, and result recap screens.
- `docs/`: supplementary architecture and protocol notes.

## Run the server

```bash
mvn -pl server spring-boot:run
```

The prototype server uses in-memory state, listens on `http://localhost:8080`, and exposes a raw WebSocket endpoint at `ws://localhost:8080/ws/realtime?token=...`.

## Current prototype tradeoffs

- The server keeps business state in memory instead of MySQL and Redis so the MVP loop can run locally without infrastructure.
- Party invitations auto-join in this internal prototype to keep the initial API surface compact.
- The Android app now includes a playable Azul UI flow, but it was not built in this environment because the Android SDK and Gradle wrapper are not available here.
