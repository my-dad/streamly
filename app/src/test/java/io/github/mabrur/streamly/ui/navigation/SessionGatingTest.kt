package io.github.mabrur.streamly.ui.navigation

import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionGatingTest {

    private val session = Session(
        userId = "u1",
        displayName = "Ada",
        email = "ada@example.com",
        isGuest = false,
    )

    @Test
    fun `Unknown yields null so the caller holds the splash`() {
        assertNull(startKeyFor(SessionState.Unknown))
    }

    @Test
    fun `SignedOut starts at Onboarding`() {
        assertEquals(StreamlyKey.Onboarding, startKeyFor(SessionState.SignedOut))
    }

    @Test
    fun `SignedIn starts at Home`() {
        assertEquals(StreamlyKey.Home, startKeyFor(SessionState.SignedIn(session)))
    }

    @Test
    fun `a guest session still starts at Home`() {
        val guest = Session(userId = "g1", displayName = "Guest", email = "", isGuest = true)
        assertEquals(StreamlyKey.Home, startKeyFor(SessionState.SignedIn(guest)))
    }
}
