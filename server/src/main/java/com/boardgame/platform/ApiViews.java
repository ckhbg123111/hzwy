package com.boardgame.platform;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class ApiViews {

    private ApiViews() {
    }

    static UserView toUserView(PlatformStore.User user) {
        return new UserView(
                user.id,
                user.displayName,
                user.kind.name(),
                user.phoneNumber,
                user.createdAt);
    }

    static PartyView toPartyView(PlatformStore.Party party, PlatformStore store) {
        List<UserView> members = party.memberUserIds.stream()
                .map(store.users::get)
                .filter(java.util.Objects::nonNull)
                .map(ApiViews::toUserView)
                .toList();
        return new PartyView(
                party.id,
                party.leaderUserId,
                members,
                party.state.name(),
                party.activeRoomId,
                party.createdAt);
    }

    static RoomView toRoomView(PlatformStore.Room room, PlatformStore store) {
        List<UserView> players = room.playerIds.stream()
                .map(store.users::get)
                .filter(java.util.Objects::nonNull)
                .map(ApiViews::toUserView)
                .toList();
        return new RoomView(
                room.id,
                room.hostUserId,
                room.gameType.name(),
                room.targetPlayers,
                room.visibility.name(),
                room.allowFill,
                room.status.name(),
                players,
                room.sourcePartyId,
                room.matchId,
                room.createdAt);
    }

    static MatchResultView toMatchResultView(PlatformStore.MatchResult result, PlatformStore store) {
        List<PlacementView> placements = result.placements.stream()
                .map(placement -> new PlacementView(
                        placement.userId(),
                        store.users.containsKey(placement.userId())
                                ? store.users.get(placement.userId()).displayName
                                : placement.userId(),
                        placement.place(),
                        placement.score(),
                        placement.completedRows()))
                .toList();
        return new MatchResultView(
                result.id,
                result.roomId,
                result.gameType.name(),
                placements,
                result.finishedAt,
                result.summary,
                (AzulEngine.AzulPublicStateView) result.finalState);
    }

    static List<RankingEntryView> toRankingViews(PlatformStore store) {
        return store.statsByUserId.values().stream()
                .sorted(Comparator.comparingInt((PlatformStore.PlayerStats stats) -> stats.wins).reversed()
                        .thenComparing(PlatformStore.PlayerStats::firstPlaceRate, Comparator.reverseOrder())
                        .thenComparing(PlatformStore.PlayerStats::averageRank)
                        .thenComparingInt((PlatformStore.PlayerStats stats) -> stats.totalGames).reversed())
                .map(stats -> new RankingEntryView(
                        stats.userId,
                        stats.displayName,
                        stats.totalGames,
                        stats.wins,
                        stats.firstPlaceRate(),
                        stats.averageRank(),
                        stats.recentPlacements.stream().collect(Collectors.toList())))
                .toList();
    }
}

record UserView(
        String id,
        String displayName,
        String kind,
        String phoneNumber,
        Instant createdAt) {
}

record PartyView(
        String id,
        String leaderUserId,
        List<UserView> members,
        String state,
        String activeRoomId,
        Instant createdAt) {
}

record RoomView(
        String id,
        String hostUserId,
        String gameType,
        int targetPlayers,
        String visibility,
        boolean allowFill,
        String status,
        List<UserView> players,
        String sourcePartyId,
        String matchId,
        Instant createdAt) {
}

record PlacementView(
        String userId,
        String displayName,
        int place,
        int score,
        int completedRows) {
}

record MatchResultView(
        String matchId,
        String roomId,
        String gameType,
        List<PlacementView> placements,
        Instant finishedAt,
        String summary,
        AzulEngine.AzulPublicStateView finalState) {
}

record RankingEntryView(
        String userId,
        String displayName,
        int totalGames,
        int wins,
        double firstPlaceRate,
        double averageRank,
        List<Integer> recentPlacements) {
}
