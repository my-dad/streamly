# Streamly Design System + Navigation 3 Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `:core:designsystem` module and the Navigation 3 shell so the app launches to the correct start destination, switches between six placeholder screens via a bottom bar, and scopes every screen's ViewModel to its `NavEntry`.

**Architecture:** A developer-owned `NavBackStack` of `@Serializable` `NavKey` route objects, rendered by `NavDisplay` with an `entryProvider`. ViewModels are scoped per-`NavEntry` by an explicitly-passed decorator, which is what later makes `onCleared()` fire on pop and release the ExoPlayer. Session gating is a pure function from `SessionState` to a start key, and the nav host is `key()`-ed on it so sign-out rebuilds the stack rather than mutating it.

**Tech Stack:** Navigation3 1.1.4 · lifecycle-viewmodel-navigation3 2.11.0 · Compose BOM 2026.02.01 · Hilt 2.60.1 · kotlinx-serialization 1.11.0

**Prerequisite:** `docs/superpowers/plans/2026-07-27-foundation-domain-data.md` must be complete. This plan consumes `AppError`, `SessionState`, `ObserveSessionUseCase`, and the Hilt graph from it.

## Global Constraints

Everything in the foundation plan's Global Constraints still applies. In addition:

- **Navigation 3 only.** Never add `androidx.navigation:navigation-compose` (Nav2). Do not infer Nav3 APIs from Nav2 patterns.
- Collect state with `collectAsStateWithLifecycle()` only.
- Screen composables are stateless: `(UiState, (Intent) -> Unit)`. Never pass a ViewModel down the tree.
- Side effects only in `LaunchedEffect`/`DisposableEffect` with correct keys; never in composition.
- Local-only UI state uses `rememberSaveable`; screen state lives in the ViewModel.
- No hardcoded widths. Size against available width; use `WindowSizeClass` where it matters.
- Every screen reuses the shared `ContentState` wrapper for loading/empty/error.
- Never expose `MutableStateFlow` or mutable collections from a ViewModel.

---

## Verified API reference

These signatures were read directly from the published artifacts (`javap` on `navigation3-runtime-android-1.1.4.aar`, `navigation3-ui-android-1.1.4.aar`, `lifecycle-viewmodel-navigation3-android-2.11.0.aar`) on 2026-07-27, and cross-checked against developer.android.com. **They are ground truth — do not "correct" them from memory.**

| Symbol | Verified signature |
|---|---|
| `NavKey` | `interface NavKey` — a bare marker interface |
| `NavBackStack<T : NavKey>` | implements `MutableList<T>` — `add`, `removeAt`, `clear`, `removeLastOrNull()` all work directly |
| `rememberNavBackStack` | `rememberNavBackStack(vararg elements: NavKey): NavBackStack<NavKey>` |
| `entryProvider` | `entryProvider<T>(fallback, builder: EntryProviderScope<T>.() -> Unit): (T) -> NavEntry<T>` |
| `EntryProviderScope.entry<K>` | reified; `entry<K> { key -> ... }` |
| `NavDisplay` | `NavDisplay(backStack, modifier, contentAlignment, onBack, entryDecorators, sceneStrategy, sizeTransform, transitionSpec, popTransitionSpec, predictivePopTransitionSpec, entryProvider)` |
| `rememberViewModelStoreNavEntryDecorator` | `<T> rememberViewModelStoreNavEntryDecorator(): ViewModelStoreNavEntryDecorator<T>` |
| `rememberSaveableStateHolderNavEntryDecorator` | `<T> rememberSaveableStateHolderNavEntryDecorator(): SaveableStateHolderNavEntryDecorator<T>` |
| `DialogSceneStrategy.Companion.dialog()` | `dialog(DialogProperties): Map<String, Any>` — for the sign-out dialog in a later plan |

### ⚠️ The finding this plan exists to get right

`NavDisplay`'s **default** `entryDecorators` does **not** include ViewModel scoping. Decompiling `navigation3-ui` shows it wires only `rememberSceneSetupNavEntryDecorator`, `rememberSaveableStateHolderNavEntryDecorator`, `rememberBackStackAwareLifecycleNavEntryDecorator`, and `rememberSharedEntryInSceneNavEntryDecorator`. Its POM does not even depend on `lifecycle-viewmodel-navigation3`, so it *cannot* reference that decorator.

