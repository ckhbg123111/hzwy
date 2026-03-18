package com.boardgame.platform

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val wallPattern = listOf(
    listOf(AzulColor.BLUE, AzulColor.YELLOW, AzulColor.RED, AzulColor.BLACK, AzulColor.WHITE),
    listOf(AzulColor.WHITE, AzulColor.BLUE, AzulColor.YELLOW, AzulColor.RED, AzulColor.BLACK),
    listOf(AzulColor.BLACK, AzulColor.WHITE, AzulColor.BLUE, AzulColor.YELLOW, AzulColor.RED),
    listOf(AzulColor.RED, AzulColor.BLACK, AzulColor.WHITE, AzulColor.BLUE, AzulColor.YELLOW),
    listOf(AzulColor.YELLOW, AzulColor.RED, AzulColor.BLACK, AzulColor.WHITE, AzulColor.BLUE),
)

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

fun factoryGroups(snapshot: AzulSnapshot): List<List<AzulTileGroup>> {
    return snapshot.factories.mapIndexed { index, tiles ->
        groupedTiles(AzulSourceType.FACTORY, index, tiles)
    }
}

fun centerGroups(snapshot: AzulSnapshot): List<AzulTileGroup> {
    return groupedTiles(AzulSourceType.CENTER, -1, snapshot.centerTiles)
}

fun groupedTiles(sourceType: AzulSourceType, sourceIndex: Int, tiles: List<AzulColor>): List<AzulTileGroup> {
    return AzulColor.entries.mapNotNull { color ->
        val count = tiles.count { it == color }
        if (count == 0) {
            null
        } else {
            AzulTileGroup(sourceType = sourceType, sourceIndex = sourceIndex, color = color, count = count)
        }
    }
}

fun legalTargets(board: AzulPlayerBoardSnapshot, color: AzulColor): List<AzulLegalTarget> {
    val targets = mutableListOf<AzulLegalTarget>()
    board.patternLines.forEachIndexed { index, line ->
        if (canPlaceOnLine(board, color, index, line)) {
            targets += AzulLegalTarget(lineIndex = index, label = "\u7b2c ${index + 1} \u884c")
        }
    }
    targets += AzulLegalTarget(lineIndex = null, label = "\u5730\u677f\u7ebf")
    return targets
}

fun orderedMatchBoards(
    snapshot: AzulSnapshot,
    currentUserId: String,
    roomPlayers: List<UserView>,
): List<MatchBoardPage> {
    val nameMap = roomPlayers.associateBy({ it.id }, { it.displayName })
    val selfBoard = snapshot.players.firstOrNull { it.userId == currentUserId }
    val others = snapshot.players.filterNot { it.userId == currentUserId }
    return buildList {
        if (selfBoard != null) {
            add(
                MatchBoardPage(
                    userId = selfBoard.userId,
                    displayName = nameMap[selfBoard.userId] ?: selfBoard.userId,
                    board = selfBoard,
                    isSelf = true,
                ),
            )
        }
        others.forEach { board ->
            add(
                MatchBoardPage(
                    userId = board.userId,
                    displayName = nameMap[board.userId] ?: board.userId,
                    board = board,
                    isSelf = false,
                ),
            )
        }
    }
}

fun MatchResultView.toUiModel(): MatchResultUiModel {
    val boardMap = finalState?.players.orEmpty().associateBy { it.userId }
    val pages = placements.mapNotNull { placement ->
        boardMap[placement.userId]?.let { board ->
            ResultBoardPage(
                userId = placement.userId,
                displayName = placement.displayName,
                place = placement.place,
                score = placement.score,
                completedRows = placement.completedRows,
                board = board,
            )
        }
    }
    return MatchResultUiModel(
        matchId = matchId,
        roomId = roomId,
        gameType = gameType,
        placements = placements,
        finishedAt = finishedAt,
        summary = summary,
        finalState = finalState,
        playerPages = pages,
    )
}

fun wallTemplateColor(row: Int, column: Int): AzulColor {
    return wallPattern[row][column]
}

fun colorDisplayName(color: AzulColor): String {
    return when (color) {
        AzulColor.BLUE -> "\u84dd\u8272"
        AzulColor.YELLOW -> "\u9ec4\u8272"
        AzulColor.RED -> "\u7ea2\u8272"
        AzulColor.BLACK -> "\u9ed1\u8272"
        AzulColor.WHITE -> "\u767d\u8272"
    }
}

fun formatTimestamp(rawValue: String?): String {
    if (rawValue.isNullOrBlank()) {
        return "--"
    }
    return runCatching {
        dateFormatter.format(Instant.parse(rawValue))
    }.getOrDefault(rawValue)
}

private fun canPlaceOnLine(
    board: AzulPlayerBoardSnapshot,
    color: AzulColor,
    index: Int,
    line: AzulPatternLine,
): Boolean {
    if (line.count >= line.capacity) {
        return false
    }
    if (line.color != null && line.color != color) {
        return false
    }
    val wallRow = board.wall.getOrNull(index).orEmpty()
    val colorColumn = wallPattern[index].indexOf(color)
    return colorColumn !in wallRow.indices || !wallRow[colorColumn]
}
