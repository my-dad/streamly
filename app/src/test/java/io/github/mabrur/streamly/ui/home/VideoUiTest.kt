package io.github.mabrur.streamly.ui.home

import io.github.mabrur.streamly.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoUiTest {

    private val video = Video(
        id = "v01",
        title = "Mountain Roads",
        channelName = "Wander Lens",
        channelAvatarUrl = "https://example.com/a.png",
        thumbnailUrl = "https://example.com/t.png",
        hlsUrl = "https://example.com/v01.m3u8",
        durationMs = 596_000L,
        viewCount = 1_284_000L,
        publishedAtEpochSeconds = 1_000_000L,
        category = "Nature",
    )

    @Test
    fun `identity fields pass through unchanged`() {
        val ui = video.toUi(nowSeconds = 1_000_000L)

        assertEquals("v01", ui.id)
        assertEquals("Mountain Roads", ui.title)
        assertEquals("Wander Lens", ui.channelName)
        assertEquals("https://example.com/t.png", ui.thumbnailUrl)
    }

    @Test
    fun `duration is formatted for display`() {
        assertEquals("9:56", video.toUi(nowSeconds = 1_000_000L).durationLabel)
    }

    @Test
    fun `meta line combines view count and relative age`() {
        val ui = video.toUi(nowSeconds = 1_000_000L + 172_800L)

        assertEquals("1.3M views · 2 days ago", ui.metaLine)
    }

    @Test
    fun `a freshly published video reads as just now`() {
        val ui = video.toUi(nowSeconds = 1_000_000L)

        assertEquals("1.3M views · just now", ui.metaLine)
    }
}
