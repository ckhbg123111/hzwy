package com.boardgame.platform

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonEncodingTest {
    @Test
    fun createRoomRequest_includes_required_default_fields() {
        val payload = AppContainer.json.encodeToString(CreateRoomRequest(targetPlayers = 2))
        val body = AppContainer.json.parseToJsonElement(payload).jsonObject

        assertEquals("AZUL", body.getValue("gameType").jsonPrimitive.content)
        assertEquals("2", body.getValue("targetPlayers").jsonPrimitive.content)
        assertEquals("PUBLIC", body.getValue("visibility").jsonPrimitive.content)
        assertEquals("true", body.getValue("allowFill").jsonPrimitive.content)
    }

    @Test
    fun gameActionRequest_includes_default_action_type() {
        val payload = AppContainer.json.encodeToString(
            GameActionRequest(
                gameId = "game_123",
                payload = buildJsonObject {
                    put("color", "BLUE")
                },
                clientSeq = "client_123",
            ),
        )
        val body = AppContainer.json.parseToJsonElement(payload).jsonObject

        assertEquals("TAKE_TILES", body.getValue("actionType").jsonPrimitive.content)
    }
}
