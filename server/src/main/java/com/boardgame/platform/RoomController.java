package com.boardgame.platform;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
class RoomController {

    private final AuthService authService;
    private final RoomService roomService;

    RoomController(AuthService authService, RoomService roomService) {
        this.authService = authService;
        this.roomService = roomService;
    }

    @PostMapping
    RoomView createRoom(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateRoomRequest request) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return roomService.createRoom(user.id, request);
    }

    @PostMapping("/{roomId}/join")
    RoomView joinRoom(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return roomService.joinRoom(user.id, roomId);
    }

    @PostMapping("/{roomId}/leave")
    RoomView leaveRoom(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return roomService.leaveRoom(user.id, roomId);
    }

    @PostMapping("/{roomId}/start")
    RoomView startRoom(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        PlatformStore.User user = authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return roomService.startRoom(user.id, roomId);
    }

    @GetMapping("/{roomId}")
    RoomView getRoom(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String roomId) {
        authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return roomService.getRoomView(roomId);
    }
}
