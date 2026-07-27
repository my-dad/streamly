package io.github.mabrur.streamly.data.remote.mapper

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import io.github.mabrur.streamly.domain.model.Category
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val payload = """
        {
          "categories": ["All", "Music"],
          "videos": [
            { "id": "v01", "title": "T1", "channelName": "C1", "channelAvatarUrl": "a1",
              "thumbnailUrl": "t1", "hlsUrl": "h1", "durationMs": 1000, "viewCount": 10,
              "publishedAtEpochSeconds": 1700000000, "category": "Music" }
          ],
          "shorts": [
            { "id": "s01", "title": "S1", "channelName": "C1", "hlsUrl": "sh1",
              "likeCount": 5, "commentCount": 2 }
          ],
          "profile": { "name": "N", "email": "e@x.com", "avatarUrl": "av" }
        }
    """.trimIndent()

    private fun parse() = json.decodeFromString<CatalogDto>(payload)

    @Test
    fun `maps categories into domain Category values`() {
        val feed = parse().toHomeFeed()
        assertEquals(listOf(Category("All"), Category("Music")), feed.categories)
    }

    @Test
    fun `maps every video field across the boundary`() {
        val video = parse().toHomeFeed().videos.single()

        assertEquals("v01", video.id)
        assertEquals("T1", video.title)
        assertEquals("C1", video.channelName)
        assertEquals("a1", video.channelAvatarUrl)
        assertEquals("t1", video.thumbnailUrl)
        assertEquals("h1", video.hlsUrl)
        assertEquals(1000L, video.durationMs)
        assertEquals(10L, video.viewCount)
        assertEquals(1_700_000_000L, video.publishedAtEpochSeconds)
        assertEquals("Music", video.category)
    }

    @Test
    fun `maps shorts across the boundary`() {
        val short = parse().toShorts().single()

        assertEquals("s01", short.id)
        assertEquals("S1", short.title)
        assertEquals("C1", short.channelName)
        assertEquals("sh1", short.hlsUrl)
        assertEquals(5L, short.likeCount)
        assertEquals(2L, short.commentCount)
    }

    @Test
    fun `maps the profile across the boundary`() {
        val profile = parse().toProfile()

        assertEquals("N", profile.name)
        assertEquals("e@x.com", profile.email)
        assertEquals("av", profile.avatarUrl)
    }

    @Test
    fun `malformed json fails to decode`() {
        assertThrows(Exception::class.java) {
            json.decodeFromString<CatalogDto>("{ \"categories\": ")
        }
    }

    @Test
    fun `missing required field fails to decode`() {
        val missingId = """
            { "categories": [], "shorts": [], "profile": { "name": "N", "email": "e", "avatarUrl": "a" },
              "videos": [ { "title": "T1", "channelName": "C1", "channelAvatarUrl": "a1",
                            "thumbnailUrl": "t1", "hlsUrl": "h1", "durationMs": 1000, "viewCount": 10,
                            "publishedAtEpochSeconds": 1700000000, "category": "Music" } ] }
        """.trimIndent()

        assertThrows(Exception::class.java) {
            json.decodeFromString<CatalogDto>(missingId)
        }
    }
}
