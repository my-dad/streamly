package io.github.mabrur.streamly.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import io.github.mabrur.streamly.core.designsystem.format.formatDuration
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

/**
 * The control layer drawn over the video stage: back arrow top-left, play/pause centered,
 * seek bar and time along the bottom. Fills whatever box the stage gives it, so the same
 * layer serves the 16:9 portrait stage and the landscape fullscreen one.
 *
 * Placement follows `streamly.dc.html`, which puts play/pause on the video rather than
 * under it. Seek, time and mute are not in the design at all — PRD line 157 requires them,
 * so they go in a bottom bar on the same surface (D-021).
 *
 * Driven by the media3-compose state holders directly against [player] rather than routed
 * through the MVI intent channel — see D-008.
 */
@Composable
fun PlayerControls(
    player: Player,
    onBack: () -> Unit,
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

    Box(modifier = modifier.fillMaxSize()) {
        // White controls over an arbitrary frame are only legible by luck — this video
        // opens on a bright sky. Two gradients, no full-surface dimming of the picture.
        Scrim(Alignment.TopCenter, Modifier.align(Alignment.TopCenter))
        Scrim(Alignment.BottomCenter, Modifier.align(Alignment.BottomCenter))

        OnVideoButton(
            onClick = onBack,
            size = 34.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                // Zero when the bars are hidden, so landscape fullscreen is unaffected.
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        OnVideoButton(
            onClick = playPause::onClick,
            size = 60.dp,
            enabled = playPause.isEnabled,
            modifier = Modifier.align(Alignment.Center),
        ) {
            if (playPause.showPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                // material-icons-core ships no Pause, and pulling in the extended set for
                // one glyph is not worth ~20MB. The design draws it as two bars anyway.
                PauseBars(contentDescription = "Pause")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Slider(
                value = position.toFloat(),
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = {
                    scrubPosition?.let { player.seekTo(it.toLong()) }
                    scrubPosition = null
                },
                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                enabled = duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = StreamlyColors.Accent,
                    inactiveTrackColor = StreamlyColors.OnVideoTrack,
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${formatDuration(position)} / ${formatDuration(duration)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                // A word rather than a glyph, for the same reason as the pause bars:
                // core has no volume icon, and "Muted" is not ambiguous.
                Text(
                    text = if (mute.showMuted) "Muted" else "Mute",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(StreamlyColors.OnVideoFill)
                        .clickable(enabled = mute.isEnabled, onClick = mute::onClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** A gradient fading from the given edge into the picture, to sit controls on. */
@Composable
private fun Scrim(edge: Alignment, modifier: Modifier = Modifier) {
    val stops = listOf(StreamlyColors.Scrim, Color.Transparent)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(
                Brush.verticalGradient(
                    if (edge == Alignment.TopCenter) stops else stops.reversed(),
                ),
            ),
    )
}

/** The design's pause glyph: two rounded white bars. */
@Composable
private fun PauseBars(contentDescription: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 22.dp)
                    .clip(StreamlyShapes.Bar)
                    .background(Color.White),
            )
        }
    }
}

/** The design's translucent white circle, at whichever diameter the caller needs. */
@Composable
private fun OnVideoButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(StreamlyColors.OnVideoFill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