**Consequence if you omit `entryDecorators`:** every screen's ViewModel falls back to Activity scope. `onCleared()` never fires on pop. The long-form `ExoPlayer` is never released, LeakCanary reports a leak, and the highest-weighted rubric item (Media3, 30%) fails — silently, because playback still *appears* to work.

`entryDecorators` **must** be passed explicitly as:

```kotlin
entryDecorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
)
```

Recorded as **D-007** in Task 8. Task 5 Step 5 verifies it empirically.

---

## Deliberate deviations from build-plan Phase 2

Build-plan task **2.4** asks `:core:designsystem` to also ship `VideoCard`,
`CategoryChipRow`, and `StreamlyScaffold`. This plan does not build them, on purpose:

- **`VideoCard` and `CategoryChipRow` move to the Home plan.** They are feed-specific and
  their parameter lists are determined by `HomeUiState`, which does not exist yet.
  Designing them here means guessing at their API and then reworking it one plan later.
  They still land in `:core:designsystem` — just built when their consumer is known.
- **`StreamlyScaffold` is dropped.** Its only caller would be `StreamlyNavHost`, which
  already composes `Scaffold` + `NavigationBar` directly. A one-caller wrapper adds a
  layer without removing one.

`ContentState` **is** built here, because every placeholder screen uses it immediately and
its shape is fully determined by `AppError`.

---

## File Structure

**`:core:designsystem`** (`core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/`)
- `theme/Color.kt`, `theme/Type.kt`, `theme/Theme.kt` — migrated verbatim from `:app`.
- `error/ErrorMessages.kt` — pure `AppError` → string-resource mapping. The testable unit.
- `component/ContentState.kt` — the shared loading/empty/error/content wrapper.
- `component/StreamlyScaffold.kt` — scaffold + bottom bar.
- `src/main/res/values/strings.xml` — error and empty-state copy.

**`:app`** (`app/src/main/java/io/github/mabrur/streamly/`)
- `ui/navigation/StreamlyKey.kt` — the six route keys.
- `ui/navigation/SessionGating.kt` — pure `startKeyFor()`.
- `ui/navigation/TopLevelDestination.kt` — bottom-bar model.
- `ui/AppViewModel.kt` — observes session state.
- `ui/StreamlyApp.kt` — session gate + nav host + `NavDisplay`.
- `ui/placeholder/Placeholders.kt` — six temporary screens, replaced by later plans.
- `MainActivity.kt` — rewritten to host `StreamlyApp`.

---

## Task 1: Migrate the theme into `:core:designsystem`

Moves the generated theme out of `:app` so every module can use it.

**Files:**
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Color.kt`
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Type.kt`
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Theme.kt`
- Delete: `app/src/main/java/io/github/mabrur/streamly/ui/theme/Color.kt`
- Delete: `app/src/main/java/io/github/mabrur/streamly/ui/theme/Type.kt`
- Delete: `app/src/main/java/io/github/mabrur/streamly/ui/theme/Theme.kt`
- Modify: `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`

**Interfaces:**
- Consumes: `:core:designsystem` module from foundation Task 1.
- Produces: `io.github.mabrur.streamly.core.designsystem.theme.StreamlyTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`.

- [ ] **Step 0: Promote `:domain` to an `api` dependency of `:core:designsystem`**

`ContentState` and `ErrorMessages` expose `AppError` in their **public** signatures. If
`:domain` stays an `implementation` dependency, that type is not on the consumer's
compile classpath through this module. Change the line in `core/designsystem/build.gradle.kts`:

```kotlin
    api(project(":domain"))
```

(was `implementation(project(":domain"))`).

- [ ] **Step 1: Move the three theme files**

```bash
mkdir -p core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme
git mv app/src/main/java/io/github/mabrur/streamly/ui/theme/Color.kt \
       core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Color.kt
git mv app/src/main/java/io/github/mabrur/streamly/ui/theme/Type.kt \
       core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Type.kt
git mv app/src/main/java/io/github/mabrur/streamly/ui/theme/Theme.kt \
       core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Theme.kt
