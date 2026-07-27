package io.github.mabrur.streamly.domain.model

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class InProgress(val percent: Float) : DownloadStatus
    data object Completed : DownloadStatus
    data object Failed : DownloadStatus
    data object Removing : DownloadStatus
}

data class DownloadItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
)
