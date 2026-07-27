package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted from `streamly.dc.html`.
 *
 * Values are the design's literals; nothing else in the app may declare a hex colour.
 */
object StreamlyColors {
    val Accent = Color(0xFF7C3AED)
    val AccentGradientEnd = Color(0xFF5B56E0)

    /** Primary text. */
    val Ink = Color(0xFF14162E)

    /** Secondary text and inactive tab labels. */
    val Muted = Color(0xFF7A7F95)
    val TabInactive = Color(0xFFA6A9BD)

    val Surface = Color(0xFFFFFFFF)

    /** Feed background — deliberately not pure white. */
    val FeedBackground = Color(0xFFF4F5FA)

    /** Neutral button fill. */
    val NeutralFill = Color(0xFFF0F1F7)

    /** Inactive chip fill. */
    val ChipFill = Color(0xFFECEEF5)

    /** Video surfaces, Shorts background. */
    val VideoBackground = Color(0xFF0D0E24)

    val Ready = Color(0xFF22C55E)

    /** The pulsing "Playing" dot on Shorts. */
    val LiveDot = Color(0xFFFF4D4D)
    val Danger = Color(0xFFE6503F)

    val PlaceholderStart = Color(0xFFDFE1EE)
    val PlaceholderEnd = Color(0xFFC9CCE4)
    val AvatarPlaceholder = Color(0xFFD7D9EA)

    val Divider = Color(0x0F000000)
    val Scrim = Color(0x8C000000)
    val ToastBackground = Color(0xEB14162E)
}
