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
        PlatformRepository(
            baseUrl = BuildConfig.BASE_URL,
            json = json,
        )
    }

    val realtimeClient: RealtimeClient by lazy {
        RealtimeClient(
            baseWsUrl = BuildConfig.BASE_WS_URL,
            json = json,
        )
    }
}
