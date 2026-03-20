package com.boardgame.platform

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class PlatformRepository(
    private val baseUrl: String = BuildConfig.BASE_URL,
    val json: Json,
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun guestLogin(deviceId: String, displayName: String): AuthSessionResponse =
        post("/api/v1/auth/guest", body = json.encodeToJsonElement(GuestAuthRequest(deviceId, displayName)))

    suspend fun createParty(token: String): PartyView =
        post("/api/v1/party", token = token, body = JsonObject(emptyMap()))

    suspend fun invite(token: String, partyId: String, targetUserId: String): PartyView =
        post("/api/v1/party/$partyId/invite", token = token, body = json.encodeToJsonElement(PartyInviteRequest(targetUserId)))

    suspend fun createRoom(token: String, targetPlayers: Int): RoomView =
        post("/api/v1/rooms", token = token, body = json.encodeToJsonElement(CreateRoomRequest(targetPlayers = targetPlayers)))

    suspend fun startRoom(token: String, roomId: String): RoomView =
        post("/api/v1/rooms/$roomId/start", token = token, body = JsonObject(emptyMap()))

    suspend fun getGame(token: String, gameId: String): GameSnapshotView =
        get("/api/v1/games/$gameId", token = token)

    suspend fun getMatchResult(token: String, matchId: String): MatchResultView =
        get("/api/v1/matches/$matchId/result", token = token)

    suspend fun getRankings(): List<RankingEntryView> =
        get("/api/v1/rankings/azul")

    private suspend inline fun <reified T> get(path: String, token: String? = null): T =
        request("GET", path, token, null)

    private suspend inline fun <reified T> post(path: String, token: String? = null, body: JsonElement): T =
        request("POST", path, token, body)

    private suspend inline fun <reified T> request(
        method: String,
        path: String,
        token: String?,
        body: JsonElement?,
    ): T = withContext(Dispatchers.IO) {
        val serializedBody = body?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
        val requestBuilder = Request.Builder().url(baseUrl + path)

        if (!token.isNullOrBlank()) {
            requestBuilder.header("X-Auth-Token", token)
        }

        val request = when (method) {
            "GET" -> requestBuilder.get().build()
            "POST" -> requestBuilder.post(serializedBody.toRequestBody(jsonMediaType)).build()
            else -> error("Unsupported method: $method")
        }

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $responseBody")
            }
            json.decodeFromString(responseBody)
        }
    }
}

class RealtimeClient(
    private val baseWsUrl: String = BuildConfig.BASE_WS_URL,
    private val json: Json,
) {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<ServerRealtimeMessage>(extraBufferCapacity = 64)
    private val _connectionStatus = MutableStateFlow(RealtimeConnectionStatus.DISCONNECTED)
    private val subscribedTopics = linkedSetOf<String>()
    private var webSocket: WebSocket? = null
    private var currentToken: String? = null
    private var reconnectJob: Job? = null

    val events = _events.asSharedFlow()
    val connectionStatus = _connectionStatus.asStateFlow()

    fun connect(token: String) {
        if (currentToken != null && currentToken != token) {
            synchronized(subscribedTopics) {
                subscribedTopics.clear()
            }
        }
        if (webSocket != null && currentToken == token) {
            return
        }

        reconnectJob?.cancel()
        webSocket?.close(1000, "reconnect")
        webSocket = null
        currentToken = token
        _connectionStatus.value = RealtimeConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url("$baseWsUrl?token=$token")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectJob?.cancel()
                this@RealtimeClient.webSocket = webSocket
                _connectionStatus.value = RealtimeConnectionStatus.CONNECTED
                synchronized(subscribedTopics) {
                    subscribedTopics.toList()
                }.forEach { topic ->
                    sendSubscription("subscribe", topic)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    json.decodeFromString(ServerRealtimeMessage.serializer(), text)
                }.onSuccess { message ->
                    _events.tryEmit(message)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@RealtimeClient.webSocket === webSocket) {
                    this@RealtimeClient.webSocket = null
                }
                if (currentToken == null) {
                    _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
                } else {
                    _connectionStatus.value = RealtimeConnectionStatus.FAILED
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@RealtimeClient.webSocket === webSocket) {
                    this@RealtimeClient.webSocket = null
                }
                if (currentToken == null) {
                    _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
                } else {
                    _connectionStatus.value = RealtimeConnectionStatus.FAILED
                    scheduleReconnect()
                }
            }
        })
    }

    fun subscribe(topic: String) {
        if (topic.isBlank()) {
            return
        }
        synchronized(subscribedTopics) {
            subscribedTopics.add(topic)
        }
        sendSubscription("subscribe", topic)
    }

    fun unsubscribe(topic: String) {
        synchronized(subscribedTopics) {
            subscribedTopics.remove(topic)
        }
        sendSubscription("unsubscribe", topic)
    }

    fun sendGameAction(gameId: String, payload: AzulMovePayload, clientSeq: String) {
        val message = buildJsonObject {
            put("type", JsonPrimitive("game.action"))
            put(
                "payload",
                json.encodeToJsonElement(
                    GameActionRequest(
                        gameId = gameId,
                        payload = json.encodeToJsonElement(payload),
                        clientSeq = clientSeq,
                    ),
                ),
            )
        }
        send(message)
    }

    fun close() {
        reconnectJob?.cancel()
        reconnectJob = null
        currentToken = null
        synchronized(subscribedTopics) {
            subscribedTopics.clear()
        }
        webSocket?.close(1000, "closed")
        webSocket = null
        _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
    }

    private fun sendSubscription(type: String, topic: String) {
        send(
            buildJsonObject {
                put("type", JsonPrimitive(type))
                put("topic", JsonPrimitive(topic))
            },
        )
    }

    private fun send(message: JsonObject) {
        webSocket?.send(json.encodeToString(JsonObject.serializer(), message))
    }

    private fun scheduleReconnect() {
        val token = currentToken ?: return
        if (reconnectJob?.isActive == true) {
            return
        }
        reconnectJob = scope.launch {
            delay(1500L)
            if (currentToken == token && webSocket == null) {
                connect(token)
            }
        }
    }
}
