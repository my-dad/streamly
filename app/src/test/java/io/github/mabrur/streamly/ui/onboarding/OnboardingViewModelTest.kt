package io.github.mabrur.streamly.ui.onboarding

import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.repository.SessionRepository
import io.github.mabrur.streamly.domain.usecase.SignInUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSessionRepository : SessionRepository {
    val signedIn = mutableListOf<Session>()
    var signOutCount = 0
    override val state: Flow<SessionState> = flowOf(SessionState.SignedOut)
    override suspend fun signIn(session: Session) { signedIn += session }
    override suspend fun signOut() { signOutCount++ }
}

class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): Pair<OnboardingViewModel, FakeSessionRepository> {
        val repo = FakeSessionRepository()
        return OnboardingViewModel(SignInUseCase(repo)) to repo
    }

    @Test
    fun `ContinueAsGuest writes a guest session`() = runTest {
        val (vm, repo) = viewModel()

        vm.onIntent(OnboardingIntent.ContinueAsGuest)
        runCurrent()

        val session = repo.signedIn.single()
        assertTrue(session.isGuest)
        assertEquals("", session.email)
    }

    @Test
    fun `ContinueWithGoogle writes a non-guest session`() = runTest {
        val (vm, repo) = viewModel()

        vm.onIntent(OnboardingIntent.ContinueWithGoogle)
        runCurrent()

        val session = repo.signedIn.single()
        assertTrue(!session.isGuest)
        assertTrue(session.email.isNotEmpty())
    }

    @Test
    fun `EmailChanged updates state without signing in`() = runTest {
        val (vm, repo) = viewModel()

        vm.onIntent(OnboardingIntent.EmailChanged("ada@example.com"))
        runCurrent()

        assertEquals("ada@example.com", vm.state.value.email)
        assertTrue(repo.signedIn.isEmpty())
    }

    @Test
    fun `SubmitEmail with a valid address signs in with it`() = runTest {
        val (vm, repo) = viewModel()

        vm.onIntent(OnboardingIntent.EmailChanged("ada@example.com"))
        vm.onIntent(OnboardingIntent.SubmitEmail)
        runCurrent()

        assertEquals("ada@example.com", repo.signedIn.single().email)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `SubmitEmail with an invalid address sets an error and does not sign in`() = runTest {
        val (vm, repo) = viewModel()

        vm.onIntent(OnboardingIntent.EmailChanged("not-an-email"))
        vm.onIntent(OnboardingIntent.SubmitEmail)
        runCurrent()

        assertNotNull(vm.state.value.error)
        assertTrue(repo.signedIn.isEmpty())
    }
}
