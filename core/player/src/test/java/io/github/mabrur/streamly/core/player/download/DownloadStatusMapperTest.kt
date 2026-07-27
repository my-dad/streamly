package io.github.mabrur.streamly.core.player.download

import androidx.media3.exoplayer.offline.Download
import io.github.mabrur.streamly.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatusMapperTest {

    @Test
    fun `queued maps to Queued`() {
        assertEquals(DownloadStatus.Queued, downloadStatusFor(Download.STATE_QUEUED, 0f))
    }

    @Test
    fun `stopped maps to Queued`() {
        assertEquals(DownloadStatus.Queued, downloadStatusFor(Download.STATE_STOPPED, 12f))
    }

    @Test
    fun `restarting maps to Queued`() {
        assertEquals(DownloadStatus.Queued, downloadStatusFor(Download.STATE_RESTARTING, 30f))
    }

    @Test
    fun `downloading maps to InProgress with the reported percent`() {
        val status = downloadStatusFor(Download.STATE_DOWNLOADING, 42.5f)

        assertTrue(status is DownloadStatus.InProgress)
        assertEquals(42.5f, (status as DownloadStatus.InProgress).percent, 0.001f)
    }

    @Test
    fun `an unset percent is clamped to zero rather than going negative`() {
        // Media3 reports C.PERCENTAGE_UNSET (-1f) until the content length is known.
        val status = downloadStatusFor(Download.STATE_DOWNLOADING, -1f)

        assertEquals(0f, (status as DownloadStatus.InProgress).percent, 0.001f)
    }

    @Test
    fun `an over-range percent is clamped to one hundred`() {
        val status = downloadStatusFor(Download.STATE_DOWNLOADING, 140f)

        assertEquals(100f, (status as DownloadStatus.InProgress).percent, 0.001f)
    }

    @Test
    fun `completed maps to Completed`() {
        assertEquals(DownloadStatus.Completed, downloadStatusFor(Download.STATE_COMPLETED, 100f))
    }

    @Test
    fun `failed maps to Failed`() {
        assertEquals(DownloadStatus.Failed, downloadStatusFor(Download.STATE_FAILED, 70f))
    }

    @Test
    fun `removing maps to Removing`() {
        assertEquals(DownloadStatus.Removing, downloadStatusFor(Download.STATE_REMOVING, 100f))
    }

    @Test
    fun `an unrecognised state degrades to Failed rather than throwing`() {
        assertEquals(DownloadStatus.Failed, downloadStatusFor(Int.MIN_VALUE, 0f))
    }
}
