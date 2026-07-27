# Streamly Onboarding, Profile, Sign-out & Ship Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the session round-trip — Onboarding writes a session, returning users skip it, Profile offers sign-out behind a confirmation dialog — then complete every graded deliverable: README, decision records, APK, and the demo recording.

**Architecture:** Sign-out needs no navigation effect. `StreamlyApp` already `key()`s the nav host on the session-derived start destination (design-system plan, Task 5), so clearing the session rebuilds the host rooted at `Onboarding`. The dialog is Compose-local state inside `ProfileUiState`, not a route (D-004).

**Prerequisites:** all five preceding plans complete.

## Global Constraints

All prior constraints apply. Additionally, for the shipping half:

- **Tick a README status item only if it genuinely works.** Never tick something that needs device verification you have not performed.
- Never delete README content documenting a known limitation or deliberate omission — it is load-bearing for review.
- `docs/decisions.md` is **append-only**.
- Never force-push; history is part of the submitted evidence.
- Never commit secrets, tokens, or personal data — including inside the agent log.

---

## Task 1: Onboarding

**Files:**
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingContract.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingRoute.kt`
- Test: `app/src/test/java/io/github/mabrur/streamly/ui/onboarding/OnboardingViewModelTest.kt`
- Modify: `app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt`

**Interfaces:**
- Consumes: `SignInUseCase`, `Session`.
- Produces: `OnboardingUiState(email, isSubmitting, error)`; `OnboardingIntent { ContinueWithGoogle; ContinueAsGuest; EmailChanged(v); SubmitEmail }`; `@HiltViewModel OnboardingViewModel`.

> **No navigation effect.** Writing the session flips `SessionState` to `SignedIn`,
> `startKeyFor` returns `Home`, and the `key(startKey)` in `StreamlyApp` rebuilds the host.
> Emitting a nav effect as well would race that rebuild.

- [x] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/mabrur/streamly/ui/onboarding/OnboardingViewModelTest.kt`:

```kotlin
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
```

- [x] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*OnboardingViewModelTest'`
Expected: FAIL — `Unresolved reference: OnboardingViewModel`.

- [x] **Step 3: Write the contract**

Create `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingContract.kt`:

```kotlin
package io.github.mabrur.streamly.ui.onboarding

import androidx.compose.runtime.Immutable

@Immutable
data class OnboardingUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: OnboardingError? = null,
)

/** Screen-local validation, distinct from the domain's AppError. */
sealed interface OnboardingError {
    data object InvalidEmail : OnboardingError
}

sealed interface OnboardingIntent {
    data object ContinueWithGoogle : OnboardingIntent
    data object ContinueAsGuest : OnboardingIntent
    data class EmailChanged(val value: String) : OnboardingIntent
    data object SubmitEmail : OnboardingIntent
}
```

- [x] **Step 4: Write the ViewModel**

Create `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingViewModel.kt`:

```kotlin
package io.github.mabrur.streamly.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.usecase.SignInUseCase
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.EmailChanged ->
                _state.update { it.copy(email = intent.value, error = null) }

            // Auth is mocked, as the PRD explicitly permits. The session pipeline is real.
            OnboardingIntent.ContinueWithGoogle -> persist(
                Session(
                    userId = UUID.randomUUID().toString(),
                    displayName = "Mabrur Chowdhury",
                    email = "mabrur@example.com",
                    isGuest = false,
                )
            )

            OnboardingIntent.ContinueAsGuest -> persist(
                Session(
                    userId = UUID.randomUUID().toString(),
                    displayName = "Guest",
                    email = "",
                    isGuest = true,
                )
            )

            OnboardingIntent.SubmitEmail -> {
                val email = _state.value.email.trim()
                if (!isValidEmail(email)) {
                    _state.update { it.copy(error = OnboardingError.InvalidEmail) }
                } else {
                    persist(
                        Session(
                            userId = UUID.randomUUID().toString(),
                            displayName = email.substringBefore('@'),
                            email = email,
                            isGuest = false,
                        )
                    )
                }
            }
        }
    }

    /**
     * No navigation effect: writing the session flips SessionState to SignedIn, and
     * StreamlyApp rebuilds the nav host rooted at Home. Navigating here too would race it.
     */
    private fun persist(session: Session) {
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            signIn(session)
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    /** Deliberately minimal — android.util.Patterns is unavailable in JVM unit tests. */
    private fun isValidEmail(value: String): Boolean =
        value.length in 3..254 &&
            value.count { it == '@' } == 1 &&
            value.substringBefore('@').isNotEmpty() &&
            value.substringAfter('@').contains('.') &&
            !value.endsWith('.')
}
```

