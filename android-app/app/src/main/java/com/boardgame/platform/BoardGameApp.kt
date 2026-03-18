package com.boardgame.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val Lobby = "lobby"
    const val Room = "room"
    const val Match = "match"
    const val Result = "result"

    fun match(matchId: String): String = "$Match/$matchId"
    fun result(matchId: String): String = "$Result/$matchId"
}

@Composable
fun BoardGameApp(
    platformViewModel: PlatformViewModel = viewModel(factory = PlatformViewModel.factory()),
) {
    val uiState by platformViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentMatchId = backStackEntry?.arguments?.getString("matchId")
    val activeResult = uiState.activeResult
    val activeMatchId = uiState.activeMatchId

    val targetRoute = remember(activeResult?.matchId, activeMatchId, uiState.room?.id) {
        when {
            activeResult != null -> Routes.result(activeResult.matchId)
            activeMatchId != null -> Routes.match(activeMatchId)
            uiState.room != null -> Routes.Room
            else -> Routes.Lobby
        }
    }

    LaunchedEffect(targetRoute, currentRoute, currentMatchId) {
        if (isCurrentDestination(currentRoute, currentMatchId, targetRoute)) {
            return@LaunchedEffect
        }
        navController.navigate(targetRoute) {
            launchSingleTop = true
            when {
                targetRoute == Routes.Lobby -> popUpTo(Routes.Lobby) { inclusive = false }
                targetRoute == Routes.Room -> popUpTo(Routes.Lobby) { inclusive = false }
                targetRoute.startsWith("${Routes.Match}/") -> popUpTo(Routes.Room) { inclusive = false }
                targetRoute.startsWith("${Routes.Result}/") -> popUpTo(Routes.Lobby) { inclusive = false }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Lobby,
    ) {
        composable(Routes.Lobby) {
            LobbyScreen(
                uiState = uiState,
                onLogin = platformViewModel::loginAsGuest,
                onCreateParty = platformViewModel::createParty,
                onInvite = platformViewModel::inviteMember,
                onCreateRoom = platformViewModel::createRoom,
                onClearError = platformViewModel::clearError,
                onClearNotice = platformViewModel::clearNotice,
            )
        }

        composable(Routes.Room) {
            RoomScreen(
                uiState = uiState,
                onStartGame = platformViewModel::startRoom,
                onClearError = platformViewModel::clearError,
                onClearNotice = platformViewModel::clearNotice,
            )
        }

        composable(
            route = "${Routes.Match}/{matchId}",
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) { entry ->
            val session = uiState.session
            val room = uiState.room
            if (session == null || room == null) {
                EmptyStateScreen(
                    title = stringResource(R.string.match_title),
                    message = stringResource(R.string.match_unavailable),
                )
            } else {
                val matchId = entry.arguments?.getString("matchId").orEmpty()
                MatchScreen(
                    matchId = matchId,
                    authToken = session.token,
                    currentUserId = session.user.id,
                    roomPlayers = room.players,
                    onLoadResult = platformViewModel::loadMatchResult,
                )
            }
        }

        composable(
            route = "${Routes.Result}/{matchId}",
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) { entry ->
            val matchId = entry.arguments?.getString("matchId").orEmpty()
            ResultScreen(
                result = uiState.activeResult,
                matchId = matchId,
                onLoadResult = platformViewModel::loadMatchResult,
                onReturnToLobby = platformViewModel::returnToLobby,
            )
        }
    }
}

private fun isCurrentDestination(currentRoute: String?, currentMatchId: String?, targetRoute: String): Boolean {
    return when {
        currentRoute == null -> false
        currentRoute == targetRoute -> true
        currentRoute == "${Routes.Match}/{matchId}" && currentMatchId != null ->
            targetRoute == Routes.match(currentMatchId)

        currentRoute == "${Routes.Result}/{matchId}" && currentMatchId != null ->
            targetRoute == Routes.result(currentMatchId)

        else -> false
    }
}
