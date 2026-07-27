package io.github.mabrur.streamly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.mabrur.streamly.ui.downloads.DownloadsRoute
import io.github.mabrur.streamly.ui.home.HomeRoute
import io.github.mabrur.streamly.ui.navigation.StreamlyKey
import io.github.mabrur.streamly.ui.navigation.TopLevelDestination
import io.github.mabrur.streamly.ui.navigation.startKeyFor
import io.github.mabrur.streamly.ui.onboarding.OnboardingRoute
import io.github.mabrur.streamly.ui.player.PlayerRoute
import io.github.mabrur.streamly.ui.profile.ProfileRoute
import io.github.mabrur.streamly.ui.shorts.ShortsRoute

@Composable
fun StreamlyApp(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val startKey = startKeyFor(sessionState)

    if (startKey == null) {
        // Session still resolving. Showing Onboarding here would flash it at a
        // signed-in user on every cold start.
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Keying on the start destination means sign-out rebuilds the host with a fresh
    // stack rooted at Onboarding, and sign-in rebuilds it rooted at Home. That is the
    // "clear, don't push" requirement without any manual back-stack surgery.
    key(startKey) {
        StreamlyNavHost(
            startKey = startKey,
            windowSizeClass = windowSizeClass,
            modifier = modifier,
        )
    }
}

@Composable
private fun StreamlyNavHost(
    startKey: StreamlyKey,
    @Suppress("UNUSED_PARAMETER") windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(startKey)
    val currentKey = backStack.lastOrNull()
    val showBottomBar = TopLevelDestination.entries.any { it.key == currentKey }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = StreamlyColors.Surface,
                    tonalElevation = 0.dp,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentKey == destination.key
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentKey != destination.key) {
                                    // Top-level switches reset the stack rather than
                                    // stacking destinations, keeping Back predictable.
                                    backStack.clear()
                                    backStack.add(destination.key)
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = stringResource(destination.labelRes),
                                    tint = if (selected) {
                                        StreamlyColors.Accent
                                    } else {
                                        StreamlyColors.TabInactive
                                    },
                                )
                            },
                            // The design labels nothing in the bar; contentDescription
                            // above is what keeps the tabs reachable to a screen reader.
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = { backStack.removeLastOrNull() },
            // REQUIRED — NavDisplay's default decorators do NOT include ViewModel
            // scoping, and navigation3-ui does not even depend on the artifact that
            // provides it. Without this line every ViewModel is Activity-scoped,
            // onCleared() never fires on pop, and the ExoPlayer leaks.
            // See docs/decisions.md D-007.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider<NavKey> {
                entry<StreamlyKey.Onboarding> { OnboardingRoute() }
                entry<StreamlyKey.Home> {
                    HomeRoute(
                        onOpenPlayer = { videoId ->
                            backStack.add(StreamlyKey.Player(videoId))
                        },
                    )
                }
                entry<StreamlyKey.Shorts> { ShortsRoute() }
                entry<StreamlyKey.Downloads> {
                    DownloadsRoute(
                        onOpenPlayer = { videoId -> backStack.add(StreamlyKey.Player(videoId)) },
                    )
                }
                entry<StreamlyKey.Profile> { ProfileRoute() }
                entry<StreamlyKey.Player> { key ->
                    PlayerRoute(
                        videoId = key.videoId,
                        // Replaces the top key rather than pushing, so Back returns to
                        // Home instead of walking a chain of Player entries.
                        onOpenVideo = { videoId ->
                            backStack.removeLastOrNull()
                            backStack.add(StreamlyKey.Player(videoId))
                        },
                    )
                }
            },
        )
    }
}
