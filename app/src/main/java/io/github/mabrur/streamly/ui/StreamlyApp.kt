package io.github.mabrur.streamly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(startKey)
    val currentKey = backStack.lastOrNull()
    val showBottomBar = TopLevelDestination.entries.any { it.key == currentKey }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // The top inset is deliberately NOT consumed here. Home's app bar and the Shorts
        // pager both paint edge to edge behind the status bar; screens that need the inset
        // apply statusBarsPadding() themselves. Consuming it in the Scaffold would leave a
        // white strip above Home's accent bar with no way for the screen to reclaim it.
        contentWindowInsets = WindowInsets.navigationBars,
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
        // The bar's height must never reach an entry through the Scaffold's padding.
        // Pushing Player hides the bar while the outgoing entry is still composed, so
        // that entry's viewport would grow by one bar height mid-transition — and a
        // LazyColumn scrolled to its end clamps to the new maximum, permanently losing
        // up to a bar height of scroll position. The saveable decorator then stores and
        // restores that already-wrong offset faithfully. Measured, see D-027.
        //
        // So the entries are padded by what their own key implies instead: the window
        // inset here, which does not move, and the bar height added per top-level entry
        // below. Reading it off the Scaffold keeps it a measurement rather than a
        // hardcoded 80.dp that a Material version could silently invalidate.
        val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Latched, not tracked. On the frame the bar re-enters composition the Scaffold
        // measures it at zero height, and tracking that would hand the returning entry a
        // full-height viewport for exactly one frame — which is all a LazyColumn needs to
        // clamp. Keeping the last non-zero measurement means the reservation only ever
        // appears, never blinks. rememberSaveable so a rotation cannot reset it to zero
        // either, which would clamp the restored position the same way.
        var barHeightDp by rememberSaveable { mutableFloatStateOf(0f) }
        SideEffect {
            val measured = (innerPadding.calculateBottomPadding() - insetBottom).value
            if (measured > 0f) barHeightDp = measured
        }
        val topLevelPadding = Modifier.padding(bottom = barHeightDp.dp)

        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                bottom = insetBottom,
            ),
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
                        modifier = topLevelPadding,
                        onOpenPlayer = { videoId ->
                            backStack.add(StreamlyKey.Player(videoId))
                        },
                        // Same reset-the-stack behaviour as tapping the Profile tab, so
                        // the avatar and the tab cannot disagree about the back stack.
                        onOpenProfile = {
                            backStack.clear()
                            backStack.add(StreamlyKey.Profile)
                        },
                    )
                }
                entry<StreamlyKey.Shorts> { ShortsRoute(modifier = topLevelPadding) }
                entry<StreamlyKey.Downloads> {
                    DownloadsRoute(
                        modifier = topLevelPadding,
                        onOpenPlayer = { videoId -> backStack.add(StreamlyKey.Player(videoId)) },
                    )
                }
                entry<StreamlyKey.Profile> { ProfileRoute(modifier = topLevelPadding) }
                entry<StreamlyKey.Player> { key ->
                    PlayerRoute(
                        videoId = key.videoId,
                        windowSizeClass = windowSizeClass,
                        // Same pop as the system back gesture, so the on-video arrow and
                        // the gesture cannot disagree about where Back goes.
                        onBack = { backStack.removeLastOrNull() },
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
