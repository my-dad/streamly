package io.github.mabrur.streamly.domain.repository

import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    /** Emits the current download set whenever any download changes state. */
    val downloads: Flow<List<DownloadItem>>

    suspend fun download(video: Video)

    suspend fun remove(videoId: String)
}
