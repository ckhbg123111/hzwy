package com.boardgame.platform

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object AppContainer {
    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    val repository: PlatformRepository by lazy {
        PlatformRepository(json = json)
    }

    val realtimeClient: RealtimeClient by lazy {
        RealtimeClient(json = json)
    }
}
