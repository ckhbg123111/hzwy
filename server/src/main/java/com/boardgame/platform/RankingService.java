package com.boardgame.platform;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class RankingService {

    private final PlatformStore store;

    RankingService(PlatformStore store) {
        this.store = store;
    }

    synchronized void recordResult(PlatformStore.MatchResult result) {
        for (PlatformStore.Placement placement : result.placements) {
            PlatformStore.User user = store.users.get(placement.userId());
            if (user == null) {
                continue;
            }
            PlatformStore.PlayerStats stats = store.statsByUserId.computeIfAbsent(
                    user.id,
                    ignored -> new PlatformStore.PlayerStats(user.id, user.displayName));
            stats.displayName = user.displayName;
            stats.recordPlacement(placement.place());
        }
    }

    synchronized List<RankingEntryView> rankingsForAzul() {
        return ApiViews.toRankingViews(store);
    }
}
