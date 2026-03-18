package com.boardgame.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun MessageCard(
    noticeMessage: String?,
    errorMessage: String?,
    onClearNotice: () -> Unit,
    onClearError: () -> Unit,
) {
    when {
        errorMessage != null -> {
            StatusCard(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                onDismiss = onClearError,
            )
        }

        noticeMessage != null -> {
            StatusCard(
                text = noticeMessage,
                color = MaterialTheme.colorScheme.primary,
                onDismiss = onClearNotice,
            )
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    color: Color,
    onDismiss: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text,
                color = color,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.dismiss),
                    color = color,
                )
            }
        }
    }
}

@Composable
fun EmptyStateScreen(
    title: String,
    message: String,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(message, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun connectionStatusText(status: RealtimeConnectionStatus): String {
    return when (status) {
        RealtimeConnectionStatus.CONNECTED -> stringResource(R.string.connection_connected)
        RealtimeConnectionStatus.CONNECTING -> stringResource(R.string.connection_connecting)
        RealtimeConnectionStatus.FAILED -> stringResource(R.string.connection_failed)
        RealtimeConnectionStatus.DISCONNECTED -> stringResource(R.string.connection_disconnected)
    }
}
