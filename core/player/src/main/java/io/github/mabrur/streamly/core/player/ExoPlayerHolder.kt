package io.github.mabrur.streamly.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ExoPlayerHolder @Inject constructor(
    @ApplicationContext context: Context,
    mediaSourceFactory: MediaSource.Factory,
) : PlayerHolder {

    private var released = false

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    override val player: Player get() = exoPlayer

    override fun setMedia(hlsUrl: String, startPositionMs: Long) {
        if (released) return
        exoPlayer.setMediaItem(MediaItem.fromUri(hlsUrl), startPositionMs)
        exoPlayer.prepare()
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
