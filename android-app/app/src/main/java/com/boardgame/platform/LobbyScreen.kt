package com.boardgame.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LobbyScreen(
    uiState: PlatformUiState,
    onLogin: (String) -> Unit,
    onCreateParty: () -> Unit,
    onInvite: (String) -> Unit,
    onCreateRoom: (Int) -> Unit,
    onClearError: () -> Unit,
    onClearNotice: () -> Unit,
) {
    val defaultGuestName = stringResource(R.string.guest_default_name)
    var displayName by rememberSaveable { mutableStateOf(defaultGuestName) }
    var inviteUserId by rememberSaveable { mutableStateOf("") }
    var roomSize by rememberSaveable { mutableIntStateOf(2) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = connectionStatusText(uiState.connectionStatus),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.login_card_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.guest_name_label)) },
                    )
                    Button(
                        onClick = { onLogin(displayName) },
                        enabled = uiState.session == null,
                    ) {
                        Text(stringResource(R.string.login_guest))
                    }
                    uiState.session?.let { session ->
                        Text(stringResource(R.string.current_user_value, session.user.displayName))
                        Text(stringResource(R.string.uid_value, session.user.id))
                    }
                }
            }

            if (uiState.session != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.party_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onCreateParty, enabled = uiState.party == null) {
                                Text(stringResource(R.string.create_party))
                            }
                        }
                        uiState.party?.let { party ->
                            Text(stringResource(R.string.party_id_value, party.id))
                            Text(
                                stringResource(
                                    R.string.party_members_value,
                                    party.members.joinToString { member -> member.displayName },
                                ),
                            )
                            OutlinedTextField(
                                value = inviteUserId,
                                onValueChange = { inviteUserId = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.invite_user_label)) },
                            )
                            Button(
                                onClick = { onInvite(inviteUserId) },
                                enabled = inviteUserId.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.send_invite))
                            }
                        } ?: Text(stringResource(R.string.no_party_hint))
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.create_room_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(2, 3, 4).forEach { size ->
                                OutlinedButton(onClick = { roomSize = size }) {
                                    Text(stringResource(R.string.room_size_value, size))
                                }
                            }
                        }
                        Text(stringResource(R.string.room_size_selected, roomSize))
                        Button(onClick = { onCreateRoom(roomSize) }) {
                            Text(stringResource(R.string.create_room))
                        }
                    }
                }
            }
        }
    }
}
