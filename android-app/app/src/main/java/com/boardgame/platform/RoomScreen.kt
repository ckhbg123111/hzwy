package com.boardgame.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun RoomScreen(
    uiState: PlatformUiState,
    onStartGame: () -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
) {
    val room = uiState.room
    if (room == null) {
        EmptyStateScreen(
            title = stringResource(R.string.room_title),
            message = stringResource(R.string.room_unavailable),
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.room_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (uiState.loading) {
                CircularProgressIndicator()
            }
            MessageCard(
                noticeMessage = uiState.noticeMessage,
                errorMessage = uiState.errorMessage,
                onClearNotice = onClearNotice,
                onClearError = onClearError,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.room_id_value, room.id))
                    Text(stringResource(R.string.room_status_value, room.status))
                    Text(stringResource(R.string.room_game_type_value, room.gameType))
                    Text(stringResource(R.string.host_value, room.hostUserId))
                    Text(stringResource(R.string.room_players_value, room.players.size, room.targetPlayers))
                    val isHost = uiState.session?.user?.id == room.hostUserId
                    Button(
                        onClick = onStartGame,
                        enabled = isHost && room.status == "OPEN",
                    ) {
                        Text(stringResource(R.string.start_game))
                    }
                    if (!isHost) {
                        Text(stringResource(R.string.waiting_host))
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.player_list_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        room.players.forEach { player ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(player.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(player.id, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
