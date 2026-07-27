package io.github.mabrur.streamly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedTest {

    private fun video(id: String, category: String) = Video(
        id = id,
        title = "Title $id",
        channelName = "Channel",
        channelAvatarUrl = "https://example.com/a.png",
        thumbnailUrl = "https://example.com/t.png",
        hlsUrl = "https://example.com/$id.m3u8",
        durationMs = 60_000L,
        viewCount = 100L,
        publishedAtEpochSeconds = 1_700_000_000L,
        category = category,
    )

    private val feed = HomeFeed(
        categories = listOf(Category("Music"), Category("Gaming")),
        videos = listOf(video("a", "Music"), video("b", "Gaming"), video("c", "Music")),
    )

    @Test
    fun `filteredBy All returns every video`() {
        assertEquals(3, feed.filteredBy(Category.All).videos.size)
    }

    @Test
    fun `filteredBy a category returns only matching videos`() {
        val result = feed.filteredBy(Category("Music"))
        assertEquals(listOf("a", "c"), result.videos.map { it.id })
    }

    @Test
    fun `filteredBy preserves the full category list`() {
        val result = feed.filteredBy(Category("Music"))
        assertEquals(feed.categories, result.categories)
    }

    @Test
    fun `filteredBy an unknown category returns no videos`() {
        assertEquals(0, feed.filteredBy(Category("Cooking")).videos.size)
    }
}
