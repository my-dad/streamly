package io.github.mabrur.streamly.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Holds the currently-showing toast message and clears it after [TOAST_DURATION_MS].
 *
 * This is route-local presentation state, which is the point. AGENTS.md requires one-shot
 * events to travel through an `Effect` channel and never through `UiState`; a ViewModel that
 * stored `toastMessage` in its state would have to model "shown, then not shown" as two
 * state emissions and would replay the toast on every configuration change. So the ViewModel
 * emits an Effect, and this holder owns only how long the pill stays on screen.
 */
@Stable
class ToastState internal constructor() {
    var message: String? by mutableStateOf(null)
        private set

    /** Replaces any toast already showing, so rapid taps do not queue up. */
    fun show(message: String) {
        this.message = message
    }

    internal fun clear() {
        message = null
    }
}

const val TOAST_DURATION_MS = 1_600L

@Composable
fun rememberToastState(): ToastState {
    val state = remember { ToastState() }
    val current = state.message

    LaunchedEffect(current) {
        if (current != null) {
            delay(TOAST_DURATION_MS)
            state.clear()
        }
    }
    return state
}
