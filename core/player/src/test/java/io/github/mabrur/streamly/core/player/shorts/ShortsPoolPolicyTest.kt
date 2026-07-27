package io.github.mabrur.streamly.core.player.shorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsPoolPolicyTest {

    @Test
    fun `holds the settled page and its next neighbour`() {
        assertEquals(listOf(0, 1), ShortsPoolPolicy.pagesToHold(settledIndex = 0, itemCount = 5))
        assertEquals(listOf(2, 3), ShortsPoolPolicy.pagesToHold(settledIndex = 2, itemCount = 5))
    }

    @Test
    fun `holds only the settled page at the end of the list`() {
        assertEquals(listOf(4), ShortsPoolPolicy.pagesToHold(settledIndex = 4, itemCount = 5))
    }

    @Test
    fun `holds only the settled page when there is a single item`() {
        assertEquals(listOf(0), ShortsPoolPolicy.pagesToHold(settledIndex = 0, itemCount = 1))
    }

    @Test
    fun `holds nothing when the list is empty`() {
        assertEquals(emptyList<Int>(), ShortsPoolPolicy.pagesToHold(settledIndex = 0, itemCount = 0))
    }

    @Test
    fun `never holds more pages than the pool has players`() {
        for (settled in 0 until 20) {
            val held = ShortsPoolPolicy.pagesToHold(settled, itemCount = 20)
            assertTrue("held $held for settled=$settled", held.size <= ShortsPoolPolicy.POOL_SIZE)
        }
    }

    @Test
    fun `never holds an out-of-range page`() {
        for (settled in 0 until 8) {
            ShortsPoolPolicy.pagesToHold(settled, itemCount = 8).forEach {
                assertTrue("page $it out of range", it in 0 until 8)
            }
        }
    }

    @Test
    fun `an out-of-range settled index yields nothing`() {
        assertEquals(emptyList<Int>(), ShortsPoolPolicy.pagesToHold(settledIndex = 9, itemCount = 5))
        assertEquals(emptyList<Int>(), ShortsPoolPolicy.pagesToHold(settledIndex = -1, itemCount = 5))
    }

    @Test
    fun `adjacent pages never share a pool slot`() {
        for (page in 0 until 20) {
            assertTrue(ShortsPoolPolicy.slotFor(page) != ShortsPoolPolicy.slotFor(page + 1))
        }
    }

    @Test
    fun `slots stay within the pool`() {
        for (page in 0 until 20) {
            assertTrue(ShortsPoolPolicy.slotFor(page) in 0 until ShortsPoolPolicy.POOL_SIZE)
        }
    }
}