rmdir app/src/main/java/io/github/mabrur/streamly/ui/theme 2>/dev/null || true
```

- [ ] **Step 2: Repoint the package declarations**

In all three moved files, change the first line from:

```kotlin
package io.github.mabrur.streamly.ui.theme
```

to:

```kotlin
package io.github.mabrur.streamly.core.designsystem.theme
```

Change nothing else in `Color.kt` or `Type.kt`. In `Theme.kt`, the `Purple80`/`Purple40`/`Typography` references keep resolving because they are now same-package.

- [ ] **Step 3: Strip the template content out of MainActivity**

Replace the whole of `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`. The `Greeting`/`GreetingPreview` template composables are deleted; `StreamlyApp` arrives in Task 5, so this is a temporary body:

```kotlin
package io.github.mabrur.streamly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamlyTheme {
                // Replaced by StreamlyApp() in Task 5.
            }
        }
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

If `StreamlyTheme` is unresolved, `:core:designsystem` is missing `api(libs.androidx.compose.material3)` — the foundation plan declares those as `api`, not `implementation`, precisely so consumers see them.

- [ ] **Step 5: Commit**

```bash
git add -A core/designsystem/src/main/java app/src/main/java
git commit -m "refactor(designsystem): move theme into :core:designsystem" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 2: Error message mapping

The pure, testable core of the error UI. Composables cannot be unit-tested without Robolectric, so the decision logic is extracted into a function that can.

**Files:**
- Create: `core/designsystem/src/main/res/values/strings.xml`
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/error/ErrorMessages.kt`
- Test: `core/designsystem/src/test/java/io/github/mabrur/streamly/core/designsystem/error/ErrorMessagesTest.kt`

**Interfaces:**
- Consumes: `AppError` from the foundation plan.
- Produces: `@StringRes fun AppError.titleResId(): Int` and `@StringRes fun AppError.bodyResId(): Int`, plus `fun AppError.isRetryable(): Boolean`. `ContentState` in Task 3 consumes all three.

- [ ] **Step 1: Add the test dependency**

`:core:designsystem` has no test dependencies yet. Add to `core/designsystem/build.gradle.kts`, inside the existing `dependencies { }` block:

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: Write the failing test**

Create `core/designsystem/src/test/java/io/github/mabrur/streamly/core/designsystem/error/ErrorMessagesTest.kt`:

```kotlin
package io.github.mabrur.streamly.core.designsystem.error

import io.github.mabrur.streamly.core.designsystem.R
import io.github.mabrur.streamly.domain.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun `network error maps to the network strings`() {
        assertEquals(R.string.error_network_title, AppError.Network.titleResId())
        assertEquals(R.string.error_network_body, AppError.Network.bodyResId())
    }

    @Test
    fun `not found maps to the not-found strings`() {
        assertEquals(R.string.error_not_found_title, AppError.NotFound.titleResId())
        assertEquals(R.string.error_not_found_body, AppError.NotFound.bodyResId())
    }

    @Test
    fun `storage error maps to the storage strings`() {
        assertEquals(R.string.error_storage_title, AppError.Storage.titleResId())
        assertEquals(R.string.error_storage_body, AppError.Storage.bodyResId())
    }

    @Test
    fun `unknown error maps to the generic strings`() {
        val error = AppError.Unknown("boom")
        assertEquals(R.string.error_generic_title, error.titleResId())
        assertEquals(R.string.error_generic_body, error.bodyResId())
    }

    @Test
    fun `network and unknown errors are retryable`() {
        assertTrue(AppError.Network.isRetryable())
        assertTrue(AppError.Unknown("boom").isRetryable())
    }

    @Test
    fun `not found and storage errors are not retryable`() {
        assertFalse(AppError.NotFound.isRetryable())
        assertFalse(AppError.Storage.isRetryable())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: FAIL — `Unresolved reference: titleResId`.

- [ ] **Step 4: Add the strings**

Create `core/designsystem/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="error_network_title">No connection</string>
    <string name="error_network_body">Check your internet connection and try again.</string>

    <string name="error_not_found_title">Not found</string>
    <string name="error_not_found_body">We couldn\'t find what you were looking for.</string>

    <string name="error_storage_title">Storage problem</string>
    <string name="error_storage_body">Something went wrong reading local storage.</string>

    <string name="error_generic_title">Something went wrong</string>
    <string name="error_generic_body">An unexpected error occurred. Please try again.</string>

    <string name="action_retry">Retry</string>

    <string name="empty_title">Nothing here yet</string>
    <string name="empty_body">There\'s nothing to show right now.</string>

    <string name="nav_home">Home</string>
    <string name="nav_shorts">Shorts</string>
    <string name="nav_downloads">Downloads</string>
    <string name="nav_profile">Profile</string>
