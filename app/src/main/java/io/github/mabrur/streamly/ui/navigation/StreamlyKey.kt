package io.github.mabrur.streamly.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The app's navigation keys.
 *
 * Every key must be [Serializable]: `rememberNavBackStack` persists the back stack
 * through kotlinx-serialization, so a key without the annotation loses the stack on
 * process death. [StreamlyKeyTest] guards this.
 *
 * Sign-out is a dialog over Profile, not a route — see docs/decisions.md D-004.
 */
@Serializable
sealed interface StreamlyKey : NavKey {

    @Serializable
    data object Onboarding : StreamlyKey

    @Serializable
    data object Home : StreamlyKey

    @Serializable
    data object Shorts : StreamlyKey

    @Serializable
    data object Downloads : StreamlyKey

    @Serializable
    data object Profile : StreamlyKey

    @Serializable
    data class Player(val videoId: String) : StreamlyKey
}
