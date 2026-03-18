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

class MatchViewModel(
    private val matchId: String,
    private val authToken: String,
    private val currentUserId: String,
    private val repository: PlatformRepository = AppContainer.repository,
    private val realtimeClient: RealtimeClient = AppContainer.realtimeClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        realtimeClient.connect(authToken)
        viewModelScope.launch {
            realtimeClient.connectionStatus.collectLatest { status ->
                _uiState.update { state -> state.copy(connectionStatus = status) }
                if (status == RealtimeConnectionStatus.CONNECTED) {
                    realtimeClient.subscribe("games/$matchId")
                    refreshSnapshot(showLoading = _uiState.value.snapshot == null)
                }
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collectLatest { event ->
                handleRealtimeEvent(event)
            }
        }
        refreshSnapshot()
    }

    fun refreshSnapshot(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { state -> state.copy(loading = true, errorMessage = null) }
            }
            runCatching {
                repository.getGame(authToken, matchId)
            }.onSuccess { snapshot ->
                applySnapshot(snapshot)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        loading = false,
                        errorMessage = throwable.message ?: "\u52a0\u8f7d\u5bf9\u5c40\u5feb\u7167\u5931\u8d25",
                    )
                }
            }
        }
    }

    fun selectFactoryColor(factoryIndex: Int, color: AzulColor, count: Int) {
        updateSelection(
            AzulTileGroup(
                sourceType = AzulSourceType.FACTORY,
                sourceIndex = factoryIndex,
                color = color,
                count = count,
            ),
        )
    }

    fun selectCenterColor(color: AzulColor, count: Int) {
        updateSelection(
            AzulTileGroup(
                sourceType = AzulSourceType.CENTER,
                sourceIndex = -1,
                color = color,
                count = count,
            ),
        )
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                selectedSource = null,
                legalTargets = emptyList(),
            )
        }
    }

    fun dismissNotice() {
        _uiState.update { state -> state.copy(noticeMessage = null) }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(errorMessage = null) }
    }

    fun submitTarget(lineIndex: Int?) {
        val snapshot = uiState.value.snapshot ?: return
        val selection = uiState.value.selectedSource ?: return
        if (uiState.value.legalTargets.none { it.lineIndex == lineIndex }) {
            return
        }
        if (uiState.value.pendingClientSeq != null) {
            return
        }

        val clientSeq = "client-" + UUID.randomUUID().toString().take(12)
        val payload = AzulMovePayload(
            sourceType = selection.group.sourceType,
            sourceIndex = selection.group.sourceIndex,
            color = selection.group.color,
            targetLine = lineIndex ?: -1,
        )
        realtimeClient.sendGameAction(
            gameId = snapshot.gameId,
            payload = payload,
            clientSeq = clientSeq,
        )
        _uiState.update { state ->
            state.copy(
                pendingClientSeq = clientSeq,
                noticeMessage = "\u6b63\u5728\u63d0\u4ea4\u64cd\u4f5c",
                errorMessage = null,
            )
        }
    }

    private fun updateSelection(group: AzulTileGroup) {
        val snapshotView = uiState.value.snapshot ?: return
        if (snapshotView.currentPlayerId != currentUserId) {
            return
        }
        val snapshot = snapshotView.state ?: return
        val board = snapshot.players.firstOrNull { it.userId == currentUserId } ?: return
        if (uiState.value.pendingClientSeq != null) {
            return
        }
        _uiState.update { state ->
            state.copy(
                selectedSource = AzulSelectionState(group),
                legalTargets = legalTargets(board, group.color),
                noticeMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun handleRealtimeEvent(event: ServerRealtimeMessage) {
        when (event.type) {
            "game.snapshot" -> decodePayload<GameSnapshotView>(event.payload)
                ?.takeIf { it.gameId == matchId }
                ?.let(::applySnapshot)

            "game.actionAck" -> decodePayload<GameActionAckView>(event.payload)
                ?.takeIf { it.gameId == matchId }
                ?.let { ack ->
                    _uiState.update { state ->
                        if (state.pendingClientSeq != ack.clientSeq) {
                            state
                        } else {
                            state.copy(
                                pendingClientSeq = null,
                                noticeMessage = "\u64cd\u4f5c\u5df2\u9001\u8fbe\uff0c\u7b49\u5f85\u5c40\u9762\u540c\u6b65",
                            )
                        }
                    }
                }

            "game.actionRejected" -> decodePayload<GameActionRejectedView>(event.payload)
                ?.takeIf { it.gameId == matchId }
                ?.let { rejected ->
                    _uiState.update { state ->
                        state.copy(
                            pendingClientSeq = null,
                            selectedSource = null,
                            legalTargets = emptyList(),
                            errorMessage = rejected.reason,
                        )
                    }
                    refreshSnapshot(showLoading = false)
                }
        }
    }

    private fun applySnapshot(snapshot: GameSnapshotView) {
        _uiState.update { state ->
            val shouldClearPending = state.snapshot == null || snapshot.stateVersion > state.snapshot.stateVersion
            state.copy(
                loading = false,
                snapshot = snapshot,
                pendingClientSeq = if (shouldClearPending) null else state.pendingClientSeq,
                selectedSource = null,
                legalTargets = emptyList(),
                noticeMessage = when {
                    snapshot.phase == "FINISHED" -> "\u5bf9\u5c40\u5df2\u7ed3\u675f\uff0c\u6b63\u5728\u52a0\u8f7d\u7ed3\u7b97"
                    shouldClearPending && state.pendingClientSeq != null -> "\u6700\u65b0\u5c40\u9762\u5df2\u540c\u6b65"
                    else -> state.noticeMessage
                },
                errorMessage = null,
            )
        }
    }

    private inline fun <reified T> decodePayload(payload: JsonElement?): T? {
        return payload?.let { repository.json.decodeFromJsonElement(it) }
    }

    companion object {
        fun factory(matchId: String, authToken: String, currentUserId: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MatchViewModel(
                        matchId = matchId,
                        authToken = authToken,
                        currentUserId = currentUserId,
                    ) as T
                }
            }
        }
    }
}
