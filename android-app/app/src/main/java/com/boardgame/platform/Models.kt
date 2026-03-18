package com.boardgame.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserView(
    val id: String,
    val displayName: String,
    val kind: String,
    val phoneNumber: String? = null,
    val createdAt: String,
)

@Serializable
data class AuthSessionResponse(
    val token: String,
    val user: UserView,
)

@Serializable
data class PartyView(
    val id: String,
    val leaderUserId: String,
    val members: List<UserView> = emptyList(),
    val state: String,
    val activeRoomId: String? = null,
    val createdAt: String,
)

@Serializable
data class RoomView(
    val id: String,
    val hostUserId: String,
    val gameType: String,
    val targetPlayers: Int,
    val visibility: String,
    val allowFill: Boolean,
    val status: String,
    val players: List<UserView> = emptyList(),
    val sourcePartyId: String? = null,
    val matchId: String? = null,
    val createdAt: String,
)

@Serializable
data class QueueStatusView(
    val status: String,
    val partyId: String,
    val roomId: String? = null,
    val matchId: String? = null,
    val message: String,
)

@Serializable
data class MatchFoundView(
    val roomId: String,
    val matchId: String,
    val gameType: String,
)

@Serializable
enum class AzulColor {
    BLUE,
    YELLOW,
    RED,
    BLACK,
    WHITE,
}

@Serializable
enum class AzulSourceType {
    FACTORY,
    CENTER,
}

@Serializable
data class AzulPatternLine(
    val capacity: Int,
    val color: AzulColor? = null,
    val count: Int,
)

@Serializable
data class AzulPlayerBoardSnapshot(
    val userId: String,
    val score: Int,
    val patternLines: List<AzulPatternLine> = emptyList(),
    val wall: List<List<Boolean>> = emptyList(),
    val floorTiles: List<AzulColor> = emptyList(),
    val hasFirstPlayerMarker: Boolean = false,
)

@Serializable
data class AzulSnapshot(
    val roundNumber: Int,
    val factories: List<List<AzulColor>> = emptyList(),
    val centerTiles: List<AzulColor> = emptyList(),
    val firstPlayerMarkerAvailable: Boolean,
    val bagCount: Int,
    val discardCount: Int,
    val players: List<AzulPlayerBoardSnapshot> = emptyList(),
)

@Serializable
data class GameSnapshotView(
    val gameId: String,
    val gameType: String,
    val phase: String,
    val stateVersion: Int,
    val currentPlayerId: String? = null,
    val deadlineAt: String? = null,
    val state: AzulSnapshot? = null,
)

@Serializable
data class PlacementView(
    val userId: String,
    val displayName: String,
    val place: Int,
    val score: Int,
    val completedRows: Int,
)

@Serializable
data class MatchResultView(
    val matchId: String,
    val roomId: String,
    val gameType: String,
    val placements: List<PlacementView> = emptyList(),
    val finishedAt: String,
    val summary: String,
    val finalState: AzulSnapshot? = null,
)

@Serializable
data class RankingEntryView(
    val userId: String,
    val displayName: String,
    val totalGames: Int,
    val wins: Int,
    val firstPlaceRate: Double,
    val averageRank: Double,
    val recentPlacements: List<Int> = emptyList(),
)

@Serializable
data class SystemNoticeView(
    val code: String,
    val message: String,
)

@Serializable
data class GameActionAckView(
    val gameId: String,
    val clientSeq: String? = null,
    val stateVersion: Int,
    val automatic: Boolean,
)

@Serializable
data class GameActionRejectedView(
    val gameId: String,
    val clientSeq: String? = null,
    val reason: String,
)

@Serializable
data class ServerRealtimeMessage(
    val type: String,
    val payload: JsonElement? = null,
    val sentAt: String? = null,
)

@Serializable
data class GuestAuthRequest(
    val deviceId: String,
    val displayName: String,
)

@Serializable
data class PartyInviteRequest(
    val userId: String,
)

@Serializable
data class CreateRoomRequest(
    val gameType: String = "AZUL",
    val targetPlayers: Int = 2,
    val visibility: String = "PUBLIC",
    val allowFill: Boolean = true,
)

@Serializable
data class GameActionRequest(
    val gameId: String,
    val actionType: String = "TAKE_TILES",
    val payload: JsonElement,
    val clientSeq: String,
)

@Serializable
data class AzulMovePayload(
    val sourceType: AzulSourceType,
    val sourceIndex: Int,
    val color: AzulColor,
    val targetLine: Int,
)

data class AzulTileGroup(
    val sourceType: AzulSourceType,
    val sourceIndex: Int,
    val color: AzulColor,
    val count: Int,
)

data class AzulSelectionState(
    val group: AzulTileGroup,
)

data class AzulLegalTarget(
    val lineIndex: Int? = null,
    val label: String,
) {
    val isFloor: Boolean
        get() = lineIndex == null
}

data class MatchBoardPage(
    val userId: String,
    val displayName: String,
    val board: AzulPlayerBoardSnapshot,
    val isSelf: Boolean,
)

data class ResultBoardPage(
    val userId: String,
    val displayName: String,
    val place: Int,
    val score: Int,
    val completedRows: Int,
    val board: AzulPlayerBoardSnapshot,
)

data class MatchResultUiModel(
    val matchId: String,
    val roomId: String,
    val gameType: String,
    val placements: List<PlacementView>,
    val finishedAt: String,
    val summary: String,
    val finalState: AzulSnapshot?,
    val playerPages: List<ResultBoardPage>,
)

enum class RealtimeConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class PlatformUiState(
    val loading: Boolean = false,
    val session: AuthSessionResponse? = null,
    val party: PartyView? = null,
    val room: RoomView? = null,
    val activeMatchId: String? = null,
    val activeResult: MatchResultUiModel? = null,
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
    val connectionStatus: RealtimeConnectionStatus = RealtimeConnectionStatus.DISCONNECTED,
)

data class MatchUiState(
    val loading: Boolean = true,
    val snapshot: GameSnapshotView? = null,
    val selectedSource: AzulSelectionState? = null,
    val legalTargets: List<AzulLegalTarget> = emptyList(),
    val pendingClientSeq: String? = null,
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
    val connectionStatus: RealtimeConnectionStatus = RealtimeConnectionStatus.DISCONNECTED,
)
