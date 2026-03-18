package com.boardgame.platform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultScreen(
    result: MatchResultUiModel?,
    matchId: String,
    onLoadResult: (String) -> Unit,
    onReturnToLobby: () -> Unit,
) {
    LaunchedEffect(matchId, result?.matchId) {
        if (result == null || result.matchId != matchId) {
            onLoadResult(matchId)
        }
    }

    if (result == null || result.matchId != matchId) {
        EmptyStateScreen(
            title = stringResource(R.string.result_title),
            message = stringResource(R.string.result_loading),
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { result.playerPages.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.finish_time_value, formatTimestamp(result.finishedAt)))
                    Text(stringResource(R.string.summary_value, result.summary))
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.result_ranking_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    result.placements.forEach { placement ->
                        Text(
                            text = stringResource(
                                R.string.result_row_value,
                                placement.place,
                                placement.displayName,
                                placement.score,
                                placement.completedRows,
                            ),
                        )
                    }
                }
            }
            if (result.playerPages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.playerPages.forEachIndexed { index, page ->
                        OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage(index) } }) {
                            Text(stringResource(R.string.result_board_tab, page.place, page.displayName))
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val boardPage = result.playerPages[page]
                    AzulBoardPanel(
                        title = boardPage.displayName,
                        subtitle = stringResource(
                            R.string.result_board_subtitle,
                            boardPage.place,
                            boardPage.score,
                            boardPage.completedRows,
                        ),
                        board = boardPage.board,
                        interactive = false,
                        legalTargets = emptySet(),
                        onTargetSelected = {},
                    )
                }
            }
            Button(onClick = onReturnToLobby) {
                Text(stringResource(R.string.return_lobby))
            }
        }
    }
}