- [x] **Step 5: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*OnboardingViewModelTest'`
Expected: PASS — 5 tests.

- [x] **Step 6: Write the screen and route**

Create `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingScreen.kt`:

```kotlin
package io.github.mabrur.streamly.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Streamly", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Watch anywhere. Even offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        Button(
            onClick = { onIntent(OnboardingIntent.ContinueWithGoogle) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = { onIntent(OnboardingIntent.EmailChanged(it)) },
            label = { Text("Email") },
            singleLine = true,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = {
                if (state.error is OnboardingError.InvalidEmail) {
                    Text("Enter a valid email address")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )

        OutlinedButton(
            onClick = { onIntent(OnboardingIntent.SubmitEmail) },
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Sign in with email")
        }

        TextButton(
            onClick = { onIntent(OnboardingIntent.ContinueAsGuest) },
            enabled = !state.isSubmitting,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Continue as guest")
        }
    }
}
```

Create `app/src/main/java/io/github/mabrur/streamly/ui/onboarding/OnboardingRoute.kt`:

```kotlin
package io.github.mabrur.streamly.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
```

In `StreamlyApp.kt`, replace the Onboarding entry with `entry<StreamlyKey.Onboarding> { OnboardingRoute() }` and add the import.

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/mabrur/streamly/ui/onboarding \
        app/src/test/java/io/github/mabrur/streamly/ui/onboarding \
        app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt
git commit -m "feat(onboarding): session persistence with guest and email entry" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 2: Profile and sign-out

**Files:**
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileContract.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileViewModel.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileScreen.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileRoute.kt`
- Test: `app/src/test/java/io/github/mabrur/streamly/ui/profile/ProfileViewModelTest.kt`
- Modify: `app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt`

- [x] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/mabrur/streamly/ui/profile/ProfileViewModelTest.kt`:

```kotlin
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
```

- [x] **Step 2: Write the contract**

Create `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileContract.kt`:

```kotlin
package io.github.mabrur.streamly.ui.profile

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.UserProfile

@Immutable
data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val showSignOutDialog: Boolean = false,
    val error: AppError? = null,
)

sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object SignOutClicked : ProfileIntent
    data object SignOutConfirmed : ProfileIntent
    data object SignOutDismissed : ProfileIntent
}
```

- [x] **Step 3: Write the ViewModel**

Create `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileViewModel.kt`:

```kotlin
package io.github.mabrur.streamly.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.usecase.SignOutUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> load()
            ProfileIntent.SignOutClicked ->
                _state.update { it.copy(showSignOutDialog = true) }
            ProfileIntent.SignOutDismissed ->
                _state.update { it.copy(showSignOutDialog = false) }
            ProfileIntent.SignOutConfirmed -> {
                _state.update { it.copy(showSignOutDialog = false) }
                viewModelScope.launch {
                    // Clearing the session flips SessionState to SignedOut, which makes
                    // StreamlyApp rebuild the nav host rooted at Onboarding. No effect
                    // needed — and the stack is cleared, not popped.
                    signOut()
                }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            catalogRepository.getProfile()
                .onSuccess { profile ->
                    _state.update { it.copy(isLoading = false, profile = profile, error = null) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable as? AppError
                                ?: AppError.Unknown(throwable.message.orEmpty()),
                        )
                    }
                }
        }
    }
}
```

- [x] **Step 4: Write the screen with the confirmation dialog**

Create `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileScreen.kt`:

