package io.github.mabrur.streamly.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ExoPlayerHolder @Inject constructor(
    @ApplicationContext context: Context,
    mediaSourceFactory: MediaSource.Factory,
    private val downloadManager: DownloadManager,
) : PlayerHolder {

    private var released = false

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    override val player: Player get() = exoPlayer

    override fun setMedia(videoId: String, hlsUrl: String, startPositionMs: Long) {
        if (released) return
        exoPlayer.setMediaItem(mediaItemFor(videoId, hlsUrl), startPositionMs)
        exoPlayer.prepare()
    }

    /**
     * A completed download is played from its own DownloadRequest, never from the original
     * URL.
     *
     * The request carries the stream keys naming the exact rendition that was stored.
     * Handing the player the master playlist instead lets its track selector choose any
     * rendition — including ones that were never downloaded. That looks fine online, where
     * the miss is silently fetched, and fails offline with UnknownHostException. Measured:
     * this was exactly the airplane-mode failure before the lookup was added.
     */
    private fun mediaItemFor(videoId: String, hlsUrl: String): MediaItem {
        val download = runCatching {
            downloadManager.downloadIndex.getDownload(videoId)
        }.getOrNull()

        return if (download?.state == Download.STATE_COMPLETED) {
            download.request.toMediaItem()
        } else {
            MediaItem.fromUri(hlsUrl)
        }
    }

    override fun pause() {
        if (!released) exoPlayer.pause()
    }

    override fun resume() {
        if (!released) exoPlayer.play()
    }

    override fun currentPositionMs(): Long =
        if (released) 0L else exoPlayer.currentPosition

    /** Guarded so a double release — e.g. onCleared after an explicit release — is a no-op. */
    override fun release() {
        if (released) return
        released = true
        exoPlayer.release()
    }
}
