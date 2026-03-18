package com.boardgame.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class GamePlatformService {

    private final PlatformStore store;
    private final PartyService partyService;
    private final RankingService rankingService;
    private final RealtimeGateway realtimeGateway;
    private final ObjectMapper objectMapper;
    private final AzulEngine azulEngine;

    GamePlatformService(
            PlatformStore store,
            PartyService partyService,
            RankingService rankingService,
            RealtimeGateway realtimeGateway,
            ObjectMapper objectMapper,
            AzulEngine azulEngine) {
        this.store = store;
        this.partyService = partyService;
        this.rankingService = rankingService;
        this.realtimeGateway = realtimeGateway;
        this.objectMapper = objectMapper;
        this.azulEngine = azulEngine;
    }

    synchronized PlatformStore.GameInstance startGame(PlatformStore.Room room) {
        if (room.gameType != PlatformStore.GameType.AZUL) {
            throw PlatformException.badRequest("GAME_TYPE_UNSUPPORTED", "Only Azul is supported in this prototype.");
        }
        PlatformStore.GameInstance game = new PlatformStore.GameInstance(
                "match_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                room.id,
                room.gameType,
                Instant.now());
        AzulEngine.AzulGameState state = azulEngine.newGame(room.playerIds);
        game.rawState = state;
        game.stateVersion = 1;
        syncMutableState(game, state, 30);
        store.games.put(game.id, game);
        publishSnapshot(game);
        return game;
    }

    GameSnapshotView getSnapshot(String gameId) {
        PlatformStore.GameInstance game = getGameOrThrow(gameId);
        synchronized (game) {
            return snapshotOf(game, stateOf(game));
        }
    }

    MatchResultView getMatchResult(String matchId) {
        PlatformStore.MatchResult result = store.matchResults.get(matchId);
        if (result == null) {
            throw PlatformException.notFound("MATCH_RESULT_NOT_FOUND", "Match result not found: " + matchId);
        }
        return ApiViews.toMatchResultView(result, store);
    }

    void submitAction(String userId, GameActionRequest request, boolean automatic) {
        if (request == null || request.gameId() == null || request.gameId().isBlank()) {
            throw PlatformException.badRequest("GAME_ACTION_INVALID", "A target gameId is required.");
        }
        if (!"TAKE_TILES".equals(request.actionType())) {
            throw PlatformException.badRequest("GAME_ACTION_TYPE_INVALID", "Only TAKE_TILES is supported.");
        }

        PlatformStore.GameInstance game = getGameOrThrow(request.gameId());
        synchronized (game) {
            if (game.finished) {
                throw PlatformException.conflict("GAME_FINISHED", "The match has already finished.");
            }
            if (!automatic && !Objects.equals(game.currentPlayerId, userId)) {
                throw PlatformException.forbidden("GAME_TURN_INVALID", "It is not this player's turn.");
            }
            if (request.clientSeq() != null && game.actionLogs.stream()
                    .anyMatch(log -> Objects.equals(log.actorUserId(), userId)
                            && Objects.equals(log.clientSeq(), request.clientSeq()))) {
                realtimeGateway.sendToUser(
                        userId,
                        "game.actionAck",
                        new GameActionAckView(game.id, request.clientSeq(), game.stateVersion, automatic));
                return;
            }

            AzulEngine.AzulGameState state = stateOf(game);
            AzulEngine.AzulAction action = treeToAction(request.payload());
            AzulEngine.AzulTurnResult result = azulEngine.applyAction(state, userId, action);
            game.actionLogs.add(new PlatformStore.GameActionLog(
                    Instant.now(),
                    userId,
                    request.clientSeq(),
                    request.actionType(),
                    safeJson(request.payload()),
                    automatic));
            if (!automatic) {
                game.consecutiveTimeouts.remove(userId);
            }
            if (result.roundAdvanced()) {
                game.consecutiveTimeouts.clear();
                game.autoPlayUsers.clear();
            }

            game.stateVersion += 1;
            syncMutableState(game, state, result.nextTurnSeconds());

            realtimeGateway.sendToUser(
                    userId,
                    "game.actionAck",
                    new GameActionAckView(game.id, request.clientSeq(), game.stateVersion, automatic));
            publishSnapshot(game);

            if (result.matchResult() != null || state.finished) {
                PlatformStore.MatchResult matchResult = azulEngine.forceFinish(
                        game.id,
                        game.roomId,
                        state,
                        result.matchResult() != null
                                ? result.matchResult().summary
                                : "Azul match finished.");
                finalizeMatch(game, matchResult);
            }
        }
    }

    MatchResultView terminateMatch(String matchId, String summary) {
        PlatformStore.GameInstance game = getGameOrThrow(matchId);
        synchronized (game) {
            if (game.finished) {
                PlatformStore.MatchResult existing = store.matchResults.get(matchId);
                return existing == null ? null : ApiViews.toMatchResultView(existing, store);
            }
            AzulEngine.AzulGameState state = stateOf(game);
            PlatformStore.MatchResult result = azulEngine.forceFinish(game.id, game.roomId, state, summary);
            game.stateVersion += 1;
            syncMutableState(game, state, 0);
            publishSnapshot(game);
            finalizeMatch(game, result);
            return ApiViews.toMatchResultView(result, store);
        }
    }

    @Scheduled(fixedDelay = 5000L)
    void autoPlayExpiredTurns() {
        List<String> expiredGames = store.games.values().stream()
                .filter(game -> !game.finished && game.deadlineAt != null && !game.deadlineAt.isAfter(Instant.now()))
                .map(game -> game.id)
                .toList();
        for (String gameId : expiredGames) {
            autoPlay(gameId);
        }
    }

    private void autoPlay(String gameId) {
        PlatformStore.GameInstance game = getGameOrThrow(gameId);
        synchronized (game) {
            if (game.finished || game.deadlineAt == null || game.deadlineAt.isAfter(Instant.now())) {
                return;
            }
            String currentPlayerId = game.currentPlayerId;
            game.consecutiveTimeouts.merge(currentPlayerId, 1, Integer::sum);
            if (game.consecutiveTimeouts.getOrDefault(currentPlayerId, 0) >= 2) {
                game.autoPlayUsers.add(currentPlayerId);
            }
            AzulEngine.AzulAction action = azulEngine.defaultAction(stateOf(game));
            submitAction(
                    currentPlayerId,
                    new GameActionRequest(
                            gameId,
                            "TAKE_TILES",
                            objectMapper.valueToTree(action),
                            "timeout-" + Instant.now().toEpochMilli()),
                    true);
        }
    }

    private void finalizeMatch(PlatformStore.GameInstance game, PlatformStore.MatchResult result) {
        game.finished = true;
        game.finishedAt = result.finishedAt;
        game.phase = "FINISHED";
        game.currentPlayerId = null;
        game.deadlineAt = null;
        store.matchResults.put(result.id, result);
        rankingService.recordResult(result);

        PlatformStore.Room room = store.rooms.get(game.roomId);
        if (room != null) {
            room.status = PlatformStore.RoomStatus.FINISHED;
            room.matchId = result.id;
            RoomView roomView = ApiViews.toRoomView(room, store);
            realtimeGateway.publishToTopic("rooms/" + room.id, "room.updated", roomView);
            for (String playerId : new ArrayList<>(room.playerIds)) {
                realtimeGateway.sendToUser(playerId, "room.updated", roomView);
            }
        }

        List<String> partiesToDetach = store.parties.values().stream()
                .filter(party -> Objects.equals(game.roomId, party.activeRoomId))
                .map(party -> party.id)
                .toList();
        partiesToDetach.forEach(partyService::detachRoom);

        MatchResultView resultView = ApiViews.toMatchResultView(result, store);
        realtimeGateway.publishToTopic("games/" + game.id, "game.result", resultView);
        for (PlatformStore.Placement placement : result.placements) {
            realtimeGateway.sendToUser(placement.userId(), "game.result", resultView);
        }
    }

    private void syncMutableState(PlatformStore.GameInstance game, AzulEngine.AzulGameState state, int nextTurnSeconds) {
        game.rawState = state;
        game.serializedState = safeJson(state);
        game.roundNumber = state.roundNumber;
        game.finished = state.finished || game.finished;
        game.phase = state.finished ? "FINISHED" : "SELECT_TILES";
        game.currentPlayerId = state.finished ? null : state.players.get(state.currentPlayerIndex).userId;
        game.deadlineAt = state.finished ? null : Instant.now().plusSeconds(Math.max(nextTurnSeconds, 1));
    }

    private GameSnapshotView snapshotOf(PlatformStore.GameInstance game, AzulEngine.AzulGameState state) {
        return new GameSnapshotView(
                game.id,
                game.gameType.name(),
                game.phase,
                game.stateVersion,
                game.currentPlayerId,
                game.deadlineAt,
                azulEngine.publicView(state));
    }

    private void publishSnapshot(PlatformStore.GameInstance game) {
        GameSnapshotView snapshot = snapshotOf(game, stateOf(game));
        realtimeGateway.publishToTopic("games/" + game.id, "game.snapshot", snapshot);
        PlatformStore.Room room = store.rooms.get(game.roomId);
        if (room != null) {
            for (String playerId : room.playerIds) {
                realtimeGateway.sendToUser(playerId, "game.snapshot", snapshot);
            }
        }
    }

    private PlatformStore.GameInstance getGameOrThrow(String gameId) {
        PlatformStore.GameInstance game = store.games.get(gameId);
        if (game == null) {
            throw PlatformException.notFound("GAME_NOT_FOUND", "Match not found: " + gameId);
        }
        return game;
    }

    private AzulEngine.AzulGameState stateOf(PlatformStore.GameInstance game) {
        return (AzulEngine.AzulGameState) game.rawState;
    }

    private AzulEngine.AzulAction treeToAction(JsonNode payload) {
        try {
            return objectMapper.treeToValue(payload, AzulEngine.AzulAction.class);
        } catch (JsonProcessingException exception) {
            throw PlatformException.badRequest("GAME_ACTION_PAYLOAD_INVALID", exception.getOriginalMessage());
        }
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize state.", exception);
        }
    }
}

record GameActionRequest(String gameId, String actionType, JsonNode payload, String clientSeq) {
}

record GameActionAckView(String gameId, String clientSeq, int stateVersion, boolean automatic) {
}

record GameActionRejectedView(String gameId, String clientSeq, String reason) {
}

record GameSnapshotView(
        String gameId,
        String gameType,
        String phase,
        int stateVersion,
        String currentPlayerId,
        Instant deadlineAt,
        AzulEngine.AzulPublicStateView state) {
}
