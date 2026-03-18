package com.boardgame.platform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TileGroupChip(
    group: AzulTileGroup,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        tonalElevation = if (selected) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(tileColorValue(group.color), CircleShape),
            )
            Text("${colorDisplayName(group.color)} x${group.count}")
        }
    }
}

@Composable
fun AzulBoardPanel(
    title: String,
    subtitle: String,
    board: AzulPlayerBoardSnapshot,
    interactive: Boolean,
    legalTargets: Set<Int?>,
    onTargetSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    board.patternLines.forEachIndexed { index, line ->
                        PatternLineRow(
                            lineIndex = index,
                            line = line,
                            isLegal = legalTargets.contains(index),
                            interactive = interactive,
                            onClick = { onTargetSelected(index) },
                        )
                    }
                    FloorLineRow(
                        floorTiles = board.floorTiles,
                        isLegal = legalTargets.contains(null),
                        interactive = interactive,
                        onClick = { onTargetSelected(null) },
                    )
                    if (board.hasFirstPlayerMarker) {
                        Text(
                            text = stringResource(R.string.first_player_marker_held),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                WallGrid(
                    wall = board.wall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PatternLineRow(
    lineIndex: Int,
    line: AzulPatternLine,
    isLegal: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = interactive && isLegal, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isLegal) 2.dp else 1.dp,
            color = if (isLegal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.pattern_line_title, lineIndex + 1))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(line.capacity) { slot ->
                    val filled = slot < line.count
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = if (filled) tileColorValue(line.color) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloorLineRow(
    floorTiles: List<AzulColor>,
    isLegal: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = interactive && isLegal, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isLegal) 2.dp else 1.dp,
            color = if (isLegal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.floor_line_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { slot ->
                    val tile = floorTiles.getOrNull(slot)
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = tileColorValue(tile),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WallGrid(
    wall: List<List<Boolean>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { column ->
                    val isFilled = wall.getOrElse(row) { emptyList() }.getOrElse(column) { false }
                    val templateColor = tileColorValue(wallTemplateColor(row, column))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(
                                color = if (isFilled) templateColor else templateColor.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .alpha(if (isFilled) 1f else 0.8f),
                    )
                }
            }
        }
    }
}

fun tileColorValue(color: AzulColor?): Color {
    return when (color) {
        AzulColor.BLUE -> Color(0xFF2D6CDF)
        AzulColor.YELLOW -> Color(0xFFE2B714)
        AzulColor.RED -> Color(0xFFD1495B)
        AzulColor.BLACK -> Color(0xFF2B2D42)
        AzulColor.WHITE -> Color(0xFFF2F4F8)
        null -> Color(0xFFE4E7EC)
    }
}
