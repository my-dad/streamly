package io.github.mabrur.streamly.core.player.shorts

import androidx.media3.common.Player

/** Seam over [ShortsPlayerPool] so ShortsViewModel stays unit-testable. */
interface ShortsPool {
    fun playerForPage(pageIndex: Int): Player
    fun onSettled(settledIndex: Int, urls: List<String>)
    fun pauseAll()
    fun resumeSettled()
    fun release()
}
