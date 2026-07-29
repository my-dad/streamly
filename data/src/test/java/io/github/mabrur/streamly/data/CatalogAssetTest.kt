package io.github.mabrur.streamly.data

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped catalog is data, not code, so nothing else in the suite touches it — a typo in
 * it fails at runtime on the first screen instead of here. This parses the real asset off the
 * filesystem (the module directory is the working directory for unit tests) rather than a
 * fixture copy, which is the whole point.
 */
class CatalogAssetTest {

    /**
     * Both are multi-rendition masters, so the 1.5 Mbps download cap in `DownloadRepositoryImpl`
     * always has something smaller to select. Adding a single-rendition source here is what
     * D-026 exists to stop happening again — verify a new master's renditions before widening.
     */
    private val vettedStreams = setOf(
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        "https://test-streams.mux.dev/pts_shift/master.m3u8",
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
        "https://cdn.jwplayer.com/manifests/pZxWPRg4.m3u8",
        "https://storage.googleapis.com/shaka-demo-assets/bbb-dark-truths-hls/hls.m3u8",
        "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
    )

    private val catalog =
        Json.decodeFromString<CatalogDto>(File("src/main/assets/catalog.json").readText())

    @Test
    fun `every stream url is a vetted multi-rendition master`() {
        val urls = catalog.videos.map { it.hlsUrl } + catalog.shorts.map { it.hlsUrl }
        assertEquals(emptySet<String>(), urls.toSet() - vettedStreams)
    }

    /**
     * Sixteen of the eighteen entries once pointed at one stream, so every card in the feed
     * played the same clip — invisible to every other test here and obvious the moment the
     * app is opened. See D-033.
     */
    @Test
    fun `the feed is not one clip wearing twelve titles`() {
        val videoStreams = catalog.videos.map { it.hlsUrl }
        assertTrue(
            "videos share too few distinct streams: ${videoStreams.toSet().size}",
            videoStreams.toSet().size >= 4,
        )
        assertEquals(
            "adjacent cards must not play the same stream",
            0,
            videoStreams.zipWithNext().count { (a, b) -> a == b },
        )
    }

    @Test
    fun `ids are unique and categories resolve`() {
        val ids = catalog.videos.map { it.id } + catalog.shorts.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(catalog.videos.all { it.category in catalog.categories })
    }
}
