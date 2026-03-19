# Board Game Platform Prototype

This repository contains a prototype implementation of the Android board game platform MVP described in the design plan.

- `server/`: Spring Boot modular-monolith prototype with guest login, party, room, matchmaking, realtime play, Azul rules, rankings, and minimal ops endpoints.
- `android-app/`: Android Compose client prototype with lobby, room, live Azul match view, move selection, and result recap screens.
- `docs/`: supplementary architecture and protocol notes.

## Run the server

```bash
mvn -pl server spring-boot:run
```

The prototype server uses in-memory state, listens on `http://localhost:8079`, and exposes a raw WebSocket endpoint at `ws://localhost:8079/ws/realtime?token=...`.

## Run the server with Docker

The backend can now be built and deployed with Docker by running the root `deploy.sh` script:

```bash
cp example.env .env
chmod +x deploy.sh
./deploy.sh
```

The script automatically loads variables from the root `.env` file, builds the image from `server/Dockerfile`, removes any existing backend container with the same name, and starts a fresh container in the background.

Common environment variables:

- `IMAGE_NAME`: Docker image name. Default: `boardgame-platform-server`
- `CONTAINER_NAME`: Docker container name. Default: `boardgame-platform-server`
- `HOST_PORT`: host port exposed by Docker. Default: `8079`
- `BOARDGAME_OPS_TOKEN`: overrides the default ops token from `application.yml`
- `SPRING_PROFILES_ACTIVE`: optional Spring profile
- `JAVA_OPTS`: optional JVM flags, such as `-Xms256m -Xmx512m`

Example:

```bash
cp example.env .env
# edit .env as needed
./deploy.sh
```

On Windows, run the script from Git Bash or WSL.

## Current prototype tradeoffs

- The server keeps business state in memory instead of MySQL and Redis so the MVP loop can run locally without infrastructure.
- Party invitations auto-join in this internal prototype to keep the initial API surface compact.
- The Android app now includes a playable Azul UI flow, but it was not built in this environment because the Android SDK and Gradle wrapper are not available here.
