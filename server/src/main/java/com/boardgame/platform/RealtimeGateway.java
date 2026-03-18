package com.boardgame.platform;

interface RealtimeGateway {

    void sendToUser(String userId, String type, Object payload);

    void publishToTopic(String topic, String type, Object payload);
}
