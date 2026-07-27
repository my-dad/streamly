package io.github.mabrur.streamly.core.player.shorts

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Exactly [ShortsPoolPolicy.POOL_SIZE] players, reused across pages.
 *
 * Only the settled page's player is ever playing; the neighbour is prepared and paused.
 * Because there are only two instances, "no more than two decoding" holds by construction.
 */
class ShortsPlayerPool @Inject constructor(
    @ApplicationContext context: Context,
    mediaSourceFactory: MediaSource.Factory,
) : ShortsPool {

    private var released = false

    private val players: List<ExoPlayer> = List(ShortsPoolPolicy.POOL_SIZE) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
    }

    /** Page index currently loaded into each slot, so we can skip redundant re-prepares. */
    private val loadedPage = IntArray(ShortsPoolPolicy.POOL_SIZE) { -1 }

    private var settledIndex: Int = -1

    override fun playerForPage(pageIndex: Int): Player =
        players[ShortsPoolPolicy.slotFor(pageIndex)]

    /**
     * Applies the policy: the settled page plays from the start, the neighbour is prepared
     * and held paused, and any other slot is stopped so nothing decodes off-screen.
     */
    override fun onSettled(settledIndex: Int, urls: List<String>) {
        if (released) return
        this.settledIndex = settledIndex

        val hold = ShortsPoolPolicy.pagesToHold(settledIndex, urls.size)

        // Stop any slot not in the hold set — this is what prevents audio bleed.
        val heldSlots = hold.map(ShortsPoolPolicy::slotFor).toSet()
        players.indices.filterNot { it in heldSlots }.forEach { slot ->
            players[slot].pause()
            players[slot].stop()
            loadedPage[slot] = -1
        }

        hold.forEach { page ->
            val slot = ShortsPoolPolicy.slotFor(page)
            val player = players[slot]

            if (loadedPage[slot] != page) {
                player.setMediaItem(MediaItem.fromUri(urls[page]))
                player.prepare()
                loadedPage[slot] = page
                Log.d(TAG, "slot $slot <- page $page")
            }

            if (page == settledIndex) {
                // Shorts restart from 0 and play unmuted — a deliberate, consistent policy.
                player.volume = 1f
                player.seekTo(0L)
                player.play()
            } else {
                // Neighbour pre-buffers only. Muted as a second line of defence against bleed.
                player.volume = 0f
                player.pause()
            }
        }
    }

    override fun pauseAll() {
        if (released) return
        players.forEach { it.pause() }
    }

    override fun resumeSettled() {
        if (released || settledIndex < 0) return
        players[ShortsPoolPolicy.slotFor(settledIndex)].play()
    }

    /** Idempotent. */
    override fun release() {
        if (released) return
        released = true
        players.forEach { it.release() }
        Log.d(TAG, "pool released")
    }

    private companion object {
        const val TAG = "ShortsPool"
    }
}
