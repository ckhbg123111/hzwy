# Server Architecture

## What Is Implemented

- `server/` is a Spring Boot 3 modular-monolith prototype targeting the MVP loop for the board game platform.
- All runtime state is stored in memory so the prototype can be exercised without MySQL or Redis.
- The first supported title is `AZUL`, implemented as a server-authoritative game engine.

## Runtime Modules

- `AuthService`: guest login, phone binding, token validation, and device bans.
- `PartyService`: temporary 2-4 player party management with direct invite join for the prototype.
- `RoomService`: room creation, join/start flow, host transfer on leave, and match-room creation for queue results.
- `MatchmakingService`: exact-fit party queue for 2/3/4 player Azul matches.
- `GamePlatformService`: game lifecycle, action idempotency, timeout autoplay, snapshots, and result persistence.
- `AzulEngine`: official base Azul rules for tile taking, center rules, wall scoring, floor penalties, round refill, endgame bonuses, and placement ranking.
- `RankingService`: personal stats and ranking list derived from completed matches.
- `OpsController`: minimal prototype ops endpoints for lookup, device ban, and manual match termination.

## Storage Model

- Users, parties, rooms, active games, match results, and rankings live in `PlatformStore`.
- `serializedState` on each `GameInstance` holds a JSON copy of the authoritative game state for inspection/debugging.
- WebSocket subscriptions are managed in `WebSocketRealtimeGateway`.

## Prototype Tradeoffs

- No database persistence across restarts.
- No friend graph, spectator mode, replay, or production auth hardening yet.
- Party invitations auto-accept to keep the early API small.
- The room flow supports temporary parties; it does not implement long-lived guilds or teams.
