package com.boardgame.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
class WebSocketRealtimeGateway implements RealtimeGateway {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketRealtimeGateway.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> sessionsByTopic = new ConcurrentHashMap<>();
    private final Map<String, String> userIdBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> topicsBySessionId = new ConcurrentHashMap<>();

    WebSocketRealtimeGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void register(String userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        userIdBySessionId.put(session.getId(), userId);
        topicsBySessionId.put(session.getId(), ConcurrentHashMap.newKeySet());
    }

    void unregister(WebSocketSession session) {
        String sessionId = session.getId();
        String userId = userIdBySessionId.remove(sessionId);
        if (userId != null) {
            Set<WebSocketSession> userSessions = sessionsByUserId.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessionsByUserId.remove(userId);
                }
            }
        }
        Set<String> topics = new HashSet<>(topicsBySessionId.getOrDefault(sessionId, Set.of()));
        topics.forEach(topic -> unsubscribe(session, topic));
        topicsBySessionId.remove(sessionId);
    }

    void subscribe(String userId, WebSocketSession session, String topic) {
        if (topic == null || topic.isBlank()) {
            throw PlatformException.badRequest("REALTIME_TOPIC_MISSING", "Topic is required for subscription.");
        }
        if (topic.startsWith("users/") && !topic.equals("users/" + userId)) {
            throw PlatformException.forbidden("REALTIME_TOPIC_FORBIDDEN", "User topic can only target the current user.");
        }
        sessionsByTopic.computeIfAbsent(topic, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        topicsBySessionId.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(topic);
    }

    void unsubscribe(WebSocketSession session, String topic) {
        Set<WebSocketSession> sessions = sessionsByTopic.get(topic);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByTopic.remove(topic);
            }
        }
        Set<String> topics = topicsBySessionId.get(session.getId());
        if (topics != null) {
            topics.remove(topic);
        }
    }

    String userId(WebSocketSession session) {
        String userId = userIdBySessionId.get(session.getId());
        if (userId == null) {
            throw PlatformException.unauthorized("REALTIME_AUTH_INVALID", "Realtime session is not authenticated.");
        }
        return userId;
    }

    @Override
    public void sendToUser(String userId, String type, Object payload) {
        Set<WebSocketSession> sessions = sessionsByUserId.getOrDefault(userId, Set.of());
        for (WebSocketSession session : sessions) {
            send(session, type, payload);
        }
    }

    @Override
    public void publishToTopic(String topic, String type, Object payload) {
        Set<WebSocketSession> sessions = sessionsByTopic.getOrDefault(topic, Set.of());
        for (WebSocketSession session : sessions) {
            send(session, type, payload);
        }
    }

    void send(WebSocketSession session, String type, Object payload) {
        if (!session.isOpen()) {
            unregister(session);
            return;
        }
        try {
            ServerRealtimeMessage envelope = new ServerRealtimeMessage(type, payload, Instant.now());
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
        } catch (IOException exception) {
            logger.warn(
                    "realtime send failed sessionId={} userId={} type={} message={}",
                    session.getId(),
                    userIdBySessionId.getOrDefault(session.getId(), "unknown"),
                    type,
                    exception.getMessage(),
                    exception);
            unregister(session);
        }
    }
}

record ServerRealtimeMessage(String type, Object payload, Instant sentAt) {
}
