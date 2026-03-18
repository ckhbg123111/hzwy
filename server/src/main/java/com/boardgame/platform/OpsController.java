package com.boardgame.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops")
class OpsController {

    private final String opsToken;
    private final PlatformStore store;
    private final AuthService authService;
    private final RoomService roomService;
    private final GamePlatformService gamePlatformService;

    OpsController(
            @Value("${boardgame.ops-token}") String opsToken,
            PlatformStore store,
            AuthService authService,
            RoomService roomService,
            GamePlatformService gamePlatformService) {
        this.opsToken = opsToken;
        this.store = store;
        this.authService = authService;
        this.roomService = roomService;
        this.gamePlatformService = gamePlatformService;
    }

    @GetMapping("/users/{userId}")
    UserView getUser(
            @RequestHeader("X-Ops-Token") String token,
            @PathVariable String userId) {
        requireOpsToken(token);
        return ApiViews.toUserView(authService.findUser(userId));
    }

    @GetMapping("/rooms/{roomId}")
    RoomView getRoom(
            @RequestHeader("X-Ops-Token") String token,
            @PathVariable String roomId) {
        requireOpsToken(token);
        return roomService.getRoomView(roomId);
    }

    @GetMapping("/matches/{matchId}")
    Object getMatch(
            @RequestHeader("X-Ops-Token") String token,
            @PathVariable String matchId) {
        requireOpsToken(token);
        PlatformStore.MatchResult result = store.matchResults.get(matchId);
        if (result != null) {
            return ApiViews.toMatchResultView(result, store);
        }
        PlatformStore.GameInstance game = store.games.get(matchId);
        if (game != null) {
            return gamePlatformService.getSnapshot(matchId);
        }
        throw PlatformException.notFound("OPS_MATCH_NOT_FOUND", "Match not found: " + matchId);
    }

    @PostMapping("/bans/devices/{deviceId}")
    SystemNoticeView banDevice(
            @RequestHeader("X-Ops-Token") String token,
            @PathVariable String deviceId,
            @Valid @RequestBody BanDeviceRequest request) {
        requireOpsToken(token);
        authService.banDevice(deviceId, request.reason());
        return new SystemNoticeView("DEVICE_BANNED", deviceId);
    }

    @PostMapping("/matches/{matchId}/terminate")
    MatchResultView terminateMatch(
            @RequestHeader("X-Ops-Token") String token,
            @PathVariable String matchId,
            @Valid @RequestBody TerminateMatchRequest request) {
        requireOpsToken(token);
        return gamePlatformService.terminateMatch(matchId, request.summary());
    }

    private void requireOpsToken(String token) {
        if (!opsToken.equals(token)) {
            throw PlatformException.forbidden("OPS_FORBIDDEN", "Invalid ops token.");
        }
    }
}

record BanDeviceRequest(@NotBlank String reason) {
}

record TerminateMatchRequest(@NotBlank String summary) {
}
