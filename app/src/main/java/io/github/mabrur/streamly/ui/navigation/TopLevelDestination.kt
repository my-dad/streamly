package io.github.mabrur.streamly.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
    @param:DrawableRes val iconRes: Int,
) {
    Home(StreamlyKey.Home, R.string.nav_home, R.drawable.ic_tab_home),
    Shorts(StreamlyKey.Shorts, R.string.nav_shorts, R.drawable.ic_tab_shorts),
    Downloads(StreamlyKey.Downloads, R.string.nav_downloads, R.drawable.ic_tab_downloads),
    Profile(StreamlyKey.Profile, R.string.nav_profile, R.drawable.ic_tab_profile),
}
