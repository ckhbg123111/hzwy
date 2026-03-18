package com.boardgame.platform;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings")
class RankingController {

    private final RankingService rankingService;

    RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/azul")
    List<RankingEntryView> azulRankings() {
        return rankingService.rankingsForAzul();
    }
}
