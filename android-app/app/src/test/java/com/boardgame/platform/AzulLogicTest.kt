package com.boardgame.platform

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class AzulLogicTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun snapshotJsonDecodesTypedAzulState() {
        val payload = """
            {
              "gameId": "match_demo",
              "gameType": "AZUL",
              "phase": "SELECT_TILES",
              "stateVersion": 3,
              "currentPlayerId": "user_self",
              "deadlineAt": "2026-03-18T12:00:00Z",
              "state": {
                "roundNumber": 2,
                "factories": [["BLUE", "BLUE", "RED", "WHITE"], ["YELLOW", "BLACK"]],
                "centerTiles": ["BLACK", "RED"],
                "firstPlayerMarkerAvailable": true,
                "bagCount": 73,
                "discardCount": 11,
                "players": [
                  {
                    "userId": "user_self",
                    "score": 5,
                    "patternLines": [
                      { "capacity": 1, "count": 0 },
                      { "capacity": 2, "color": "BLUE", "count": 1 },
                      { "capacity": 3, "count": 0 },
                      { "capacity": 4, "count": 0 },
                      { "capacity": 5, "count": 0 }
                    ],
                    "wall": [
                      [false, false, false, false, false],
                      [false, false, false, false, false],
                      [false, false, false, false, false],
                      [false, false, false, false, false],
                      [false, false, false, false, false]
                    ],
                    "floorTiles": [],
                    "hasFirstPlayerMarker": false
                  }
                ]
              }
            }
        """.trimIndent()

        val snapshot = json.decodeFromString<GameSnapshotView>(payload)

        assertEquals("match_demo", snapshot.gameId)
        assertNotNull(snapshot.state)
        assertEquals(2, snapshot.state?.roundNumber)
        assertEquals(AzulColor.BLUE, snapshot.state?.factories?.first()?.first())
        assertEquals(AzulColor.BLACK, snapshot.state?.centerTiles?.first())
    }

    @Test
    fun groupedTilesCollapsesByColorInEnumOrder() {
        val groups = groupedTiles(
            sourceType = AzulSourceType.FACTORY,
            sourceIndex = 0,
            tiles = listOf(AzulColor.RED, AzulColor.BLUE, AzulColor.BLUE, AzulColor.WHITE),
        )

        assertEquals(listOf(AzulColor.BLUE, AzulColor.RED, AzulColor.WHITE), groups.map { it.color })
        assertEquals(listOf(2, 1, 1), groups.map { it.count })
    }

    @Test
    fun legalTargetsOnlyExposeCompatibleLinesAndFloor() {
        val board = AzulPlayerBoardSnapshot(
            userId = "user_self",
            score = 12,
            patternLines = listOf(
                AzulPatternLine(capacity = 1, color = null, count = 0),
                AzulPatternLine(capacity = 2, color = AzulColor.BLUE, count = 1),
                AzulPatternLine(capacity = 3, color = AzulColor.RED, count = 1),
                AzulPatternLine(capacity = 4, color = null, count = 4),
                AzulPatternLine(capacity = 5, color = null, count = 0),
            ),
            wall = listOf(
                listOf(false, false, false, false, false),
                listOf(false, false, false, false, false),
                listOf(false, false, false, false, false),
                listOf(false, false, false, false, false),
                listOf(false, false, false, false, true),
            ),
            floorTiles = emptyList(),
            hasFirstPlayerMarker = false,
        )

        val targets = legalTargets(board, AzulColor.BLUE)

        assertEquals(listOf(0, 1, null), targets.map { it.lineIndex })
        assertTrue(targets.any { it.isFloor })
        assertFalse(targets.any { it.lineIndex == 2 })
        assertFalse(targets.any { it.lineIndex == 3 })
        assertFalse(targets.any { it.lineIndex == 4 })
    }

    @Test
    fun matchResultUiModelUsesPlacementOrderAndFinalBoards() {
        val result = MatchResultView(
            matchId = "match_demo",
            roomId = "room_demo",
            gameType = "AZUL",
            placements = listOf(
                PlacementView("user_a", "Alice", 1, 68, 2),
                PlacementView("user_b", "Bob", 2, 61, 1),
            ),
            finishedAt = "2026-03-18T12:30:00Z",
            summary = "Azul match finished.",
            finalState = AzulSnapshot(
                roundNumber = 6,
                factories = emptyList(),
                centerTiles = emptyList(),
                firstPlayerMarkerAvailable = false,
                bagCount = 0,
                discardCount = 21,
                players = listOf(
                    AzulPlayerBoardSnapshot(
                        userId = "user_b",
                        score = 61,
                        patternLines = emptyList(),
                        wall = List(5) { List(5) { false } },
                        floorTiles = emptyList(),
                        hasFirstPlayerMarker = false,
                    ),
                    AzulPlayerBoardSnapshot(
                        userId = "user_a",
                        score = 68,
                        patternLines = emptyList(),
                        wall = List(5) { List(5) { false } },
                        floorTiles = emptyList(),
                        hasFirstPlayerMarker = true,
                    ),
                ),
            ),
        )

        val uiModel = result.toUiModel()

        assertEquals("match_demo", uiModel.matchId)
        assertEquals(listOf("Alice", "Bob"), uiModel.playerPages.map { it.displayName })
        assertEquals(listOf(1, 2), uiModel.playerPages.map { it.place })
        assertTrue(uiModel.playerPages.first().board.hasFirstPlayerMarker)
    }
}
