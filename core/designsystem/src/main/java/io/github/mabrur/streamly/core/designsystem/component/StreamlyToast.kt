package io.github.mabrur.streamly.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

/**
 * The dark pill toast from the design. Sits above the tab bar.
 *
 * Renders whatever it is given and nothing more. The message is delivered by the screen's
 * `Effect` channel and held for the duration of the animation by [rememberToastState] —
 * deliberately *not* by the screen's `UiState`, since a toast is a one-shot event and
 * AGENTS.md requires those to travel by Effect rather than by state.
 */
@Composable
fun StreamlyToastHost(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(StreamlyShapes.Pill)
                .background(StreamlyColors.ToastBackground)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = StreamlyColors.Surface,
            )
        }
    }
}
