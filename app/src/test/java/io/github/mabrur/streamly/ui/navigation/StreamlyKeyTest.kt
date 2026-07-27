package io.github.mabrur.streamly.ui.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamlyKeyTest {

    private val json = Json

    private fun roundTrip(key: StreamlyKey): StreamlyKey =
        json.decodeFromString(StreamlyKey.serializer(), json.encodeToString(StreamlyKey.serializer(), key))

    @Test
    fun `Onboarding survives a serialization round-trip`() {
        assertEquals(StreamlyKey.Onboarding, roundTrip(StreamlyKey.Onboarding))
    }

    @Test
    fun `Home survives a serialization round-trip`() {
        assertEquals(StreamlyKey.Home, roundTrip(StreamlyKey.Home))
    }

    @Test
    fun `Shorts survives a serialization round-trip`() {
        assertEquals(StreamlyKey.Shorts, roundTrip(StreamlyKey.Shorts))
    }

    @Test
    fun `Downloads survives a serialization round-trip`() {
        assertEquals(StreamlyKey.Downloads, roundTrip(StreamlyKey.Downloads))
    }

    @Test
    fun `Profile survives a serialization round-trip`() {
        assertEquals(StreamlyKey.Profile, roundTrip(StreamlyKey.Profile))
    }

    @Test
    fun `Player round-trips and preserves its videoId`() {
        val original = StreamlyKey.Player(videoId = "v07")
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertEquals("v07", (restored as StreamlyKey.Player).videoId)
    }
}
