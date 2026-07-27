package io.github.mabrur.streamly.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import io.github.mabrur.streamly.core.designsystem.format.formatDuration

/**
 * Transport controls driven by the media3-compose state holders directly against
 * [player], rather than routed through the MVI intent channel — see D-008.
 */
@Composable
fun PlayerControls(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val playPause = rememberPlayPauseButtonState(player)
    val mute = rememberMuteButtonState(player)
    val progress = rememberProgressStateWithTickInterval(
        player = player,
        tickIntervalMs = 500L,
        scope = scope,
    )

    // While the user drags, show the drag position instead of the player's.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    val duration = progress.durationMs.coerceAtLeast(0L)
    val position = scrubPosition?.toLong() ?: progress.currentPositionMs.coerceAtLeast(0L)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Slider(
            value = position.toFloat(),
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { player.seekTo(it.toLong()) }
                scrubPosition = null
            },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            enabled = duration > 0,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
                Icon(
                    imageVector = if (playPause.showPlay) Icons.Filled.PlayArrow else Icons.Filled.Clear,
                    contentDescription = if (playPause.showPlay) "Play" else "Pause",
                )
            }
            IconButton(onClick = mute::onClick, enabled = mute.isEnabled) {
                Icon(
                    imageVector = if (mute.showMuted) Icons.Filled.Clear else Icons.Filled.Check,
                    contentDescription = if (mute.showMuted) "Unmute" else "Mute",
                )
            }
            Text(
                text = "${formatDuration(position)} / ${formatDuration(duration)}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