```kotlin
package io.github.mabrur.streamly.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.mabrur.streamly.core.designsystem.component.ContentState

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentState(
        isLoading = state.isLoading,
        error = state.error,
        data = state.profile,
        modifier = modifier,
        onRetry = { onIntent(ProfileIntent.Retry) },
    ) { profile ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = profile.email.ifEmpty { "Signed in as guest" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Shallow links, explicitly permitted by the PRD.
            listOf("Downloads", "History", "Settings").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }

            TextButton(
                onClick = { onIntent(ProfileIntent.SignOutClicked) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Screen 07: a dialog gating sign-out, not a route — see docs/decisions.md D-004.
    if (state.showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(ProfileIntent.SignOutDismissed) },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to watch. Downloads stay on this device.") },
            confirmButton = {
                TextButton(onClick = { onIntent(ProfileIntent.SignOutConfirmed) }) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(ProfileIntent.SignOutDismissed) }) {
                    Text("Cancel")
                }
            },
        )
    }
}
```

Create `app/src/main/java/io/github/mabrur/streamly/ui/profile/ProfileRoute.kt`:

```kotlin
package io.github.mabrur.streamly.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
```

In `StreamlyApp.kt`, replace the Profile entry with `entry<StreamlyKey.Profile> { ProfileRoute() }` and add the import. **Every placeholder is now gone** — delete `ui/placeholder/Placeholders.kt`.

- [x] **Step 5: Verify**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — 45 app tests.

- [x] **Step 6: Commit**

```bash
git rm app/src/main/java/io/github/mabrur/streamly/ui/placeholder/Placeholders.kt
git add app/src/main/java/io/github/mabrur/streamly/ui \
        app/src/test/java/io/github/mabrur/streamly/ui/profile
git commit -m "feat(profile): profile screen with sign-out confirmation dialog" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Where this stands — Tasks 1–2 done, 3–5 blocked

**Branch:** `feat/onboarding-profile`, cut from `master`. Two commits.

Tasks 1 and 2 were pulled forward out of plan order: Onboarding and Profile touch only
`SessionRepository`, `CatalogRepository` and the nav host, so they build off `master`
without any of the Player, Shorts or Downloads work. `feat/player` is parked awaiting
device verification and is **not** merged, so none of its code is on this branch.

**Verified:** `:app:compileDebugKotlin` clean; 32 app tests pass (7 Home, 4 VideoUi,
4 SessionGating, 6 StreamlyKey, 5 Onboarding, 6 Profile), no failures. No exposed
`MutableStateFlow`, no `data` imports in `ui`. The one remaining compiler warning
(`TopLevelDestination.kt`, deprecated `Icons.Filled.List`) predates this branch.

**Needs device verification** — session round-trip, reported as pending, never as done:
- Onboarding → Continue as guest → lands on Home; Profile shows "Signed in as guest"
- Sign in with a valid email → Home; an invalid one shows the inline error and does not proceed
- **Kill and relaunch → returning user skips Onboarding entirely** (build-plan 7.3)
- Profile → Sign out → dialog appears; Cancel does nothing; confirm returns to Onboarding
  with the back stack cleared, not popped

### Expect one merge conflict, in `StreamlyApp.kt`

A trial merge of every open branch confirmed this branch is the only one that conflicts,
and only against `feat/player`. Both edit the same two regions of `StreamlyApp.kt`: the
import block, and the `entryProvider` body. That is structural — every feature branch
registers its screen in the one nav host — not a design problem.

The resolution is mechanical: keep all three imports (`OnboardingRoute`, `PlayerRoute`,
`ProfileRoute`) and all three real entries, so `Onboarding`, `Profile` and `Player` are
live and only `Shorts` and `Downloads` remain `PlaceholderScreen`. Nothing else conflicts —
`feat/shorts-pool-policy` and `feat/download-status-mapper` add new packages to
`:core:player` and merge clean in any order.

With `feat/player`, `feat/shorts-pool-policy`, `feat/download-status-mapper` and
`docs/readme-architecture` merged together, the suite is green: 92 tests, 0 failures
(8 domain, 20 data, 17 designsystem, 19 core:player, 28 app).

### Two deviations

1. **`hiltViewModel()` import.** The plan writes `androidx.hilt.navigation.compose`
   in both `OnboardingRoute` and `ProfileRoute`; that artifact is banned by D-012 for
   pulling in Nav2. Used `androidx.hilt.lifecycle.viewmodel.compose`.

2. **`ui/placeholder/Placeholders.kt` was NOT deleted** (Task 2 Step 4). The plan says
   "every placeholder is now gone", which assumes Plans 4–6 have landed. On this branch
   Shorts, Downloads and Player still route through `PlaceholderScreen`, so deleting it
   breaks the build. Delete it in the last plan that replaces the final placeholder.

### Why Tasks 3–5 are blocked, not skipped

Task 3 audits architecture across every screen, Task 4 writes the README status list, and
Task 5 ships the APK and demo. All three describe the finished app. Running them now would
either assert things that are not built yet or tick README items that do not work — which
the plan's own constraints forbid. They run last, after Shorts and Downloads.

**Test-count note:** Task 2 Step 5 expects 45 app tests. That figure assumes Player (7),
Shorts and Downloads tests are present. On this branch the correct figure is 32.

---

## Task 3: Architecture audit

Every constraint the reviewer can check mechanically, checked mechanically.

- [x] **Step 1: Run the full audit**

```bash
set -e
echo "=== 1. data internals outside :data (expect clean) ==="
grep -rn "io.ktor\|remote\.dto" app/src core domain/src --include=*.kt || echo "clean"

