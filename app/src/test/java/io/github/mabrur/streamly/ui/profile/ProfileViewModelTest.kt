package io.github.mabrur.streamly.ui.profile

import app.cash.turbine.test
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.repository.SessionRepository
import io.github.mabrur.streamly.domain.usecase.SignOutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSessionRepository : SessionRepository {
    var signOutCount = 0
    override val state: Flow<SessionState> = flowOf(SessionState.SignedOut)
    override suspend fun signIn(session: Session) = Unit
    override suspend fun signOut() { signOutCount++ }
}

private class FakeCatalogRepository(
    private val profile: Result<UserProfile>,
) : CatalogRepository {
    override suspend fun getHomeFeed(): Result<HomeFeed> = Result.failure(AppError.NotFound)
    override suspend fun getShorts(): Result<List<Short>> = Result.success(emptyList())
    override suspend fun getVideo(id: String): Result<Video> = Result.failure(AppError.NotFound)
    override suspend fun getRelated(id: String): Result<List<Video>> = Result.success(emptyList())
    override suspend fun getProfile(): Result<UserProfile> = profile
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val profile = UserProfile("Ada", "ada@example.com", "https://example.com/a.png")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        result: Result<UserProfile> = Result.success(profile),
    ): Pair<ProfileViewModel, FakeSessionRepository> {
        val session = FakeSessionRepository()
        val vm = ProfileViewModel(
            catalogRepository = FakeCatalogRepository(result),
            signOut = SignOutUseCase(session),
        )
        return vm to session
    }

    @Test
    fun `loads the profile`() = runTest {
        val (vm, _) = viewModel()

        vm.state.test {
            assertTrue(awaitItem().isLoading)
            val loaded = awaitItem()
            assertEquals("Ada", loaded.profile?.name)
            assertEquals("ada@example.com", loaded.profile?.email)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces a load failure`() = runTest {
        val (vm, _) = viewModel(Result.failure(AppError.Network))

        vm.state.test {
            skipItems(1)
            assertEquals(AppError.Network, awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the sign-out dialog is hidden initially`() = runTest {
        val (vm, _) = viewModel()
        assertFalse(vm.state.value.showSignOutDialog)
    }

    @Test
    fun `SignOutClicked shows the confirmation dialog and does not sign out`() = runTest {
        val (vm, session) = viewModel()

        vm.onIntent(ProfileIntent.SignOutClicked)
        runCurrent()

        assertTrue(vm.state.value.showSignOutDialog)
        assertEquals(0, session.signOutCount)
    }

    @Test
    fun `SignOutDismissed hides the dialog without signing out`() = runTest {
        val (vm, session) = viewModel()

        vm.onIntent(ProfileIntent.SignOutClicked)
        vm.onIntent(ProfileIntent.SignOutDismissed)
        runCurrent()

        assertFalse(vm.state.value.showSignOutDialog)
        assertEquals(0, session.signOutCount)
    }

    @Test
    fun `SignOutConfirmed clears the session`() = runTest {
        val (vm, session) = viewModel()

        vm.onIntent(ProfileIntent.SignOutClicked)
        vm.onIntent(ProfileIntent.SignOutConfirmed)
        runCurrent()

        assertEquals(1, session.signOutCount)
        assertFalse(vm.state.value.showSignOutDialog)
    }
}