</resources>
```

- [ ] **Step 5: Write the mapping**

Create `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/error/ErrorMessages.kt`:

```kotlin
package io.github.mabrur.streamly.core.designsystem.error

import androidx.annotation.StringRes
import io.github.mabrur.streamly.core.designsystem.R
import io.github.mabrur.streamly.domain.error.AppError

@StringRes
fun AppError.titleResId(): Int = when (this) {
    AppError.Network -> R.string.error_network_title
    AppError.NotFound -> R.string.error_not_found_title
    AppError.Storage -> R.string.error_storage_title
    is AppError.Unknown -> R.string.error_generic_title
}

@StringRes
fun AppError.bodyResId(): Int = when (this) {
    AppError.Network -> R.string.error_network_body
    AppError.NotFound -> R.string.error_not_found_body
    AppError.Storage -> R.string.error_storage_body
    is AppError.Unknown -> R.string.error_generic_body
}

/** Only transient failures offer a retry affordance. */
fun AppError.isRetryable(): Boolean = when (this) {
    AppError.Network -> true
    is AppError.Unknown -> true
    AppError.NotFound -> false
    AppError.Storage -> false
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS — 6 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add core/designsystem/build.gradle.kts \
        core/designsystem/src/main/res/values/strings.xml \
        core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/error \
        core/designsystem/src/test
git commit -m "feat(designsystem): sealed error to string-resource mapping" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 3: The shared `ContentState` wrapper

One wrapper every screen reuses, so loading/empty/error coverage is systematic rather than per-screen improvisation.

**Files:**
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/component/ContentState.kt`

**Interfaces:**
- Consumes: `titleResId()`, `bodyResId()`, `isRetryable()` from Task 2.
- Produces:
  ```kotlin
  @Composable fun <T> ContentState(
      isLoading: Boolean,
      error: AppError?,
      data: T?,
      modifier: Modifier = Modifier,
      isEmpty: (T) -> Boolean = { false },
      onRetry: (() -> Unit)? = null,
      content: @Composable (T) -> Unit,
  )
  ```
  Every screen in every later plan calls this. Precedence is fixed: **error → loading → empty → content**.

- [ ] **Step 1: Write the composable**

Create `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/component/ContentState.kt`:

```kotlin
package io.github.mabrur.streamly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.R
import io.github.mabrur.streamly.core.designsystem.error.bodyResId
import io.github.mabrur.streamly.core.designsystem.error.isRetryable
import io.github.mabrur.streamly.core.designsystem.error.titleResId
import io.github.mabrur.streamly.domain.error.AppError

/**
 * Shared loading / empty / error / content wrapper.
 *
 * Precedence is deliberate and fixed: error wins over loading, loading over
 * empty, empty over content. A screen that is refreshing after a failure shows
 * the error rather than flickering a spinner over stale data.
 */
@Composable
fun <T> ContentState(
    isLoading: Boolean,
    error: AppError?,
    data: T?,
    modifier: Modifier = Modifier,
    isEmpty: (T) -> Boolean = { false },
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when {
        error != null -> MessageState(
            modifier = modifier,
            title = stringResource(error.titleResId()),
            body = stringResource(error.bodyResId()),
            actionLabel = if (error.isRetryable() && onRetry != null) {
                stringResource(R.string.action_retry)
            } else {
                null
            },
            onAction = onRetry,
        )

        isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        data == null || isEmpty(data) -> MessageState(
            modifier = modifier,
            title = stringResource(R.string.empty_title),
            body = stringResource(R.string.empty_body),
            actionLabel = null,
            onAction = null,
        )

        else -> content(data)
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/component/ContentState.kt
git commit -m "feat(designsystem): shared ContentState loading/empty/error wrapper" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 4: Route keys and session gating

Both units here are pure and fully testable, which matters: the serialization test is what catches a missing `@Serializable` before it shows up as lost state after process death.

**Files:**
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/navigation/StreamlyKey.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/navigation/SessionGating.kt`
- Test: `app/src/test/java/io/github/mabrur/streamly/ui/navigation/StreamlyKeyTest.kt`
- Test: `app/src/test/java/io/github/mabrur/streamly/ui/navigation/SessionGatingTest.kt`

**Interfaces:**
- Consumes: `SessionState`, `Session` from the foundation plan; `NavKey` from navigation3-runtime.
- Produces:
  - `sealed interface StreamlyKey : NavKey` with `Onboarding`, `Home`, `Shorts`, `Downloads`, `Profile` (all `data object`) and `Player(videoId: String)` (`data class`). All `@Serializable`.
  - `fun startKeyFor(state: SessionState): StreamlyKey?` — `null` means "still resolving, hold the splash".

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/io/github/mabrur/streamly/ui/navigation/StreamlyKeyTest.kt`. This test exists to prove every key survives a serialization round-trip — `rememberNavBackStack` persists the stack that way, so a key missing `@Serializable` loses the back stack on process death:

```kotlin
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
```

Create `app/src/test/java/io/github/mabrur/streamly/ui/navigation/SessionGatingTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — `Unresolved reference: StreamlyKey` / `startKeyFor`.

- [ ] **Step 3: Write the route keys**

Create `app/src/main/java/io/github/mabrur/streamly/ui/navigation/StreamlyKey.kt`:

```kotlin
package io.github.mabrur.streamly.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The app's navigation keys.
 *
 * Every key must be [Serializable]: `rememberNavBackStack` persists the back stack
 * through kotlinx-serialization, so a key without the annotation loses the stack on
 * process death. [StreamlyKeyTest] guards this.
 *
 * Sign-out is a dialog over Profile, not a route — see docs/decisions.md D-004.
 */
@Serializable
sealed interface StreamlyKey : NavKey {

    @Serializable
    data object Onboarding : StreamlyKey

    @Serializable
    data object Home : StreamlyKey

    @Serializable
    data object Shorts : StreamlyKey

    @Serializable
    data object Downloads : StreamlyKey

    @Serializable
    data object Profile : StreamlyKey

    @Serializable
    data class Player(val videoId: String) : StreamlyKey
}
```

- [ ] **Step 4: Write the session gating function**

Create `app/src/main/java/io/github/mabrur/streamly/ui/navigation/SessionGating.kt`:

```kotlin
package io.github.mabrur.streamly.ui.navigation

import io.github.mabrur.streamly.domain.model.SessionState

/**
 * The start destination for a given session state.
 *
 * Returns `null` while the persisted session is still being read — the caller holds
 * a loading surface rather than guessing, which would flash Onboarding at a signed-in
 * user on every cold start.
 */
fun startKeyFor(state: SessionState): StreamlyKey? = when (state) {
    SessionState.Unknown -> null
    SessionState.SignedOut -> StreamlyKey.Onboarding
    is SessionState.SignedIn -> StreamlyKey.Home
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — 10 tests, `BUILD SUCCESSFUL`.

If the `StreamlyKey` tests fail with "Serializer for class 'StreamlyKey' is not found", the `kotlin-serialization` plugin is missing from `app/build.gradle.kts` — the foundation plan adds it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mabrur/streamly/ui/navigation \
        app/src/test/java/io/github/mabrur/streamly/ui/navigation
git commit -m "feat(nav): serializable Nav3 route keys and session gating" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 5: The `NavDisplay` host

The critical task. Its decorator list is what makes ViewModel scoping — and therefore player release — work at all.

**Files:**
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/navigation/TopLevelDestination.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/AppViewModel.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/placeholder/Placeholders.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt`
- Modify: `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`

**Interfaces:**
- Consumes: `StreamlyKey`, `startKeyFor` (Task 4); `ObserveSessionUseCase` (foundation plan); `ContentState` (Task 3).
- Produces: `@Composable fun StreamlyApp()`; `enum class TopLevelDestination(val key: StreamlyKey, @StringRes val labelRes: Int, val icon: ImageVector)`; `@HiltViewModel class AppViewModel` exposing `val sessionState: StateFlow<SessionState>`. Later plans replace the placeholder bodies inside `entryProvider` one at a time.

- [ ] **Step 1: Write the bottom-bar model**

Create `app/src/main/java/io/github/mabrur/streamly/ui/navigation/TopLevelDestination.kt`:

```kotlin
package io.github.mabrur.streamly.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mabrur.streamly.core.designsystem.R

/**
 * Destinations reachable from the bottom bar.
 *
 * Player is deliberately absent: it pushes over the bar and hides it.
 * Onboarding is absent because it precedes the bar entirely.
 */
enum class TopLevelDestination(
    val key: StreamlyKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(StreamlyKey.Home, R.string.nav_home, Icons.Filled.Home),
    Shorts(StreamlyKey.Shorts, R.string.nav_shorts, Icons.Filled.PlayArrow),
    Downloads(StreamlyKey.Downloads, R.string.nav_downloads, Icons.Filled.List),
    Profile(StreamlyKey.Profile, R.string.nav_profile, Icons.Filled.Person),
}
```

- [ ] **Step 2: Write the app-level ViewModel**

Create `app/src/main/java/io/github/mabrur/streamly/ui/AppViewModel.kt`:

```kotlin
package io.github.mabrur.streamly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.usecase.ObserveSessionUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = observeSession().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionState.Unknown,
    )
}
```

- [ ] **Step 3: Write the placeholder screens**

Create `app/src/main/java/io/github/mabrur/streamly/ui/placeholder/Placeholders.kt`. Each already routes through `ContentState`, so the loading/empty/error contract is in place from the start rather than retrofitted:

```kotlin
package io.github.mabrur.streamly.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.mabrur.streamly.core.designsystem.component.ContentState

/**
 * Temporary screen bodies for the navigation shell.
 * Each later plan replaces exactly one of these with the real screen.
 */
@Composable
fun PlaceholderScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    ContentState(
        isLoading = false,
        error = null,
        data = label,
        modifier = modifier,
        isEmpty = { it.isEmpty() },
    ) { value ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
```

- [ ] **Step 4: Write the nav host**

Create `app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt`:

```kotlin
package io.github.mabrur.streamly.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.mabrur.streamly.ui.navigation.StreamlyKey
import io.github.mabrur.streamly.ui.navigation.TopLevelDestination
import io.github.mabrur.streamly.ui.navigation.startKeyFor
import io.github.mabrur.streamly.ui.placeholder.PlaceholderScreen

@Composable
fun StreamlyApp(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val startKey = startKeyFor(sessionState)

    if (startKey == null) {
        // Session still resolving. Showing Onboarding here would flash it at a
        // signed-in user on every cold start.
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Keying on the start destination means sign-out rebuilds the host with a fresh
    // stack rooted at Onboarding, and sign-in rebuilds it rooted at Home. That is the
    // "clear, don't push" requirement without any manual back-stack surgery.
    key(startKey) {
        StreamlyNavHost(startKey = startKey, modifier = modifier)
    }
}

@Composable
private fun StreamlyNavHost(
    startKey: StreamlyKey,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(startKey)
    val currentKey = backStack.lastOrNull()
    val showBottomBar = TopLevelDestination.entries.any { it.key == currentKey }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentKey == destination.key,
                            onClick = {
                                if (currentKey != destination.key) {
                                    // Top-level switches reset the stack rather than
                                    // stacking destinations, keeping Back predictable.
                                    backStack.clear()
                                    backStack.add(destination.key)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = { backStack.removeLastOrNull() },
            // REQUIRED — NavDisplay's default decorators do NOT include ViewModel
            // scoping, and navigation3-ui does not even depend on the artifact that
            // provides it. Without this line every ViewModel is Activity-scoped,
            // onCleared() never fires on pop, and the ExoPlayer leaks.
            // See docs/decisions.md D-007.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider<NavKey> {
                entry<StreamlyKey.Onboarding> { PlaceholderScreen("Onboarding") }
                entry<StreamlyKey.Home> { PlaceholderScreen("Home") }
                entry<StreamlyKey.Shorts> { PlaceholderScreen("Shorts") }
                entry<StreamlyKey.Downloads> { PlaceholderScreen("Downloads") }
                entry<StreamlyKey.Profile> { PlaceholderScreen("Profile") }
                entry<StreamlyKey.Player> { key -> PlaceholderScreen("Player ${key.videoId}") }
            },
        )
    }
}
```

- [ ] **Step 5: Host it from MainActivity**

Replace the `setContent` block in `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`:

```kotlin
package io.github.mabrur.streamly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyTheme
import io.github.mabrur.streamly.ui.StreamlyApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamlyTheme {
                StreamlyApp()
            }
        }
    }
}
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