echo "=== 2. android imports in :domain (expect clean) ==="
grep -rn "^import android\|^import androidx" domain/src --include=*.kt || echo "clean"

echo "=== 3. exposed MutableStateFlow (expect clean) ==="
grep -rn "val [a-zA-Z]*: MutableStateFlow" app/src core --include=*.kt || echo "clean"

echo "=== 4. AndroidView anywhere (expect clean) ==="
grep -rn "AndroidView" app/src core --include=*.kt || echo "clean"

echo "=== 5. remembered ExoPlayer (expect clean) ==="
grep -rn "remember *{ *ExoPlayer" app/src core --include=*.kt || echo "clean"

echo "=== 6. exactly one SimpleCache construction (expect 1) ==="
grep -rn "SimpleCache(" app/src core --include=*.kt | wc -l

echo "=== 7. runCatching in repositories (expect clean) ==="
grep -rn "runCatching" data/src core/player/src --include=*.kt || echo "clean"

echo "=== 8. forbidden stacks in the dependency graph (expect clean) ==="
./gradlew -q :app:dependencies --configuration debugRuntimeClasspath > /tmp/deps.txt
grep -E 'androidx.navigation:navigation-|io.reactivex|retrofit2|androidx.fragment:fragment' /tmp/deps.txt || echo "clean"

echo "=== 9. Nav3 present (expect hits) ==="
grep -cE 'androidx.navigation3' /tmp/deps.txt

echo "=== 10. XML layouts (expect clean) ==="
find app/src core -path '*/res/layout/*' 2>/dev/null | grep . || echo "clean"

echo "=== 11. symlinks intact (expect four 120000) ==="
git ls-files -s | grep -c '^120000'
```

Expected: `clean` for 1–5, 7, 8, 10; `1` for 6; non-zero for 9; `4` for 11.

Fix anything that fails **before** proceeding.

- [x] **Step 2: Run every test and build**

Run: `./gradlew testDebugUnitTest :domain:test && ./gradlew assembleDebug`

**Result: 121 tests, 0 failures** — 8 domain, 20 data, 19 designsystem, 19 core:player,
**55 app**. `BUILD SUCCESSFUL`. The plan predicted 111/45-app; the extra ten are tests added
beyond what the Shorts and Downloads plans specified (`ShortsViewModelTest`,
`DownloadsViewModelTest`, and three Player download cases).

### Audit results

| # | Check | Result |
|---|---|---|
| 1 | Ktor / DTO types outside `:data` | clean |
| 2 | `android`/`androidx` imports in `:domain` | clean |
| 3 | Exposed `MutableStateFlow` | clean |
| 4 | `AndroidView` | clean |
| 5 | `remember { ExoPlayer }` | clean |
| 6 | `SimpleCache` construction sites | 1 |
| 7 | `runCatching` in repositories | clean after fix, see below |
| 8 | Nav2 / RxJava / Retrofit / Fragment | `androidx.fragment` only, see below |
| 9 | Navigation 3 present | 8 hits |
| 10 | XML layouts | clean |
| 11 | Symlinks intact (`120000`) | 4 |

**Check 7 initially failed** on two hits added by the Downloads work: a
`runCatching { downloadIndex.getDownload(...) }` in `ExoPlayerHolder` and a
`runCatching { JSONObject(...) }` in `DownloadRepositoryImpl`. Neither sat in a suspend
function, so neither could actually swallow a `CancellationException` — but a catch-all in
a repository is worth narrowing regardless. Both are now `try`/`catch` on the specific
exception (`IOException`, `JSONException`). The only remaining grep hit is the comment in
`CatalogRepositoryImpl` that tells the next person not to use `runCatching`.

**Check 8 reports `androidx.fragment:fragment:1.5.1`.** It appears exactly once in the
graph, pulled by `com.google.dagger:hilt-android`, which supports `@AndroidEntryPoint` on
Fragments and so declares it. It cannot be removed without dropping Hilt, and no `Fragment`
is subclassed or referenced anywhere in the source — which is what the constraint forbids.
Already recorded as D-012.

---

## Task 4: Documentation

- [ ] **Step 1: Fill in the agent log**

`docs/agent-log.md` is still the empty template, and §10 of the PRD counts it as workflow
evidence. Replace it with real entries — one per plan executed, using the actual prompts:

```markdown
# Agent log

