package io.github.mabrur.streamly.ui.shorts

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.core.designsystem.format.formatCompactCount
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.Short

@Immutable
data class ShortUi(
    val id: String,
    val title: String,
    val channelName: String,
    val hlsUrl: String,
    val likeLabel: String,
    val commentLabel: String,
)

fun Short.toUi(): ShortUi = ShortUi(
    id = id,
    title = title,
    channelName = channelName,
    hlsUrl = hlsUrl,
    likeLabel = formatCompactCount(likeCount),
    commentLabel = formatCompactCount(commentCount),
)

@Immutable
data class ShortsUiState(
    val isLoading: Boolean = false,
    val shorts: List<ShortUi> = emptyList(),
    val settledIndex: Int = 0,
    val error: AppError? = null,
)

sealed interface ShortsIntent {
    data object Retry : ShortsIntent

    /** Emitted from `pagerState.settledPage` only — never from `currentPage`. */
    data class PageSettled(val index: Int) : ShortsIntent
}
