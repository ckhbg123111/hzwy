package com.boardgame.platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class RoomService {

    private final PlatformStore store;
    private final PartyService partyService;
    private final RealtimeGateway realtimeGateway;
    private final GamePlatformService gamePlatformService;

    RoomService(
            PlatformStore store,
            PartyService partyService,
            RealtimeGateway realtimeGateway,
            GamePlatformService gamePlatformService) {
        this.store = store;
        this.partyService = partyService;
        this.realtimeGateway = realtimeGateway;
        this.gamePlatformService = gamePlatformService;
    }

    synchronized RoomView createRoom(String requesterUserId, CreateRoomRequest request) {
        validateTargetPlayers(request.targetPlayers());
        EntryGroup group = partyService.resolveEntryGroup(requesterUserId);
        if (group.memberIds().size() > request.targetPlayers()) {
            throw PlatformException.conflict("ROOM_CAPACITY", "Party size is larger than the room capacity.");
        }

        PlatformStore.Room room = new PlatformStore.Room(
                "room_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                Instant.now(),
                request.gameType(),
                request.targetPlayers(),
                request.visibility(),
                request.allowFill(),
                requesterUserId);
        room.playerIds.addAll(group.memberIds());
        room.sourcePartyId = group.partyId();
        store.rooms.put(room.id, room);
        if (group.partyId() != null) {
            partyService.attachRoom(group.partyId(), room.id);
        }
        publishRoomLifecycle("room.created", room);
        return ApiViews.toRoomView(room, store);
    }

    synchronized RoomView joinRoom(String requesterUserId, String roomId) {
        PlatformStore.Room room = getRoomOrThrow(roomId);
        if (room.status != PlatformStore.RoomStatus.OPEN) {
            throw PlatformException.conflict("ROOM_NOT_OPEN", "The room is not accepting new players.");
        }

        EntryGroup group = partyService.resolveEntryGroup(requesterUserId);
        int remainingSeats = room.targetPlayers - room.playerIds.size();
        if (group.memberIds().size() > remainingSeats) {
            throw PlatformException.conflict("ROOM_FULL", "Not enough seats are available.");
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>(room.playerIds);
        merged.addAll(group.memberIds());
        room.playerIds.clear();
        room.playerIds.addAll(merged);
        if (group.partyId() != null) {
            partyService.attachRoom(group.partyId(), room.id);
        }
        publishRoomLifecycle("room.updated", room);
        return ApiViews.toRoomView(room, store);
    }

    synchronized RoomView leaveRoom(String requesterUserId, String roomId) {
        PlatformStore.Room room = getRoomOrThrow(roomId);
        if (room.status != PlatformStore.RoomStatus.OPEN) {
            throw PlatformException.conflict("ROOM_LOCKED", "Players cannot leave after a match starts.");
        }
        if (!room.playerIds.remove(requesterUserId)) {
            throw PlatformException.notFound("ROOM_PLAYER_NOT_FOUND", "User is not in the room.");
        }
        PlatformStore.Party party = partyService.findPartyByUserOrNull(requesterUserId);
        if (party != null && room.id.equals(party.activeRoomId)) {
            partyService.detachRoom(party.id);
        }
        if (requesterUserId.equals(room.hostUserId) && !room.playerIds.isEmpty()) {
            room.hostUserId = room.playerIds.get(0);
        }
        if (room.playerIds.isEmpty()) {
            store.rooms.remove(room.id);
            return null;
        }
        publishRoomLifecycle("room.updated", room);
        return ApiViews.toRoomView(room, store);
    }

    synchronized RoomView startRoom(String requesterUserId, String roomId) {
        PlatformStore.Room room = getRoomOrThrow(roomId);
        if (!room.hostUserId.equals(requesterUserId)) {
            throw PlatformException.forbidden("ROOM_HOST_REQUIRED", "Only the room host can start the game.");
        }
        if (room.status != PlatformStore.RoomStatus.OPEN) {
            throw PlatformException.conflict("ROOM_ALREADY_STARTED", "Room has already started.");
        }
        if (room.playerIds.size() < 2) {
            throw PlatformException.badRequest("ROOM_NOT_READY", "At least two players are required to start.");
        }

        room.status = PlatformStore.RoomStatus.IN_GAME;
        PlatformStore.GameInstance gameInstance = gamePlatformService.startGame(room);
        room.matchId = gameInstance.id;
        publishRoomLifecycle("room.updated", room);
        for (String playerId : new ArrayList<>(room.playerIds)) {
            realtimeGateway.sendToUser(
                    playerId,
                    "match.found",
                    new MatchFoundView(room.id, gameInstance.id, room.gameType.name()));
        }
        return ApiViews.toRoomView(room, store);
    }

    synchronized RoomView createMatchedRoom(List<MatchmakingTicket> tickets, PlatformStore.GameType gameType, int targetPlayers) {
        List<String> orderedPlayers = new ArrayList<>();
        String hostUserId = null;
        for (MatchmakingTicket ticket : tickets) {
            if (hostUserId == null) {
                hostUserId = ticket.memberIds().get(0);
            }
            orderedPlayers.addAll(ticket.memberIds());
        }

        PlatformStore.Room room = new PlatformStore.Room(
                "room_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                Instant.now(),
                gameType,
                targetPlayers,
                PlatformStore.RoomVisibility.PUBLIC,
                true,
                hostUserId);
        room.playerIds.addAll(orderedPlayers);
        store.rooms.put(room.id, room);
        for (MatchmakingTicket ticket : tickets) {
            if (ticket.partyId() != null) {
                partyService.attachRoom(ticket.partyId(), room.id);
            }
        }
        room.status = PlatformStore.RoomStatus.IN_GAME;
        PlatformStore.GameInstance gameInstance = gamePlatformService.startGame(room);
        room.matchId = gameInstance.id;
        publishRoomLifecycle("room.created", room);
        publishRoomLifecycle("room.updated", room);
        for (String playerId : orderedPlayers) {
            realtimeGateway.sendToUser(
                    playerId,
                    "match.found",
                    new MatchFoundView(room.id, gameInstance.id, room.gameType.name()));
        }
        return ApiViews.toRoomView(room, store);
    }

    synchronized RoomView getRoomView(String roomId) {
        return ApiViews.toRoomView(getRoomOrThrow(roomId), store);
    }

    synchronized void markFinished(String roomId) {
        PlatformStore.Room room = getRoomOrThrow(roomId);
        room.status = PlatformStore.RoomStatus.FINISHED;
        if (room.sourcePartyId != null) {
            partyService.detachRoom(room.sourcePartyId);
        }
        publishRoomLifecycle("room.updated", room);
    }

    synchronized PlatformStore.Room getRoomOrThrow(String roomId) {
        PlatformStore.Room room = store.rooms.get(roomId);
        if (room == null) {
            throw PlatformException.notFound("ROOM_NOT_FOUND", "Room not found: " + roomId);
        }
        return room;
    }

    private void publishRoomLifecycle(String eventType, PlatformStore.Room room) {
        RoomView view = ApiViews.toRoomView(room, store);
        realtimeGateway.publishToTopic("rooms/" + room.id, eventType, view);
        for (String userId : new ArrayList<>(room.playerIds)) {
            realtimeGateway.sendToUser(userId, eventType, view);
        }
    }

    private void validateTargetPlayers(int targetPlayers) {
        if (targetPlayers < 2 || targetPlayers > 4) {
            throw PlatformException.badRequest("ROOM_PLAYERS_INVALID", "Room capacity must be between 2 and 4.");
        }
    }
}

record CreateRoomRequest(
        @NotNull PlatformStore.GameType gameType,
        @Min(2) @Max(4) int targetPlayers,
        @NotNull PlatformStore.RoomVisibility visibility,
        boolean allowFill) {
}

record MatchFoundView(String roomId, String matchId, String gameType) {
}