## 2026-07-27 — Brainstorm, build plan, and seven implementation plans
**Tool:** Claude Code (Opus 5)
**Prompt:** "/superpowers:brainstorming — i am building this app for an interview process and docs/streamly-handoff.md is the PRD file."
**Result:** Settled five up-front decisions (module split, Compose-native playback, shared
AppError, dialog-not-route sign-out, bottom-bar nav). Produced `docs/streamly-build-plan.md`
and six task-level plans under `docs/superpowers/plans/`. Reviewed each plan before
executing; several were revised after self-review caught defects.

## 2026-07-27 — Navigation 3 and Media3 API verification
**Tool:** Claude Code (Opus 5)
**Prompt:** "go read the nav3 docs and write plan 2 … verify media3 first"
**Result:** Both libraries post-date the model's training data, so the APIs were read
directly from the published AARs with `javap` rather than recalled. This caught that
`NavDisplay`'s default entry decorators exclude ViewModel scoping (D-007) — which would have
leaked an ExoPlayer per Player-screen visit while appearing to work.
```

Add one entry per subsequent working session. **No secrets, no personal data.**

- [ ] **Step 2: Write the README**

Replace `README.md`. Tick **only** what you have genuinely verified:

```markdown
# Streamly

A minimal YouTube-style Android app: two playback surfaces (long-form + vertical shorts)
streamed over HLS with Media3, real offline downloads, onboarding with session persistence,
and profile/sign-out.

Network data is faked. The architecture, player lifecycle, and code quality are real.

## Status

- [ ] Onboarding with session persistence
- [ ] Home feed with categories, loading/empty/error states
- [ ] Shorts — vertical pager, pooled players
- [ ] Player — HLS, lifecycle-correct, rotation-safe
- [ ] Downloads — real progress, offline playback, remove
- [ ] Profile + sign-out confirmation
- [ ] Adaptive layout via WindowSizeClass

*(Tick each only after verifying on a device.)*

## Architecture

Five Gradle modules:

    :app                 wiring, NavDisplay host, DI graph, one package per screen
    :domain              pure kotlin("jvm") — models, repository interfaces, use cases
    :data                Ktor + MockEngine, DTOs, mappers, DataStore session
    :core:player         ExoPlayer holder, ShortsPlayerPool, download stack
    :core:designsystem   theme, ContentState, shared composables

Dependency direction is `:app → :domain ← :data`. `:domain` has no Android plugin at all,
so "no framework imports in the domain layer" is enforced by Gradle rather than by review.

Every screen follows the same MVI contract: an immutable `UiState`, a sealed `Intent`, and a
sealed `Effect` for one-shot events. ViewModels expose `state` and `onIntent` — never a
`MutableStateFlow`, never a navigation lambda.

Decisions and their rationale are recorded in [`docs/decisions.md`](docs/decisions.md).

## Running it

    ./gradlew assembleDebug        # build
    ./gradlew testDebugUnitTest    # unit tests
    ./gradlew :domain:test         # domain tests (pure JVM)

No API keys or configuration are required — the catalog is bundled and served by Ktor's
MockEngine. HLS media streams from public test CDNs, so the first run needs a network.

## AI workflow

