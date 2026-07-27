package io.github.mabrur.streamly.ui.downloads

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.DownloadStatus

@Immutable
data class DownloadRowUi(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val status: DownloadStatus,
    val sizeLabel: String,
)

@Immutable
data class DownloadsUiState(
    val isLoading: Boolean = false,
    val items: List<DownloadRowUi> = emptyList(),
    val storageLabel: String = "",
    val error: AppError? = null,
)

sealed interface DownloadsIntent {
    data class RemoveClicked(val videoId: String) : DownloadsIntent
    data class PlayClicked(val videoId: String) : DownloadsIntent
}

sealed interface DownloadsEffect {
    data class OpenPlayer(val videoId: String) : DownloadsEffect
    data class ShowToast(val message: String) : DownloadsEffect
}
