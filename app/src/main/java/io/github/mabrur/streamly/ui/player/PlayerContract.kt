package io.github.mabrur.streamly.ui.player

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.ui.home.VideoUi

@Immutable
data class PlayerUiState(
    val isLoading: Boolean = false,
    val video: VideoUi? = null,
    val hlsUrl: String? = null,
    val related: List<VideoUi> = emptyList(),
    /** True once this video has a completed download, so playback is coming off disk. */
    val isPlayingOffline: Boolean = false,
    /** "Download", "Downloaded", or "42%" — driven by the real download state. */
    val downloadLabel: String = "Download",
    /** Local, non-persisted. There is no subscription backend; the PRD permits the stub. */
    val isSubscribed: Boolean = false,
    val isLiked: Boolean = false,
    val error: AppError? = null,
)

sealed interface PlayerIntent {
    /**
     * Starts (or restarts) the screen for [videoId].
     *
     * The id arrives by intent rather than through `SavedStateHandle`: Nav3 does not
     * populate a ViewModel's SavedStateHandle from route keys the way Nav2 did, so
     * reading it there would yield null and leave the screen permanently in its error
     * state. The route sends this from a `LaunchedEffect` keyed on the id.
     */
    data class Load(val videoId: String) : PlayerIntent
    data object Retry : PlayerIntent
    data object DownloadClicked : PlayerIntent
    data object SubscribeToggled : PlayerIntent
    data object LikeToggled : PlayerIntent
    data object ShareClicked : PlayerIntent
    data class RelatedClicked(val videoId: String) : PlayerIntent
}

sealed interface PlayerEffect {
    /** Handled at the nav host by *replacing* the top key — see D-005 rationale. */
    data class OpenVideo(val videoId: String) : PlayerEffect
    data object DownloadStarted : PlayerEffect
    data object DownloadFailed : PlayerEffect
    data object LinkCopied : PlayerEffect
}