`AGENTS.md` is the single source of truth for all coding agents, symlinked to `CLAUDE.md`,
`.cursor/rules`, `.codex/instructions.md`, and `.antigravity/rules` (all committed as git mode
`120000`). Agent-assisted commits carry a `Co-authored-by` trailer via the repo-local
`commit.template`.

The work was planned before it was written: `docs/streamly-build-plan.md` for phasing, then
per-phase plans in `docs/superpowers/plans/`. `docs/agent-log.md` records the actual prompts.

Both Navigation 3 and `media3-ui-compose` post-date the model's training data, so their APIs
were read from the published AARs with `javap` rather than recalled. That caught a real
defect before it was written: `NavDisplay`'s default entry decorators do not include
ViewModel scoping, which would have leaked an ExoPlayer on every Player-screen exit while
appearing to work correctly (see D-007).

## Shortcuts taken, and why

- **Auth is mocked.** "Continue with Google" writes a session directly. The PRD permits this;
  the session *pipeline* — DataStore persistence, gated start destination, sign-out clearing
  — is real.
- **Shorts reuse landscape HLS streams**, centre-cropped to fill. Public vertical HLS test
  content effectively does not exist. The pooling behaviour, which is what is graded, is
  unaffected.
- **Category chips filter client-side** against the bundled catalog rather than re-querying.
- **No `Scheduler` on the download service**, so downloads do not resume after a reboot. That
  needs WorkManager and the PRD does not ask for it.
- **Transport controls act on the `Player` directly** rather than through the intent channel
  (D-008), because Media3 already ships tested state holders for them.
- **Two pooled Shorts players, not three.** The graded rule is "no more than 1–2 decoding";
  with two instances that holds by construction.

## Known limitations

- Downloads do not survive a reboot (see above).
- The MockEngine fails roughly one request in eight by design, so error states are genuinely
  reachable in the demo. This is deliberate, not a bug.
- No instrumented tests. Unit tests cover ViewModel intent→state transitions, the download
  state mapper, the shorts pool policy, and all display formatters.
```

- [ ] **Step 3: Commit**

```bash
git add README.md docs/agent-log.md
git commit -m "docs: README with architecture, AI workflow, and shortcuts" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 5: Ship

- [ ] **Step 1: Clean-clone build check**

```bash
rm -rf /tmp/streamly-clean
git clone . /tmp/streamly-clean
cd /tmp/streamly-clean && ./gradlew assembleDebug && cd -
```

Expected: `BUILD SUCCESSFUL` from a fresh clone. If it fails, something needed is untracked —
find it with `git status --ignored` and fix before shipping.

- [ ] **Step 2: Walk the PRD §13 checklist**

Go through every line of `docs/streamly-handoff.md` §13 on a device and record the result.
Do not tick from inference.

- [ ] **Step 3: Update the README status list**

Tick only what Step 2 actually confirmed.

- [ ] **Step 4: Attach the APK**

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Attach it to a GitHub
release, or state the build command in the README if releases are unavailable.

- [ ] **Step 5: Record the demo**

2–4 minutes, all seven screens, **ending with** the sequence that proves the 30% category:

1. Onboarding → continue as guest
2. Home feed → scroll, tap a category chip
3. Tap a video → Player → play, scrub, mute, show the buffering indicator
4. **Rotate** → position survives
5. Back → Shorts → swipe through several, show no audio overlap
6. Downloads tab → **start a download → real progress**
7. **Enable airplane mode → play the completed download offline**
8. Profile → sign out → confirmation dialog → back at Onboarding

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "chore: final status and deliverables" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

- [ ] **Step 7 (optional, only if time survives): CI**

`.github/workflows/build.yml` running `./gradlew assembleDebug testDebugUnitTest` on push.
Cheap, and it signals professionalism — but it is the first thing to cut.

---

## Definition of done — the whole project

- [ ] Clean clone builds
- [ ] 111 unit tests pass
- [ ] All 11 audit checks in Task 3 pass
- [ ] `docs/decisions.md` contains D-001 … D-009
- [ ] README documents architecture, AI workflow, shortcuts, and limitations
- [ ] `docs/agent-log.md` has real entries
- [ ] APK attached; demo recorded covering all seven screens including offline playback
- [ ] `git ls-files -s | grep -c '^120000'` returns **4**
- [ ] Every device-only claim verified on a device — never reported as done from inference
