package com.boardgame.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class PlatformViewModel(
    private val repository: PlatformRepository = AppContainer.repository,
    private val realtimeClient: RealtimeClient = AppContainer.realtimeClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlatformUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            realtimeClient.connectionStatus.collectLatest { status ->
                _uiState.update { state -> state.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collectLatest { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    fun loginAsGuest(displayName: String) = launchTask {
        val session = repository.guestLogin(
            deviceId = "android-" + UUID.randomUUID().toString().take(8),
            displayName = displayName.ifBlank { "\u6d4b\u8bd5\u73a9\u5bb6" },
        )
        realtimeClient.connect(session.token)
        _uiState.update { state ->
            state.copy(
                session = session,
                noticeMessage = "\u5df2\u767b\u5f55\u4e3a ${session.user.displayName}",
                errorMessage = null,
            )
        }
    }

    fun createParty() = withSession { session ->
        launchTask {
            val party = repository.createParty(session.token)
            subscribeParty(party)
            _uiState.update { state ->
                state.copy(
                    party = party,
                    noticeMessage = "\u961f\u4f0d\u5df2\u521b\u5efa",
                )
            }
        }
    }

    fun inviteMember(targetUserId: String) = withSession { session ->
        val partyId = uiState.value.party?.id ?: return@withSession
        launchTask {
            val party = repository.invite(session.token, partyId, targetUserId)
            subscribeParty(party)
            _uiState.update { state ->
                state.copy(
                    party = party,
                    noticeMessage = "\u9080\u8bf7\u5df2\u53d1\u9001",
                )
            }
        }
    }

    fun createRoom(targetPlayers: Int = 2) = withSession { session ->
        launchTask {
            val room = repository.createRoom(session.token, targetPlayers)
            subscribeRoom(room)
            _uiState.update { state ->
                state.copy(
                    room = room,
                    activeResult = null,
                    activeMatchId = room.matchId,
                    noticeMessage = "\u623f\u95f4\u5df2\u521b\u5efa",
                )
            }
        }
    }

    fun startRoom() = withSession { session ->
        val roomId = uiState.value.room?.id ?: return@withSession
        launchTask {
            val room = repository.startRoom(session.token, roomId)
            subscribeRoom(room)
            _uiState.update { state ->
                state.copy(
                    room = room,
                    activeMatchId = room.matchId,
                    noticeMessage = "\u5bf9\u5c40\u5df2\u5f00\u59cb",
                )
            }
        }
    }

    fun loadMatchResult(matchId: String) = withSession { session ->
        if (uiState.value.activeResult?.matchId == matchId) {
            return@withSession
        }
        launchTask {
            val result = repository.getMatchResult(session.token, matchId).toUiModel()
            _uiState.update { state ->
                state.copy(
                    activeResult = result,
                    activeMatchId = null,
                )
            }
        }
    }

    fun returnToLobby() {
        _uiState.update { state ->
            state.copy(
                room = null,
                activeMatchId = null,
                activeResult = null,
                noticeMessage = null,
                errorMessage = null,
            )
        }
    }

    fun clearError() {
        _uiState.update { state -> state.copy(errorMessage = null) }
    }

    fun clearNotice() {
        _uiState.update { state -> state.copy(noticeMessage = null) }
    }

    override fun onCleared() {
        realtimeClient.close()
        super.onCleared()
    }

    private fun handleRealtimeEvent(event: ServerRealtimeMessage) {
        when (event.type) {
            "party.updated" -> decodePayload<PartyView>(event.payload)?.let { party ->
                subscribeParty(party)
                _uiState.update { state -> state.copy(party = party) }
            }

            "room.created", "room.updated" -> decodePayload<RoomView>(event.payload)?.let { room ->
                subscribeRoom(room)
                _uiState.update { state ->
                    state.copy(
                        room = room,
                        activeMatchId = if (room.status == "IN_GAME") room.matchId else state.activeMatchId,
                    )
                }
            }

            "match.found" -> decodePayload<MatchFoundView>(event.payload)?.let { match ->
                _uiState.update { state ->
                    state.copy(
                        activeMatchId = match.matchId,
                        noticeMessage = "\u5df2\u8fdb\u5165\u5bf9\u5c40",
                    )
                }
            }

            "game.result" -> decodePayload<MatchResultView>(event.payload)?.let { result ->
                _uiState.update { state ->
                    state.copy(
                        activeResult = result.toUiModel(),
                        activeMatchId = null,
                        room = state.room?.copy(status = "FINISHED", matchId = result.matchId),
                    )
                }
            }

            "queue.status" -> decodePayload<QueueStatusView>(event.payload)?.let { queue ->
                _uiState.update { state -> state.copy(noticeMessage = queue.message) }
            }

            "system.notice" -> decodePayload<SystemNoticeView>(event.payload)?.let { notice ->
                _uiState.update { state -> state.copy(noticeMessage = notice.message) }
            }
        }
    }

    private inline fun <reified T> decodePayload(payload: JsonElement?): T? {
        return payload?.let { repository.json.decodeFromJsonElement(it) }
    }

    private fun withSession(block: (AuthSessionResponse) -> Unit) {
        uiState.value.session?.let(block)
    }

    private fun launchTask(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(loading = true, errorMessage = null) }
            runCatching {
                block()
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        errorMessage = throwable.message ?: "\u53d1\u751f\u672a\u77e5\u9519\u8bef",
                    )
                }
            }
            _uiState.update { state -> state.copy(loading = false) }
        }
    }

    private fun subscribeParty(party: PartyView?) {
        party?.id?.let { partyId ->
            realtimeClient.subscribe("parties/$partyId")
        }
    }

    private fun subscribeRoom(room: RoomView?) {
        room?.id?.let { roomId ->
            realtimeClient.subscribe("rooms/$roomId")
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlatformViewModel() as T
            }
        }
    }
}
