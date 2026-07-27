package io.github.mabrur.streamly.core.player.download

import androidx.media3.exoplayer.offline.Download
import io.github.mabrur.streamly.domain.model.DownloadStatus

/**
 * Maps a Media3 download state to the domain status.
 *
 * All seven states are handled. STOPPED and RESTARTING both present as Queued: from the
 * user's point of view the download is waiting, and distinguishing them would add a UI
 * state nobody can act on.
 *
 * [percentDownloaded] is clamped because Media3 reports `C.PERCENTAGE_UNSET` (-1f) until
 * the content length is known — rendering that raw gives a bar that jumps backwards.
 */
fun downloadStatusFor(state: Int, percentDownloaded: Float): DownloadStatus =
    when (state) {
        Download.STATE_QUEUED,
        Download.STATE_STOPPED,
        Download.STATE_RESTARTING -> DownloadStatus.Queued

        Download.STATE_DOWNLOADING ->
            DownloadStatus.InProgress(percentDownloaded.coerceIn(0f, 100f))

        Download.STATE_COMPLETED -> DownloadStatus.Completed
        Download.STATE_REMOVING -> DownloadStatus.Removing
        Download.STATE_FAILED -> DownloadStatus.Failed
        else -> DownloadStatus.Failed
    }
