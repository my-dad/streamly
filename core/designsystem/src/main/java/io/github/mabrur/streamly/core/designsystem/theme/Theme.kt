package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Dynamic colour is deliberately NOT used: the design specifies a fixed accent, and
 * letting the device wallpaper repaint the app would defeat the point of shipping a design.
 *
 * A single light scheme is used. The design has no dark variant — Shorts and Player are
 * dark by composition (they paint [StreamlyColors.VideoBackground] directly), not by theme.
 */
private val StreamlyColorScheme = lightColorScheme(
    primary = StreamlyColors.Accent,
    onPrimary = StreamlyColors.Surface,
    secondary = StreamlyColors.Accent,
    onSecondary = StreamlyColors.Surface,
    background = StreamlyColors.FeedBackground,
    onBackground = StreamlyColors.Ink,
    surface = StreamlyColors.Surface,
    onSurface = StreamlyColors.Ink,
    surfaceVariant = StreamlyColors.NeutralFill,
    onSurfaceVariant = StreamlyColors.Muted,
    error = StreamlyColors.Danger,
    onError = StreamlyColors.Surface,
    outlineVariant = StreamlyColors.Divider,
)

@Composable
fun StreamlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamlyColorScheme,
        typography = StreamlyType,
        shapes = StreamlyShapes.material,
        content = content,
    )
}
