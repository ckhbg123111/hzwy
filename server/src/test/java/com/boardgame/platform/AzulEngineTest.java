package com.boardgame.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AzulEngineTest {

    private final AzulEngine engine = new AzulEngine();

    @Test
    void newGameDealsCorrectFactoryCountAndTiles() {
        AzulEngine.AzulGameState state = engine.newGame(List.of("p1", "p2"), 7L);

        assertEquals(5, state.factories.size());
        assertTrue(state.factories.stream().allMatch(factory -> factory.size() == 4));
        assertEquals(80, state.bag.size());
        assertTrue(state.centerTiles.isEmpty());
        assertTrue(state.firstPlayerMarkerAvailable);
    }

    @Test
    void factorySelectionMovesRemainderToCenterAndFillsPatternLine() {
        AzulEngine.AzulGameState state = engine.newGame(List.of("p1", "p2"), 11L);
        replaceFactories(state, List.of(
                List.of(AzulEngine.TileColor.BLUE, AzulEngine.TileColor.BLUE, AzulEngine.TileColor.RED, AzulEngine.TileColor.WHITE),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        state.centerTiles.clear();
        state.currentPlayerIndex = 0;

        AzulEngine.AzulTurnResult result = engine.applyAction(
                state,
                "p1",
                new AzulEngine.AzulAction(AzulEngine.SourceType.FACTORY, 0, AzulEngine.TileColor.BLUE, 1));

        AzulEngine.AzulPlayerBoard board = state.players.get(0);
        assertEquals(2, board.patternLines.get(1).count);
        assertEquals(AzulEngine.TileColor.BLUE, board.patternLines.get(1).color);
        assertEquals(List.of(AzulEngine.TileColor.RED, AzulEngine.TileColor.WHITE), state.centerTiles);
        assertEquals("p2", state.players.get(state.currentPlayerIndex).userId);
        assertFalse(result.roundAdvanced());
    }

    @Test
    void centerSelectionClaimsFirstPlayerMarker() {
        AzulEngine.AzulGameState state = engine.newGame(List.of("p1", "p2"), 17L);
        replaceFactories(state, List.of(
                List.of(AzulEngine.TileColor.BLUE),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        state.centerTiles.clear();
        state.centerTiles.add(AzulEngine.TileColor.YELLOW);
        state.centerTiles.add(AzulEngine.TileColor.YELLOW);
        state.firstPlayerMarkerAvailable = true;
        state.currentPlayerIndex = 0;

        engine.applyAction(
                state,
                "p1",
                new AzulEngine.AzulAction(AzulEngine.SourceType.CENTER, -1, AzulEngine.TileColor.YELLOW, -1));

        AzulEngine.AzulPlayerBoard board = state.players.get(0);
        assertEquals(List.of(AzulEngine.TileColor.YELLOW, AzulEngine.TileColor.YELLOW), board.floorTiles);
        assertTrue(board.hasFirstPlayerMarker);
        assertEquals("p1", state.nextRoundStarterUserId);
        assertFalse(state.firstPlayerMarkerAvailable);
    }

    @Test
    void rejectsIllegalPlacementWhenWallAlreadyContainsColor() {
        AzulEngine.AzulGameState state = engine.newGame(List.of("p1", "p2"), 23L);
        replaceFactories(state, List.of(
                List.of(AzulEngine.TileColor.BLUE, AzulEngine.TileColor.BLUE),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        state.centerTiles.clear();
        state.currentPlayerIndex = 0;
        state.players.get(0).wall[0][0] = true;

        assertThrows(PlatformException.class, () -> engine.applyAction(
                state,
                "p1",
                new AzulEngine.AzulAction(AzulEngine.SourceType.FACTORY, 0, AzulEngine.TileColor.BLUE, 0)));
    }

    @Test
    void finishingActionCreatesMatchResult() {
        AzulEngine.AzulGameState state = engine.newGame(List.of("p1", "p2"), 31L);
        replaceFactories(state, List.of(
                List.of(AzulEngine.TileColor.BLUE),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        state.centerTiles.clear();
        state.firstPlayerMarkerAvailable = false;
        state.currentPlayerIndex = 0;
        AzulEngine.AzulPlayerBoard board = state.players.get(0);
        board.score = 10;
        board.wall[0][1] = true;
        board.wall[0][2] = true;
        board.wall[0][3] = true;
        board.wall[0][4] = true;

        AzulEngine.AzulTurnResult result = engine.applyAction(
                state,
                "p1",
                new AzulEngine.AzulAction(AzulEngine.SourceType.FACTORY, 0, AzulEngine.TileColor.BLUE, 0));

        assertTrue(state.finished);
        assertNotNull(result.matchResult());
        assertEquals("p1", result.matchResult().placements.get(0).userId());
        assertEquals(1, result.matchResult().placements.get(0).place());
        assertNotNull(result.matchResult().finalState);
    }

    @Test
    void forceFinishBreaksTieByCompletedRows() {
        AzulEngine.AzulGameState state = engine.newGame(List.of("a", "b"), 41L);
        AzulEngine.AzulPlayerBoard first = state.players.get(0);
        AzulEngine.AzulPlayerBoard second = state.players.get(1);
        first.score = 20;
        second.score = 20;
        fillRow(first.wall, 0);
        fillRow(first.wall, 1);
        fillRow(second.wall, 0);

        PlatformStore.MatchResult result = engine.forceFinish("match1", "room1", state, "force finish");

        assertEquals("a", result.placements.get(0).userId());
        assertEquals(1, result.placements.get(0).place());
        assertEquals("b", result.placements.get(1).userId());
    }

    private void replaceFactories(AzulEngine.AzulGameState state, List<List<AzulEngine.TileColor>> factories) {
        state.factories.clear();
        factories.forEach(factory -> state.factories.add(new ArrayList<>(factory)));
    }

    private void fillRow(boolean[][] wall, int row) {
        for (int column = 0; column < wall[row].length; column++) {
            wall[row][column] = true;
        }
    }
}
