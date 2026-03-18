package com.boardgame.platform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchScreen(
    matchId: String,
    authToken: String,
    currentUserId: String,
    roomPlayers: List<UserView>,
    onLoadResult: (String) -> Unit,
) {
    val matchViewModel: MatchViewModel = viewModel(
        key = "match:$matchId",
        factory = MatchViewModel.factory(
            matchId = matchId,
            authToken = authToken,
            currentUserId = currentUserId,
        ),
    )
    val uiState by matchViewModel.uiState.collectAsState()
    val snapshot = uiState.snapshot?.state

    if (snapshot == null) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.match_loading),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        return
    }

    LaunchedEffect(uiState.snapshot?.phase) {
        if (uiState.snapshot?.phase == "FINISHED") {
            onLoadResult(matchId)
        }
    }

    val pages = orderedMatchBoards(snapshot, currentUserId, roomPlayers)
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    val currentTurnUserId = uiState.snapshot?.currentPlayerId
    val currentTurnName = roomPlayers.firstOrNull { it.id == currentTurnUserId }?.displayName ?: currentTurnUserId.orEmpty()
    val isMyTurn = currentTurnUserId == currentUserId
    val canInteract = isMyTurn &&
        pagerState.currentPage == 0 &&
        uiState.pendingClientSeq == null &&
        uiState.connectionStatus == RealtimeConnectionStatus.CONNECTED
    val selectedGroup = uiState.selectedSource?.group
    val legalTargetSet = uiState.legalTargets.map { it.lineIndex }.toSet()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MatchHeader(
                roundNumber = snapshot.roundNumber,
                currentTurnName = currentTurnName.ifBlank { "--" },
                deadlineAt = uiState.snapshot?.deadlineAt,
                connectionStatus = uiState.connectionStatus,
            )
            MessageCard(
                noticeMessage = uiState.noticeMessage,
                errorMessage = uiState.errorMessage,
                onClearNotice = matchViewModel::dismissNotice,
                onClearError = matchViewModel::dismissError,
            )
            if (selectedGroup != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.selected_source_hint, selectedSourceLabel(selectedGroup)),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (isMyTurn && pagerState.currentPage != 0) {
                Button(onClick = { scope.launch { pagerState.animateScrollToPage(0) } }) {
                    Text(stringResource(R.string.return_to_my_board))
                }
            }

            FactoriesSection(
                snapshot = snapshot,
                selectedGroup = selectedGroup,
                enabled = canInteract,
                onFactorySelected = matchViewModel::selectFactoryColor,
                onCenterSelected = matchViewModel::selectCenterColor,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val boardPage = pages[page]
                AzulBoardPanel(
                    title = boardPage.displayName,
                    subtitle = if (boardPage.isSelf) {
                        stringResource(R.string.my_board_subtitle, boardPage.board.score)
                    } else {
                        stringResource(R.string.opponent_board_subtitle, boardPage.board.score)
                    },
                    board = boardPage.board,
                    interactive = boardPage.isSelf && canInteract,
                    legalTargets = if (boardPage.isSelf) legalTargetSet else emptySet(),
                    onTargetSelected = matchViewModel::submitTarget,
                )
            }
        }
    }
}

@Composable
private fun MatchHeader(
    roundNumber: Int,
    currentTurnName: String,
    deadlineAt: String?,
    connectionStatus: RealtimeConnectionStatus,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.match_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.round_value, roundNumber))
            Text(stringResource(R.string.current_turn_value, currentTurnName))
            Text(countdownLabel(deadlineAt))
            Text(connectionStatusText(connectionStatus))
        }
    }
}

@Composable
private fun FactoriesSection(
    snapshot: AzulSnapshot,
    selectedGroup: AzulTileGroup?,
    enabled: Boolean,
    onFactorySelected: (Int, AzulColor, Int) -> Unit,
    onCenterSelected: (AzulColor, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.factories_title),
            style = MaterialTheme.typography.titleMedium,
        )
        factoryGroups(snapshot).chunked(3).forEachIndexed { rowIndex, factoryRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                factoryRow.forEachIndexed { rowLocalIndex, groups ->
                    val factoryIndex = rowIndex * 3 + rowLocalIndex
                    Card(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.factory_title, factoryIndex + 1),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (groups.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.factory_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                groups.forEach { group ->
                                    TileGroupChip(
                                        group = group,
                                        selected = group == selectedGroup,
                                        enabled = enabled,
                                        onClick = { onFactorySelected(factoryIndex, group.color, group.count) },
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(3 - factoryRow.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.center_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.bag_discard_value, snapshot.bagCount, snapshot.discardCount))
                if (snapshot.firstPlayerMarkerAvailable) {
                    Text(stringResource(R.string.marker_available), color = MaterialTheme.colorScheme.primary)
                }
                if (centerGroups(snapshot).isEmpty()) {
                    Text(
                        text = stringResource(R.string.center_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    centerGroups(snapshot).forEach { group ->
                        TileGroupChip(
                            group = group,
                            selected = group == selectedGroup,
                            enabled = enabled,
                            onClick = { onCenterSelected(group.color, group.count) },
                        )
                    }
                }
            }
        }
    }
}

private fun selectedSourceLabel(group: AzulTileGroup): String {
    val sourceLabel = if (group.sourceType == AzulSourceType.CENTER) {
        "\u4e2d\u592e\u533a"
    } else {
        "\u5de5\u5382 ${group.sourceIndex + 1}"
    }
    return "$sourceLabel ${colorDisplayName(group.color)} x${group.count}"
}

@Composable
private fun countdownLabel(deadlineAt: String?): String {
    val rawValue = deadlineAt ?: return stringResource(R.string.countdown_unknown)
    return runCatching {
        val deadline = Instant.parse(rawValue)
        val seconds = ((deadline.toEpochMilli() - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
        stringResource(R.string.countdown_value, seconds)
    }.getOrDefault(stringResource(R.string.countdown_unknown))
}
