package com.boardgame.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games")
class GameController {

    private final AuthService authService;
    private final GamePlatformService gamePlatformService;

    GameController(AuthService authService, GamePlatformService gamePlatformService) {
        this.authService = authService;
        this.gamePlatformService = gamePlatformService;
    }

    @GetMapping("/{gameId}")
    GameSnapshotView getSnapshot(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String gameId) {
        authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return gamePlatformService.getSnapshot(gameId);
    }
}
