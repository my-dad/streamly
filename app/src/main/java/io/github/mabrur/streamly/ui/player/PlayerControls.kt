package io.github.mabrur.streamly.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
// The thumb/track slots are the only way to get a thin seek bar; they are still
// experimental in this material3 version.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    player: Player,
    onBack: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
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

    // Auto-hide. Three conditions keep the controls up, and each is a case where hiding
    // them would strand the user: while paused there would be no way to resume, while
    // scrubbing the bar would vanish under the finger, and within AUTO_HIDE_MS of the last
    // touch the user is still working. `touches` exists so a tap that changes none of the
    // other keys — mute, fullscreen — still restarts the timer.
    var visible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    val isScrubbing = scrubPosition != null
    val isPaused = playPause.showPlay
    val touch = { touches++ }

    LaunchedEffect(visible, isPaused, isScrubbing, touches) {
        if (visible && !isPaused && !isScrubbing) {
            delay(AUTO_HIDE_MS)
            visible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The whole stage is the tap target that brings the controls back. No ripple:
            // a ripple across the video would be worse than the problem it signals.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (visible) "Hide player controls" else "Show player controls",
            ) {
                visible = !visible
                touch()
            },
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // White controls over an arbitrary frame are only legible by luck — this video
                // opens on a bright sky. Two gradients, no full-surface dimming of the picture.
                Scrim(Alignment.TopCenter, Modifier.align(Alignment.TopCenter))
                Scrim(Alignment.BottomCenter, Modifier.align(Alignment.BottomCenter))

                OnVideoButton(
                    onClick = { touch(); onBack() },
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
                    onClick = { touch(); playPause.onClick() },
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
                    // M3's default slider is a 16dp-tall expressive control — far too heavy sitting
                    // on a video. Custom thumb and track give the thin bar players actually use.
                    val fraction = if (duration > 0) position.toFloat() / duration else 0f
                    Slider(
                        value = position.toFloat(),
                        onValueChange = { scrubPosition = it },
                        onValueChangeFinished = {
                            scrubPosition?.let { player.seekTo(it.toLong()) }
                            scrubPosition = null
                            touch()
                        },
                        valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                        enabled = duration > 0,
                        modifier = Modifier.height(16.dp),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(StreamlyColors.Accent),
                            )
                        },
                        track = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(StreamlyColors.OnVideoTrack),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(3.dp)
                                        .background(StreamlyColors.Accent),
                                )
                            }
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${formatDuration(position)} / ${formatDuration(duration)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // A word rather than a glyph: core has no volume icon, and "Muted"
                            // is not ambiguous.
                            Text(
                                text = if (mute.showMuted) "Muted" else "Mute",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(StreamlyColors.OnVideoFill)
                                    .clickable(enabled = mute.isEnabled) { touch(); mute.onClick() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            OnVideoButton(onClick = { touch(); onToggleFullscreen() }, size = 34.dp) {
                                FullscreenGlyph(expand = !isFullscreen)
                            }
                        }
                    }
            }
        }
    }
}
}

/** Time with no interaction before the controls fade out, while playing. */
private const val AUTO_HIDE_MS = 3_000L

/**
 * Four corner brackets — pointing outward to enter fullscreen, inward to leave it.
 * Drawn rather than imported: `material-icons-core` has no fullscreen glyph either.
 */
@Composable
private fun FullscreenGlyph(expand: Boolean) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val arm = size.minDimension * 0.36f
        val stroke = 1.5.dp.toPx()
        val inset = if (expand) 0f else arm
        // Each corner is two arms meeting at a right angle. Pointing inward is the same
        // bracket moved in by one arm length and mirrored, which is what `dir` does.
        val dir = if (expand) 1f else -1f

        listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(size.width - inset, inset) to Offset(-1f, 1f),
            Offset(inset, size.height - inset) to Offset(1f, -1f),
            Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
        ).forEach { (corner, sign) ->
            drawLine(
                color = Color.White,
                start = corner,
                end = Offset(corner.x + sign.x * dir * arm, corner.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = corner,
                end = Offset(corner.x, corner.y + sign.y * dir * arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
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
