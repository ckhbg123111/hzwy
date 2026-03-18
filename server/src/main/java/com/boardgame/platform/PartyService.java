package com.boardgame.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class PartyService {

    private final PlatformStore store;
    private final AuthService authService;
    private final RealtimeGateway realtimeGateway;

    PartyService(PlatformStore store, AuthService authService, RealtimeGateway realtimeGateway) {
        this.store = store;
        this.authService = authService;
        this.realtimeGateway = realtimeGateway;
    }

    synchronized PartyView createParty(String userId) {
        if (findPartyByUserOrNull(userId) != null) {
            throw PlatformException.conflict("PARTY_ALREADY_EXISTS", "User is already in a party.");
        }
        ensureUserNotInOpenRoom(userId);
        PlatformStore.Party party = new PlatformStore.Party(
                "pty_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                Instant.now(),
                userId);
        store.parties.put(party.id, party);
        PartyView view = ApiViews.toPartyView(party, store);
        realtimeGateway.sendToUser(userId, "party.updated", view);
        return view;
    }

    synchronized PartyView inviteMember(String partyId, String leaderUserId, String targetUserId) {
        PlatformStore.Party party = getPartyOrThrow(partyId);
        if (!party.leaderUserId.equals(leaderUserId)) {
            throw PlatformException.forbidden("PARTY_LEADER_REQUIRED", "Only the party leader can invite players.");
        }
        if (party.memberUserIds.size() >= 4) {
            throw PlatformException.conflict("PARTY_FULL", "Party is already full.");
        }
        if (party.memberUserIds.contains(targetUserId)) {
            throw PlatformException.conflict("PARTY_MEMBER_EXISTS", "User is already in the party.");
        }

        authService.findUser(targetUserId);
        if (findPartyByUserOrNull(targetUserId) != null) {
            throw PlatformException.conflict("PARTY_TARGET_BUSY", "Target user is already in a party.");
        }
        ensureUserNotInOpenRoom(targetUserId);

        party.memberUserIds.add(targetUserId);
        PartyView view = ApiViews.toPartyView(party, store);
        realtimeGateway.sendToUser(targetUserId, "party.invite", view);
        publishPartyUpdate(party);
        return view;
    }

    synchronized PartyView leaveParty(String partyId, String userId) {
        PlatformStore.Party party = getPartyOrThrow(partyId);
        if (!party.memberUserIds.remove(userId)) {
            throw PlatformException.notFound("PARTY_MEMBER_NOT_FOUND", "User is not part of the party.");
        }
        if (party.memberUserIds.isEmpty()) {
            store.parties.remove(party.id);
            realtimeGateway.sendToUser(userId, "party.updated", null);
            return null;
        }
        if (userId.equals(party.leaderUserId)) {
            party.leaderUserId = party.memberUserIds.iterator().next();
        }
        PartyView view = ApiViews.toPartyView(party, store);
        realtimeGateway.sendToUser(userId, "party.updated", null);
        publishPartyUpdate(party);
        return view;
    }

    synchronized EntryGroup resolveEntryGroup(String userId) {
        PlatformStore.Party party = findPartyByUserOrNull(userId);
        if (party == null) {
            ensureUserNotInOpenRoom(userId);
            return new EntryGroup(null, List.of(userId));
        }
        if (!party.leaderUserId.equals(userId)) {
            throw PlatformException.forbidden(
                    "PARTY_LEADER_REQUIRED",
                    "Only the party leader can move the party into a room or queue.");
        }
        if (party.state != PlatformStore.PartyState.IDLE) {
            throw PlatformException.conflict(
                    "PARTY_BUSY",
                    "The party is already in queue or attached to a room.");
        }
        ensureUsersNotInOpenRoom(party.memberUserIds);
        return new EntryGroup(party.id, List.copyOf(party.memberUserIds));
    }

    synchronized PlatformStore.Party getPartyOrThrow(String partyId) {
        PlatformStore.Party party = store.parties.get(partyId);
        if (party == null) {
            throw PlatformException.notFound("PARTY_NOT_FOUND", "Party not found: " + partyId);
        }
        return party;
    }

    synchronized PlatformStore.Party findPartyByUserOrNull(String userId) {
        return store.parties.values().stream()
                .filter(party -> party.memberUserIds.contains(userId))
                .findFirst()
                .orElse(null);
    }

    synchronized void markQueued(String partyId) {
        PlatformStore.Party party = getPartyOrThrow(partyId);
        party.state = PlatformStore.PartyState.IN_QUEUE;
        publishPartyUpdate(party);
    }

    synchronized void clearQueue(String partyId) {
        PlatformStore.Party party = getPartyOrThrow(partyId);
        party.state = PlatformStore.PartyState.IDLE;
        publishPartyUpdate(party);
    }

    synchronized void attachRoom(String partyId, String roomId) {
        PlatformStore.Party party = getPartyOrThrow(partyId);
        party.state = PlatformStore.PartyState.IN_ROOM;
        party.activeRoomId = roomId;
        publishPartyUpdate(party);
    }

    synchronized void detachRoom(String partyId) {
        PlatformStore.Party party = getPartyOrThrow(partyId);
        party.state = PlatformStore.PartyState.IDLE;
        party.activeRoomId = null;
        publishPartyUpdate(party);
    }

    synchronized PartyView view(String partyId) {
        return ApiViews.toPartyView(getPartyOrThrow(partyId), store);
    }

    private void publishPartyUpdate(PlatformStore.Party party) {
        PartyView view = ApiViews.toPartyView(party, store);
        realtimeGateway.publishToTopic("parties/" + party.id, "party.updated", view);
        for (String memberUserId : new ArrayList<>(party.memberUserIds)) {
            realtimeGateway.sendToUser(memberUserId, "party.updated", view);
        }
    }

    private void ensureUserNotInOpenRoom(String userId) {
        boolean busy = store.rooms.values().stream()
                .anyMatch(room -> room.status != PlatformStore.RoomStatus.FINISHED && room.playerIds.contains(userId));
        if (busy) {
            throw PlatformException.conflict("USER_BUSY", "User is already in an active room.");
        }
    }

    private void ensureUsersNotInOpenRoom(Iterable<String> userIds) {
        for (String userId : userIds) {
            ensureUserNotInOpenRoom(userId);
        }
    }
}

record EntryGroup(String partyId, List<String> memberIds) {
}
