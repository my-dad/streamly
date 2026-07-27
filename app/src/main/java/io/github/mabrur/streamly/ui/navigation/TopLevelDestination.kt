package io.github.mabrur.streamly.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mabrur.streamly.core.designsystem.R

/**
 * Destinations reachable from the bottom bar.
 *
 * Player is deliberately absent: it pushes over the bar and hides it.
 * Onboarding is absent because it precedes the bar entirely.
 */
enum class TopLevelDestination(
    val key: StreamlyKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(StreamlyKey.Home, R.string.nav_home, Icons.Filled.Home),
    Shorts(StreamlyKey.Shorts, R.string.nav_shorts, Icons.Filled.PlayArrow),
    Downloads(StreamlyKey.Downloads, R.string.nav_downloads, Icons.Filled.List),
    Profile(StreamlyKey.Profile, R.string.nav_profile, Icons.Filled.Person),
}
