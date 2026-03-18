package com.boardgame.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
class RealtimeSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final WebSocketRealtimeGateway realtimeGateway;
    private final GamePlatformService gamePlatformService;

    RealtimeSocketHandler(
            ObjectMapper objectMapper,
            AuthService authService,
            WebSocketRealtimeGateway realtimeGateway,
            GamePlatformService gamePlatformService) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.realtimeGateway = realtimeGateway;
        this.gamePlatformService = gamePlatformService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryParameters(session.getUri()).get("token");
        PlatformStore.User user = authService.requireUser(token);
        realtimeGateway.register(user.id, session);
        realtimeGateway.subscribe(user.id, session, "users/" + user.id);
        realtimeGateway.send(session, "system.notice", new SystemNoticeView("CONNECTED", "Realtime channel connected."));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = realtimeGateway.userId(session);
        ClientRealtimeMessage clientMessage = objectMapper.readValue(message.getPayload(), ClientRealtimeMessage.class);
        switch (clientMessage.type()) {
            case "subscribe" -> {
                realtimeGateway.subscribe(userId, session, clientMessage.topic());
                realtimeGateway.send(session, "system.notice", new SystemNoticeView("SUBSCRIBED", clientMessage.topic()));
            }
            case "unsubscribe" -> {
                realtimeGateway.unsubscribe(session, clientMessage.topic());
                realtimeGateway.send(session, "system.notice", new SystemNoticeView("UNSUBSCRIBED", clientMessage.topic()));
            }
            case "game.action" -> {
                GameActionRequest request = objectMapper.treeToValue(clientMessage.payload(), GameActionRequest.class);
                try {
                    gamePlatformService.submitAction(userId, request, false);
                } catch (PlatformException exception) {
                    realtimeGateway.sendToUser(
                            userId,
                            "game.actionRejected",
                            new GameActionRejectedView(
                                    request.gameId(),
                                    request.clientSeq(),
                                    exception.getMessage()));
                }
            }
            case "ping" -> realtimeGateway.send(session, "system.notice", new SystemNoticeView("PONG", "pong"));
            default -> throw PlatformException.badRequest("REALTIME_TYPE_INVALID", "Unsupported realtime message type.");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        realtimeGateway.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        realtimeGateway.unregister(session);
    }

    private Map<String, String> queryParameters(URI uri) {
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
            return Map.of();
        }
        return Arrays.stream(uri.getQuery().split("&"))
                .map(entry -> entry.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> pair[0],
                        pair -> pair.length > 1 ? pair[1] : ""));
    }
}

record ClientRealtimeMessage(String type, String topic, JsonNode payload) {
}

record SystemNoticeView(String code, String message) {
}
