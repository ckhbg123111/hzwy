package com.boardgame.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class MatchmakingService {

    private final PartyService partyService;
    private final RoomService roomService;
    private final RealtimeGateway realtimeGateway;
    private final List<MatchmakingTicket> waitingTickets = new ArrayList<>();

    MatchmakingService(PartyService partyService, RoomService roomService, RealtimeGateway realtimeGateway) {
        this.partyService = partyService;
        this.roomService = roomService;
        this.realtimeGateway = realtimeGateway;
    }

    synchronized QueueStatusView enqueueParty(
            String partyId,
            String leaderUserId,
            PlatformStore.GameType gameType,
            int targetPlayers) {
        PlatformStore.Party party = partyService.getPartyOrThrow(partyId);
        if (!party.leaderUserId.equals(leaderUserId)) {
            throw PlatformException.forbidden("PARTY_LEADER_REQUIRED", "Only the party leader can queue the party.");
        }
        if (party.state != PlatformStore.PartyState.IDLE) {
            throw PlatformException.conflict("PARTY_BUSY", "Party is already busy.");
        }
        if (party.memberUserIds.size() > targetPlayers) {
            throw PlatformException.conflict("MATCH_CAPACITY", "Party size exceeds the selected match size.");
        }
        boolean alreadyQueued = waitingTickets.stream().anyMatch(ticket -> partyId.equals(ticket.partyId()));
        if (alreadyQueued) {
            throw PlatformException.conflict("QUEUE_EXISTS", "Party is already in the queue.");
        }

        MatchmakingTicket ticket = new MatchmakingTicket(
                "queue_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                party.id,
                List.copyOf(party.memberUserIds),
                gameType,
                targetPlayers,
                Instant.now());
        waitingTickets.add(ticket);
        waitingTickets.sort(Comparator.comparing(MatchmakingTicket::queuedAt));
        partyService.markQueued(party.id);

        QueueStatusView waiting = new QueueStatusView("WAITING", party.id, null, null, "Queued for matchmaking.");
        party.memberUserIds.forEach(memberId -> realtimeGateway.sendToUser(memberId, "queue.status", waiting));

        RoomView matchedRoom = tryMatch(gameType, targetPlayers);
        if (matchedRoom == null) {
            return waiting;
        }

        QueueStatusView matched = new QueueStatusView(
                "MATCHED",
                party.id,
                matchedRoom.id(),
                matchedRoom.matchId(),
                "Match found.");
        party.memberUserIds.forEach(memberId -> realtimeGateway.sendToUser(memberId, "queue.status", matched));
        return matched;
    }

    private RoomView tryMatch(PlatformStore.GameType gameType, int targetPlayers) {
        List<MatchmakingTicket> candidates = waitingTickets.stream()
                .filter(ticket -> ticket.gameType() == gameType && ticket.targetPlayers() == targetPlayers)
                .sorted(Comparator.comparing(MatchmakingTicket::queuedAt))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<MatchmakingTicket> matched = new ArrayList<>();
        int seats = 0;
        for (MatchmakingTicket ticket : candidates) {
            int size = ticket.memberIds().size();
            if (seats + size > targetPlayers) {
                continue;
            }
            matched.add(ticket);
            seats += size;
            if (seats == targetPlayers) {
                break;
            }
        }
        if (seats != targetPlayers) {
            return null;
        }

        waitingTickets.removeAll(matched);
        RoomView room = roomService.createMatchedRoom(matched, gameType, targetPlayers);
        for (MatchmakingTicket ticket : matched) {
            QueueStatusView matchedView = new QueueStatusView(
                    "MATCHED",
                    ticket.partyId(),
                    room.id(),
                    room.matchId(),
                    "Match found.");
            ticket.memberIds().forEach(memberId -> realtimeGateway.sendToUser(memberId, "queue.status", matchedView));
        }
        return room;
    }
}

record MatchmakingTicket(
        String ticketId,
        String partyId,
        List<String> memberIds,
        PlatformStore.GameType gameType,
        int targetPlayers,
        Instant queuedAt) {
}
