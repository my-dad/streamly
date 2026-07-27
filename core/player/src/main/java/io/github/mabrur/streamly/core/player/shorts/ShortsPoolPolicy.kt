package io.github.mabrur.streamly.core.player.shorts

/**
 * Decides which pages hold a player and which pool slot each page uses.
 *
 * Pure and Android-free so the "at most two players decoding" guarantee is provable by
 * unit test rather than by watching logcat.
 */
object ShortsPoolPolicy {

    /** Two players: the settled page plays, one neighbour pre-buffers. Never a third. */
    const val POOL_SIZE = 2

    /**
     * Pages that should have a player attached: the settled page, plus the next one if it
     * exists. Returns empty for an out-of-range settled index or an empty list.
     */
    fun pagesToHold(settledIndex: Int, itemCount: Int): List<Int> {
        if (itemCount <= 0 || settledIndex !in 0 until itemCount) return emptyList()
        val next = settledIndex + 1
        return if (next < itemCount) listOf(settledIndex, next) else listOf(settledIndex)
    }

    /**
     * Maps a page to a pool slot by parity, so a page and its neighbour can never be
     * assigned the same player — which would make pre-buffering steal the playing surface.
     */
    fun slotFor(pageIndex: Int): Int =
        ((pageIndex % POOL_SIZE) + POOL_SIZE) % POOL_SIZE
}