If `entry<StreamlyKey.Player> { key -> ... }` fails to infer, annotate explicitly: `entry<StreamlyKey.Player> { key: StreamlyKey.Player -> ... }`.

`Icons.Filled.Home/PlayArrow/List/Person` are all in `material-icons-core`, which
`material3` pulls in transitively. If any is unresolved, declare it explicitly — add to
`[libraries]` in `gradle/libs.versions.toml`:

```toml
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
```

then add `implementation(libs.androidx.compose.material.icons.core)` to `app/build.gradle.kts`.
Do **not** add `material-icons-extended` — it is large and unnecessary for four icons.

- [ ] **Step 7: Prove the ViewModel decorator actually scopes — do not skip**

This is the one behaviour that later plans silently depend on. Add a temporary probe: in `AppViewModel`, add

```kotlin
    override fun onCleared() {
        android.util.Log.d("StreamlyVM", "AppViewModel cleared")
    }
```

Then add a throwaway `@HiltViewModel class ProbeViewModel @Inject constructor() : ViewModel() { override fun onCleared() { android.util.Log.d("StreamlyVM", "ProbeViewModel cleared") } }`, obtain it inside the `entry<StreamlyKey.Player>` body with `hiltViewModel<ProbeViewModel>()`, then on a device: navigate Home → Player → Back, and confirm `ProbeViewModel cleared` appears in logcat.

