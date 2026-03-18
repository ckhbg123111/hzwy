package com.boardgame.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AzulEngine {

    private static final int[] FLOOR_PENALTIES = {1, 1, 2, 2, 2, 3, 3};
    private static final TileColor[][] WALL_PATTERN = {
            {TileColor.BLUE, TileColor.YELLOW, TileColor.RED, TileColor.BLACK, TileColor.WHITE},
            {TileColor.WHITE, TileColor.BLUE, TileColor.YELLOW, TileColor.RED, TileColor.BLACK},
            {TileColor.BLACK, TileColor.WHITE, TileColor.BLUE, TileColor.YELLOW, TileColor.RED},
            {TileColor.RED, TileColor.BLACK, TileColor.WHITE, TileColor.BLUE, TileColor.YELLOW},
            {TileColor.YELLOW, TileColor.RED, TileColor.BLACK, TileColor.WHITE, TileColor.BLUE}
    };

    AzulGameState newGame(List<String> playerIds) {
        return newGame(playerIds, System.nanoTime());
    }

    AzulGameState newGame(List<String> playerIds, long seed) {
        if (playerIds.size() < 2 || playerIds.size() > 4) {
            throw PlatformException.badRequest("AZUL_PLAYERS_INVALID", "Azul requires 2 to 4 players.");
        }
        AzulGameState state = new AzulGameState();
        state.rngSeed = seed;
        state.roundNumber = 1;
        state.currentPlayerIndex = 0;
        state.firstPlayerMarkerAvailable = true;
        for (String playerId : playerIds) {
            state.players.add(new AzulPlayerBoard(playerId));
        }
        for (TileColor color : TileColor.values()) {
            for (int i = 0; i < 20; i++) {
                state.bag.add(color);
            }
        }
        shuffle(state.bag, state);
        refillFactories(state);
        return state;
    }

    AzulTurnResult applyAction(AzulGameState state, String playerId, AzulAction action) {
        if (state.finished) {
            throw PlatformException.conflict("AZUL_FINISHED", "This Azul match has already finished.");
        }
        AzulPlayerBoard board = currentBoard(state);
        if (!Objects.equals(board.userId, playerId)) {
            throw PlatformException.forbidden("AZUL_TURN_INVALID", "It is not this player's turn.");
        }
        if (action.targetLine() < -1 || action.targetLine() > 4) {
            throw PlatformException.badRequest("AZUL_TARGET_INVALID", "Target line must be -1 or between 0 and 4.");
        }

        int selectedCount;
        if (action.sourceType() == SourceType.FACTORY) {
            selectedCount = takeFromFactory(state, action.sourceIndex(), action.color());
        } else if (action.sourceType() == SourceType.CENTER) {
            selectedCount = takeFromCenter(state, board, action.color());
        } else {
            throw PlatformException.badRequest("AZUL_SOURCE_INVALID", "Unsupported source type.");
        }

        int overflow = placeSelectedTiles(board, action.color(), selectedCount, action.targetLine());
        addFloorTiles(board, action.color(), overflow);

        if (allDisplaysEmpty(state)) {
            PlatformStore.MatchResult result = scoreRoundAndMaybeFinish(state);
            return new AzulTurnResult(state, result, !state.finished, state.finished ? 0 : 30);
        }

        state.currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size();
        return new AzulTurnResult(state, null, false, 30);
    }

    AzulAction defaultAction(AzulGameState state) {
        AzulPlayerBoard board = currentBoard(state);
        for (int factoryIndex = 0; factoryIndex < state.factories.size(); factoryIndex++) {
            for (TileColor color : uniqueColors(state.factories.get(factoryIndex))) {
                for (int line = 0; line < 5; line++) {
                    if (canPlaceOnLine(board, color, line)) {
                        return new AzulAction(SourceType.FACTORY, factoryIndex, color, line);
                    }
                }
                return new AzulAction(SourceType.FACTORY, factoryIndex, color, -1);
            }
        }
        for (TileColor color : uniqueColors(state.centerTiles)) {
            for (int line = 0; line < 5; line++) {
                if (canPlaceOnLine(board, color, line)) {
                    return new AzulAction(SourceType.CENTER, -1, color, line);
                }
            }
            return new AzulAction(SourceType.CENTER, -1, color, -1);
        }
        throw PlatformException.badRequest("AZUL_NO_ACTION", "No legal default action is available.");
    }

    PlatformStore.MatchResult forceFinish(String matchId, String roomId, AzulGameState state, String summary) {
        state.finished = true;
        return buildMatchResult(matchId, roomId, state, summary);
    }

    AzulPublicStateView publicView(AzulGameState state) {
        List<List<TileColor>> factories = state.factories.stream()
                .map(List::copyOf)
                .toList();
        List<AzulPlayerPublicView> players = state.players.stream()
                .map(board -> new AzulPlayerPublicView(
                        board.userId,
                        board.score,
                        board.patternLines.stream()
                                .map(line -> new PatternLinePublicView(line.capacity, line.color, line.count))
                                .toList(),
                        cloneWall(board.wall),
                        List.copyOf(board.floorTiles),
                        board.hasFirstPlayerMarker))
                .toList();
        return new AzulPublicStateView(
                state.roundNumber,
                factories,
                List.copyOf(state.centerTiles),
                state.firstPlayerMarkerAvailable,
                state.bag.size(),
                state.discardPile.size(),
                players);
    }

    private int takeFromFactory(AzulGameState state, int sourceIndex, TileColor color) {
        if (sourceIndex < 0 || sourceIndex >= state.factories.size()) {
            throw PlatformException.badRequest("AZUL_FACTORY_INVALID", "Factory index is out of range.");
        }
        List<TileColor> factory = state.factories.get(sourceIndex);
        int selectedCount = countColor(factory, color);
        if (selectedCount == 0) {
            throw PlatformException.badRequest("AZUL_FACTORY_COLOR_INVALID", "Selected color is not in the factory.");
        }
        List<TileColor> remaining = new ArrayList<>();
        for (TileColor tile : factory) {
            if (tile == color) {
                continue;
            }
            remaining.add(tile);
        }
        factory.clear();
        state.centerTiles.addAll(remaining);
        return selectedCount;
    }

    private int takeFromCenter(AzulGameState state, AzulPlayerBoard board, TileColor color) {
        int selectedCount = countColor(state.centerTiles, color);
        if (selectedCount == 0) {
            throw PlatformException.badRequest("AZUL_CENTER_COLOR_INVALID", "Selected color is not in the center.");
        }
        state.centerTiles.removeIf(tile -> tile == color);
        if (state.firstPlayerMarkerAvailable) {
            board.hasFirstPlayerMarker = true;
            state.firstPlayerMarkerAvailable = false;
            state.nextRoundStarterUserId = board.userId;
        }
        return selectedCount;
    }

    private int placeSelectedTiles(AzulPlayerBoard board, TileColor color, int count, int targetLine) {
        if (targetLine == -1) {
            return count;
        }
        PatternLineState line = board.patternLines.get(targetLine);
        if (!canPlaceOnLine(board, color, targetLine)) {
            throw PlatformException.badRequest("AZUL_LINE_INVALID", "Tiles cannot be placed on the selected pattern line.");
        }
        int accepted = Math.min(count, line.capacity - line.count);
        line.color = color;
        line.count += accepted;
        return count - accepted;
    }

    private boolean canPlaceOnLine(AzulPlayerBoard board, TileColor color, int targetLine) {
        if (targetLine < 0 || targetLine > 4) {
            return false;
        }
        PatternLineState line = board.patternLines.get(targetLine);
        if (line.count >= line.capacity) {
            return false;
        }
        if (line.color != null && line.color != color) {
            return false;
        }
        return !board.wall[targetLine][wallColumnFor(targetLine, color)];
    }

    private void addFloorTiles(AzulPlayerBoard board, TileColor color, int count) {
        for (int index = 0; index < count; index++) {
            board.floorTiles.add(color);
        }
    }

    private boolean allDisplaysEmpty(AzulGameState state) {
        return state.factories.stream().allMatch(List::isEmpty) && state.centerTiles.isEmpty();
    }

    private PlatformStore.MatchResult scoreRoundAndMaybeFinish(AzulGameState state) {
        for (AzulPlayerBoard board : state.players) {
            for (int row = 0; row < board.patternLines.size(); row++) {
                PatternLineState line = board.patternLines.get(row);
                if (line.count == line.capacity && line.color != null) {
                    int column = wallColumnFor(row, line.color);
                    board.wall[row][column] = true;
                    board.score += placementScore(board.wall, row, column);
                    for (int discard = 1; discard < line.count; discard++) {
                        state.discardPile.add(line.color);
                    }
                    line.count = 0;
                    line.color = null;
                }
            }

            int penaltyCount = board.floorTiles.size() + (board.hasFirstPlayerMarker ? 1 : 0);
            board.score = Math.max(0, board.score - floorPenalty(penaltyCount));
            state.discardPile.addAll(board.floorTiles);
            board.floorTiles.clear();
            board.hasFirstPlayerMarker = false;
        }

        boolean anyCompletedRow = state.players.stream().anyMatch(this::hasCompletedRow);
        if (anyCompletedRow) {
            applyEndGameBonuses(state);
            state.finished = true;
            return buildMatchResult(
                    "match_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                    "room-generated",
                    state,
                    "Azul match finished with a completed horizontal wall row.");
        }

        refillFactories(state);
        state.firstPlayerMarkerAvailable = true;
        state.centerTiles.clear();
        state.currentPlayerIndex = indexOfPlayer(state, state.nextRoundStarterUserId);
        state.nextRoundStarterUserId = null;
        state.roundNumber += 1;
        return null;
    }

    private void applyEndGameBonuses(AzulGameState state) {
        for (AzulPlayerBoard board : state.players) {
            board.score += 2 * completedRows(board);
            board.score += 7 * completedColumns(board);
            board.score += 10 * completedColorSets(board);
        }
    }

    private PlatformStore.MatchResult buildMatchResult(
            String matchId,
            String roomId,
            AzulGameState state,
            String summary) {
        List<PlatformStore.Placement> placements = state.players.stream()
                .sorted(Comparator.comparingInt((AzulPlayerBoard board) -> board.score).reversed()
                        .thenComparing(Comparator.comparingInt(this::completedRows).reversed())
                        .thenComparing(board -> board.userId))
                .map(board -> new PlayerScore(
                        board.userId,
                        board.score,
                        completedRows(board)))
                .toList()
                .stream()
                .map(playerScore -> new PlatformStore.Placement(
                        playerScore.userId(),
                        0,
                        playerScore.score(),
                        playerScore.completedRows()))
                .toList();

        List<PlatformStore.Placement> ranked = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            PlatformStore.Placement placement = placements.get(index);
            ranked.add(new PlatformStore.Placement(
                    placement.userId(),
                    index + 1,
                    placement.score(),
                    placement.completedRows()));
        }
        return new PlatformStore.MatchResult(
                matchId,
                roomId,
                PlatformStore.GameType.AZUL,
                java.time.Instant.now(),
                ranked,
                summary,
                publicView(state));
    }

    private void refillFactories(AzulGameState state) {
        state.factories.clear();
        int factoryCount = switch (state.players.size()) {
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> throw PlatformException.badRequest("AZUL_PLAYERS_INVALID", "Azul requires 2 to 4 players.");
        };
        for (int factoryIndex = 0; factoryIndex < factoryCount; factoryIndex++) {
            List<TileColor> factory = new ArrayList<>();
            for (int tileIndex = 0; tileIndex < 4; tileIndex++) {
                TileColor tile = drawTile(state);
                if (tile != null) {
                    factory.add(tile);
                }
            }
            state.factories.add(factory);
        }
    }

    private TileColor drawTile(AzulGameState state) {
        if (state.bag.isEmpty()) {
            if (state.discardPile.isEmpty()) {
                return null;
            }
            state.bag.addAll(state.discardPile);
            state.discardPile.clear();
            shuffle(state.bag, state);
        }
        return state.bag.remove(state.bag.size() - 1);
    }

    private void shuffle(List<TileColor> tiles, AzulGameState state) {
        Collections.shuffle(tiles, new Random(state.rngSeed++));
    }

    private int countColor(List<TileColor> tiles, TileColor color) {
        int count = 0;
        for (TileColor tile : tiles) {
            if (tile == color) {
                count++;
            }
        }
        return count;
    }

    private List<TileColor> uniqueColors(List<TileColor> tiles) {
        return Arrays.stream(TileColor.values())
                .filter(color -> tiles.contains(color))
                .toList();
    }

    private AzulPlayerBoard currentBoard(AzulGameState state) {
        return state.players.get(state.currentPlayerIndex);
    }

    private int wallColumnFor(int row, TileColor color) {
        for (int column = 0; column < WALL_PATTERN[row].length; column++) {
            if (WALL_PATTERN[row][column] == color) {
                return column;
            }
        }
        throw new IllegalArgumentException("Color not found in wall pattern.");
    }

    private int placementScore(boolean[][] wall, int row, int column) {
        int horizontal = 1;
        int vertical = 1;

        for (int current = column - 1; current >= 0 && wall[row][current]; current--) {
            horizontal++;
        }
        for (int current = column + 1; current < 5 && wall[row][current]; current++) {
            horizontal++;
        }
        for (int current = row - 1; current >= 0 && wall[current][column]; current--) {
            vertical++;
        }
        for (int current = row + 1; current < 5 && wall[current][column]; current++) {
            vertical++;
        }

        if (horizontal == 1 && vertical == 1) {
            return 1;
        }
        if (horizontal == 1) {
            return vertical;
        }
        if (vertical == 1) {
            return horizontal;
        }
        return horizontal + vertical;
    }

    private int floorPenalty(int penaltyCount) {
        int capped = Math.min(penaltyCount, FLOOR_PENALTIES.length);
        int total = 0;
        for (int index = 0; index < capped; index++) {
            total += FLOOR_PENALTIES[index];
        }
        return total;
    }

    private boolean hasCompletedRow(AzulPlayerBoard board) {
        return completedRows(board) > 0;
    }

    private int completedRows(AzulPlayerBoard board) {
        int count = 0;
        for (boolean[] row : board.wall) {
            boolean complete = true;
            for (boolean filled : row) {
                if (!filled) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                count++;
            }
        }
        return count;
    }

    private int completedColumns(AzulPlayerBoard board) {
        int count = 0;
        for (int column = 0; column < 5; column++) {
            boolean complete = true;
            for (int row = 0; row < 5; row++) {
                if (!board.wall[row][column]) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                count++;
            }
        }
        return count;
    }

    private int completedColorSets(AzulPlayerBoard board) {
        int count = 0;
        for (TileColor color : TileColor.values()) {
            boolean complete = true;
            for (int row = 0; row < 5; row++) {
                int column = wallColumnFor(row, color);
                if (!board.wall[row][column]) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                count++;
            }
        }
        return count;
    }

    private int indexOfPlayer(AzulGameState state, String userId) {
        if (userId == null) {
            return 0;
        }
        for (int index = 0; index < state.players.size(); index++) {
            if (Objects.equals(state.players.get(index).userId, userId)) {
                return index;
            }
        }
        return 0;
    }

    private boolean[][] cloneWall(boolean[][] wall) {
        boolean[][] copy = new boolean[wall.length][];
        for (int row = 0; row < wall.length; row++) {
            copy[row] = Arrays.copyOf(wall[row], wall[row].length);
        }
        return copy;
    }

    enum TileColor {
        BLUE,
        YELLOW,
        RED,
        BLACK,
        WHITE
    }

    enum SourceType {
        FACTORY,
        CENTER
    }

    static final class AzulGameState {
        public final List<AzulPlayerBoard> players = new ArrayList<>();
        public final List<List<TileColor>> factories = new ArrayList<>();
        public final List<TileColor> centerTiles = new ArrayList<>();
        public final List<TileColor> bag = new ArrayList<>();
        public final List<TileColor> discardPile = new ArrayList<>();
        public boolean firstPlayerMarkerAvailable;
        public String nextRoundStarterUserId;
        public int currentPlayerIndex;
        public int roundNumber;
        public boolean finished;
        public long rngSeed;
    }

    static final class AzulPlayerBoard {
        public final String userId;
        public int score;
        public final List<PatternLineState> patternLines = new ArrayList<>();
        public final boolean[][] wall = new boolean[5][5];
        public final List<TileColor> floorTiles = new ArrayList<>();
        public boolean hasFirstPlayerMarker;

        AzulPlayerBoard(String userId) {
            this.userId = userId;
            for (int capacity = 1; capacity <= 5; capacity++) {
                patternLines.add(new PatternLineState(capacity));
            }
        }
    }

    static final class PatternLineState {
        public final int capacity;
        public TileColor color;
        public int count;

        PatternLineState(int capacity) {
            this.capacity = capacity;
        }
    }

    record AzulAction(SourceType sourceType, int sourceIndex, TileColor color, int targetLine) {
    }

    record AzulTurnResult(
            AzulGameState state,
            PlatformStore.MatchResult matchResult,
            boolean roundAdvanced,
            int nextTurnSeconds) {
    }

    record PatternLinePublicView(int capacity, TileColor color, int count) {
    }

    record AzulPlayerPublicView(
            String userId,
            int score,
            List<PatternLinePublicView> patternLines,
            boolean[][] wall,
            List<TileColor> floorTiles,
            boolean hasFirstPlayerMarker) {
    }

    record AzulPublicStateView(
            int roundNumber,
            List<List<TileColor>> factories,
            List<TileColor> centerTiles,
            boolean firstPlayerMarkerAvailable,
            int bagCount,
            int discardCount,
            List<AzulPlayerPublicView> players) {
    }

    private record PlayerScore(String userId, int score, int completedRows) {
    }
}
