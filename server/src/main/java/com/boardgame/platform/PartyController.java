package com.boardgame.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/party")
class PartyController {

    private final AuthService authService;
    private final PartyService partyService;
    private final MatchmakingService matchmakingService;

    PartyController(AuthService authService, PartyService partyService, MatchmakingService matchmakingService) {
        this.authService = authService;
        this.partyService = partyService;
        this.matchmakingService = matchmakingService;
    }

    @PostMapping
    PartyView createParty(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return partyService.createParty(user.id);
    }

    @PostMapping("/{partyId}/invite")
    PartyView invite(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String partyId,
            @Valid @RequestBody PartyInviteRequest request) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return partyService.inviteMember(partyId, user.id, request.userId());
    }

    @PostMapping("/{partyId}/leave")
    PartyView leave(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String partyId) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return partyService.leaveParty(partyId, user.id);
    }

    @PostMapping("/{partyId}/queue")
    QueueStatusView queue(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String partyId,
            @Valid @RequestBody QueuePartyRequest request) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return matchmakingService.enqueueParty(partyId, user.id, request.gameType(), request.targetPlayers());
    }
}

record PartyInviteRequest(@NotBlank String userId) {
}

record QueuePartyRequest(
        @NotNull PlatformStore.GameType gameType,
        @Min(2) @Max(4) int targetPlayers) {
}

record QueueStatusView(
        String status,
        String partyId,
        String roomId,
        String matchId,
        String message) {
}