**If that log never appears, the decorator is not wired and every later Media3 plan is built on sand — stop and fix it here.**

Remove both probes once confirmed.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/mabrur/streamly/ui \
        app/src/main/java/io/github/mabrur/streamly/MainActivity.kt
git commit -m "feat(nav): NavDisplay host with entry-scoped ViewModels and bottom bar" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 6: `WindowSizeClass` plumbing

Adaptive layout must be built in from the start, per the PRD's hard-constraints table.

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`
- Modify: `app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt`

**Interfaces:**
- Produces: `StreamlyApp(windowSizeClass: WindowSizeClass, ...)`. Later plans read `windowSizeClass.widthSizeClass` to choose one- versus two-pane layouts.

- [ ] **Step 1: Add the dependency**

In `app/build.gradle.kts`, add to `dependencies { }`:

```kotlin
    implementation(libs.androidx.compose.material3.window.size)
```

The catalog alias already exists from the foundation plan.

- [ ] **Step 2: Compute it in the activity and pass it down**

In `MainActivity.kt`, replace the `setContent` block:

```kotlin
        setContent {
            StreamlyTheme {
                StreamlyApp(windowSizeClass = calculateWindowSizeClass(this))
            }
        }
```

and add the imports:

```kotlin
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
```

`calculateWindowSizeClass` is experimental, so annotate the class:

```kotlin
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
```

- [ ] **Step 3: Accept and forward it**

In `StreamlyApp.kt`, change the two signatures. `StreamlyApp` becomes:

```kotlin
@Composable
fun StreamlyApp(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
```

and its `key(startKey)` body becomes:

```kotlin
    key(startKey) {
        StreamlyNavHost(
            startKey = startKey,
            windowSizeClass = windowSizeClass,
            modifier = modifier,
        )
    }
```

`StreamlyNavHost` becomes:

```kotlin
@Composable
private fun StreamlyNavHost(
    startKey: StreamlyKey,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
```

Add the import:

```kotlin
import androidx.compose.material3.windowsizeclass.WindowSizeClass
```

`windowSizeClass` is intentionally unused for now — the placeholder screens have no layout to adapt. Later plans consume it. Suppress the warning at the parameter if the build treats it as an error.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/io/github/mabrur/streamly/MainActivity.kt \
        app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt
git commit -m "feat(ui): plumb WindowSizeClass from the activity" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 7: Full verification pass

- [ ] **Step 1: Run every test**

Run: `./gradlew :domain:test :data:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest`
Expected: 44 tests total — 8 domain, 20 data, 6 designsystem, 10 app. `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm no Nav2 anywhere in the dependency graph**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath > /tmp/streamly-deps.txt
echo "--- Nav2 (expect no output) ---"
grep -E 'androidx.navigation:navigation-(compose|runtime|fragment)' /tmp/streamly-deps.txt || echo "clean"
echo "--- Nav3 (expect hits) ---"
grep -E 'androidx.navigation3|lifecycle-viewmodel-navigation3' /tmp/streamly-deps.txt | sort -u
echo "--- forbidden stacks (expect no output) ---"
grep -E 'io.reactivex|com.squareup.retrofit2|androidx.fragment:fragment' /tmp/streamly-deps.txt || echo "clean"
```

Expected: `clean` for Nav2 and forbidden stacks; Nav3 artifacts present.

- [ ] **Step 3: Confirm layering still holds**

```bash
echo "--- data internals leaking (expect no output) ---"
grep -rn "io.ktor\|remote.dto" app/src core domain/src --include=*.kt || echo "clean"
echo "--- android imports in :domain (expect no output) ---"
grep -rn "^import android\|^import androidx" domain/src --include=*.kt || echo "clean"
echo "--- AndroidView anywhere (expect no output) ---"
grep -rn "AndroidView" app/src core --include=*.kt || echo "clean"
```

Expected: `clean` on all three.

- [ ] **Step 4: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Needs device verification** (cannot be checked from here — report as pending, never as done):
  - App launches to Onboarding on a fresh install; to Home when a session exists.
  - Bottom bar switches between Home / Shorts / Downloads / Profile.
  - Bottom bar is visible on Shorts, hidden on Player.
  - Rotation preserves the back stack and the selected tab.
  - Process death (Developer Options → "Don't keep activities") restores the back stack.
  - The Task 5 Step 7 probe logs `ProbeViewModel cleared` on Back from Player.

---

## Task 8: Decision record

**Files:**
- Modify: `docs/decisions.md` (append only — never edit D-001 … D-006)

- [ ] **Step 1: Append D-007**

Append to `docs/decisions.md`:

```markdown

---

## D-007 — `NavDisplay` entry decorators are passed explicitly

**Status:** Accepted · 2026-07-27

`NavDisplay` is always called with an explicit `entryDecorators` list:

    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

Decompiling `navigation3-ui` 1.1.4 shows its default decorator set is
`rememberSceneSetupNavEntryDecorator`, `rememberSaveableStateHolderNavEntryDecorator`,
`rememberBackStackAwareLifecycleNavEntryDecorator`, and
`rememberSharedEntryInSceneNavEntryDecorator`. ViewModel scoping is absent, and the
POM does not depend on `lifecycle-viewmodel-navigation3` at all, so it cannot be
included by default.

Without the explicit list, every screen ViewModel is Activity-scoped: `onCleared()`
never fires on pop, the long-form `ExoPlayer` is never released, and the app leaks a
player per Player-screen visit. Playback still appears to work, so the defect is
invisible without LeakCanary — which is what makes it worth recording.

**Consequence:** any future `NavDisplay` call site must pass this list. Adding one
without it silently reintroduces the leak.
```

- [ ] **Step 2: Commit**

```bash
git add docs/decisions.md
git commit -m "docs(decisions): record D-007 explicit NavDisplay entry decorators" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Definition of done for this plan

- [ ] `./gradlew assembleDebug` — `BUILD SUCCESSFUL`
- [ ] 44 unit tests passing across all four modules
- [ ] No Nav2, RxJava, Retrofit, or Fragment artifacts in `debugRuntimeClasspath`
- [ ] No `AndroidView` anywhere; no Ktor/DTO types outside `:data`; no Android imports in `:domain`
- [ ] `docs/decisions.md` contains D-001 … D-007
- [ ] ViewModel-per-`NavEntry` scoping **empirically confirmed** via the Task 5 Step 7 probe
- [ ] Device checks listed in Task 7 reported as *needs verification*

**Next plan:** Home feed (build-plan Phase 3) — the first real MVI screen, replacing the
`StreamlyKey.Home` placeholder. It needs no new API research; Plan 4 (Player) does, and
must verify `media3-ui-compose` 1.10.1 the same way this plan verified Nav3.
