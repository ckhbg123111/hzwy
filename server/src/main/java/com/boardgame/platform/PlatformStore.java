package com.boardgame.platform;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class PlatformStore {

    final Map<String, User> users = new ConcurrentHashMap<>();
    final Map<String, Session> sessionsByToken = new ConcurrentHashMap<>();
    final Map<String, String> userIdByPhone = new ConcurrentHashMap<>();
    final Map<String, Party> parties = new ConcurrentHashMap<>();
    final Map<String, Room> rooms = new ConcurrentHashMap<>();
    final Map<String, GameInstance> games = new ConcurrentHashMap<>();
    final Map<String, MatchResult> matchResults = new ConcurrentHashMap<>();
    final Map<String, PlayerStats> statsByUserId = new ConcurrentHashMap<>();
    final Map<String, String> bannedDevices = new ConcurrentHashMap<>();

    enum UserKind {
        GUEST,
        PHONE
    }

    enum PartyState {
        IDLE,
        IN_QUEUE,
        IN_ROOM
    }

    enum RoomVisibility {
        PUBLIC,
        PRIVATE
    }

    enum RoomStatus {
        OPEN,
        IN_GAME,
        FINISHED
    }

    enum GameType {
        AZUL
    }

    static final class User {
        final String id;
        final Instant createdAt;
        UserKind kind;
        String displayName;
        String phoneNumber;
        String deviceId;

        User(String id, Instant createdAt, UserKind kind, String displayName, String deviceId) {
            this.id = id;
            this.createdAt = createdAt;
            this.kind = kind;
            this.displayName = displayName;
            this.deviceId = deviceId;
        }
    }

    record Session(String token, String userId, String deviceId, Instant issuedAt) {
    }

    record FriendRelation(String fromUserId, String toUserId, Instant createdAt) {
    }

    static final class Party {
        final String id;
        final Instant createdAt;
        String leaderUserId;
        PartyState state = PartyState.IDLE;
        String activeRoomId;
        final LinkedHashSet<String> memberUserIds = new LinkedHashSet<>();

        Party(String id, Instant createdAt, String leaderUserId) {
            this.id = id;
            this.createdAt = createdAt;
            this.leaderUserId = leaderUserId;
            this.memberUserIds.add(leaderUserId);
        }
    }

    static final class Room {
        final String id;
        final Instant createdAt;
        final GameType gameType;
        final int targetPlayers;
        final RoomVisibility visibility;
        final boolean allowFill;
        final List<String> playerIds = new ArrayList<>();
        String hostUserId;
        String sourcePartyId;
        RoomStatus status = RoomStatus.OPEN;
        String matchId;

        Room(
                String id,
                Instant createdAt,
                GameType gameType,
                int targetPlayers,
                RoomVisibility visibility,
                boolean allowFill,
                String hostUserId) {
            this.id = id;
            this.createdAt = createdAt;
            this.gameType = gameType;
            this.targetPlayers = targetPlayers;
            this.visibility = visibility;
            this.allowFill = allowFill;
            this.hostUserId = hostUserId;
        }
    }

    static final class GameInstance {
        final String id;
        final String roomId;
        final GameType gameType;
        final Instant createdAt;
        final List<GameActionLog> actionLogs = new ArrayList<>();
        final Map<String, Integer> consecutiveTimeouts = new HashMap<>();
        final Set<String> autoPlayUsers = new HashSet<>();
        Object rawState;
        String serializedState;
        String currentPlayerId;
        String phase;
        int stateVersion;
        int roundNumber;
        Instant deadlineAt;
        Instant finishedAt;
        boolean finished;

        GameInstance(String id, String roomId, GameType gameType, Instant createdAt) {
            this.id = id;
            this.roomId = roomId;
            this.gameType = gameType;
            this.createdAt = createdAt;
        }
    }

    record GameActionLog(
            Instant actedAt,
            String actorUserId,
            String clientSeq,
            String actionType,
            String payload,
            boolean automatic) {
    }

    record Placement(String userId, int place, int score, int completedRows) {
    }

    static final class MatchResult {
        final String id;
        final String roomId;
        final GameType gameType;
        final Instant finishedAt;
        final List<Placement> placements;
        final String summary;
        final Object finalState;

        MatchResult(
                String id,
                String roomId,
                GameType gameType,
                Instant finishedAt,
                List<Placement> placements,
                String summary,
                Object finalState) {
            this.id = id;
            this.roomId = roomId;
            this.gameType = gameType;
            this.finishedAt = finishedAt;
            this.placements = placements;
            this.summary = summary;
            this.finalState = finalState;
        }
    }

    static final class PlayerStats {
        final String userId;
        String displayName;
        int totalGames;
        int wins;
        int totalRank;
        final Deque<Integer> recentPlacements = new ArrayDeque<>();

        PlayerStats(String userId, String displayName) {
            this.userId = userId;
            this.displayName = displayName;
        }

        void recordPlacement(int place) {
            totalGames += 1;
            totalRank += place;
            if (place == 1) {
                wins += 1;
            }
            recentPlacements.addFirst(place);
            while (recentPlacements.size() > 20) {
                recentPlacements.removeLast();
            }
        }

        double averageRank() {
            return totalGames == 0 ? 0.0 : (double) totalRank / totalGames;
        }

        double firstPlaceRate() {
            return totalGames == 0 ? 0.0 : (double) wins / totalGames;
        }
    }
}
