package io.github.mabrur.streamly.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionRepositoryImplTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>

    /**
     * A real scope, not a `TestScope`. DataStore runs a long-lived internal actor in
     * whatever scope it is given; an undriven `TestScope` never executes it, so every
     * `edit` and `data` collection blocks forever.
     */
    private lateinit var storeScope: CoroutineScope

    private val session = Session(
        userId = "u1",
        displayName = "Ada",
        email = "ada@example.com",
        isGuest = false,
    )

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "streamly-test-${System.nanoTime()}")
        tempDir.mkdirs()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tempDir, "session.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun `emits SignedOut when nothing is persisted`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)

        repository.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits SignedIn after signIn`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)
        repository.signIn(session)

        repository.state.test {
            val state = awaitItem()
            assertTrue(state is SessionState.SignedIn)
            assertEquals(session, (state as SessionState.SignedIn).session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `round-trips every session field`() = runTest {
        val guest = Session(userId = "g1", displayName = "Guest", email = "", isGuest = true)
        val repository = SessionRepositoryImpl(dataStore)
        repository.signIn(guest)

        repository.state.test {
            assertEquals(guest, (awaitItem() as SessionState.SignedIn).session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits SignedOut after signOut`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)
        repository.signIn(session)
        repository.signOut()

        repository.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state emits again when the session changes`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)

        repository.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())

            repository.signIn(session)
            assertTrue(awaitItem() is SessionState.SignedIn)

            repository.signOut()
            assertEquals(SessionState.SignedOut, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
