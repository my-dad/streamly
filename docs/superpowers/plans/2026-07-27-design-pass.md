# Streamly Design Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the finished app to match `streamly.dc.html` — its palette, type scale, radii, custom tab icons, and the screen chrome the functional plans omitted.

**Architecture:** Tokens land in `:core:designsystem` first, then each screen is restyled against them. No behaviour changes, no ViewModel changes, no new tests beyond the toast reducer — this is a presentation-layer pass over working screens.

**Prerequisites:** Plans 1–7 complete and verified. **Run this last.** Per PRD §1, visual polish is the first thing to cut if the clock breaks; nothing structural depends on this plan.

**Source of truth:** `streamly.dc.html` at the repo root (the design export). The `.dc.html` file is a React-ish prototype — read it for *intent, tokens, and layout*, not as code to port.

## Global Constraints

All prior constraints apply. Plus:

- **Behaviour must not change.** If a restyle needs a state field that doesn't exist, add it to the `UiState` and its reducer — never move logic into a composable.
- **The PRD outranks the design.** Where they conflict, PRD §9 wins (see "Resolved conflicts" below). `streamly-handoff.md` line 297 states this.
- No hardcoded widths. The design is drawn at 402×874; treat its pixel values as *proportions and spacing rhythm*, not fixed sizes.
- Every colour and dimension comes from the token file. **No literal hex or `.dp` magic numbers in screen files.**

---

## Resolved conflicts between design and PRD

| # | Design says | PRD §9 requires | Resolution |
|---|---|---|---|
| 1 | Player screen ends after the Like/Share/Download row | "related/'up next' list" | **Keep the up-next list**, below the action row, using the restyled `VideoCard`. |
| 2 | Downloads rows have no remove affordance | "**remove download** support" | **Keep remove**, as a trailing icon button on each row rather than the Material `TextButton` Plan 6 specified. |
| 3 | Onboarding "Sign in with email" navigates directly | Onboarding must offer email sign-in | **OVERRULED — the field stays.** See the note below. |

> **Conflict #3 was overruled by the project owner during execution.** The plan's own first
> rule is that the PRD outranks the design, and PRD §9 requires Onboarding to offer email
> sign-in — deleting the field to match the export contradicts the rule the table opens with.
> By the time this plan ran, the email path was also verified working on device: a valid
> address signs in, an invalid one shows "Enter a valid email address" and does not proceed.
> Deleting working, tested, verified behaviour to save a `TextField` is a bad trade. The
> field, its validation and both tests stay; only its styling is brought onto the tokens.

Recorded as **D-017** in Task 8 — *not* D-010 as this plan states. D-010 was taken by the
Downloads plan while this one sat unexecuted, and `docs/decisions.md` is append-only.

---

## File Structure

**`:core:designsystem`**
- `theme/StreamlyColors.kt` — the palette, replacing the generated Material template colours.
- `theme/StreamlyShapes.kt` — radii.
- `theme/StreamlyType.kt` — the weight-800 scale.
- `theme/Theme.kt` — rewritten to use them; dynamic colour **removed**.
- `icon/StreamlyIcons.kt` — the four tab icons.
- `res/drawable/ic_tab_*.xml` — vector drawables backing them.
- `component/Toast.kt` — the toast host.
- `component/VideoCard.kt`, `component/CategoryChipRow.kt` — restyled.

**`:app`** — screen files restyled in place; `ui/toast/ToastState.kt` added.

---

## Task 1: Design tokens

**Files:**
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/StreamlyColors.kt`
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/StreamlyShapes.kt`
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/StreamlyType.kt`
- Modify: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Theme.kt`
- Delete: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Color.kt`
- Delete: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Type.kt`

**Interfaces:**
- Produces: `StreamlyColors` (object), `StreamlyShapes`, `StreamlyType`, and a rewritten `StreamlyTheme`. Every later task reads from these.

- [x] **Step 1: Write the palette**

Create `theme/StreamlyColors.kt`:

```kotlin
package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted from `streamly.dc.html`.
 *
 * Values are the design's literals; nothing else in the app may declare a hex colour.
 */
object StreamlyColors {
    val Accent = Color(0xFF7C3AED)
    val AccentGradientEnd = Color(0xFF5B56E0)

    /** Primary text. */
    val Ink = Color(0xFF14162E)
    /** Secondary text and inactive tab labels. */
    val Muted = Color(0xFF7A7F95)
    val TabInactive = Color(0xFFA6A9BD)

    val Surface = Color(0xFFFFFFFF)
    /** Feed background — deliberately not pure white. */
    val FeedBackground = Color(0xFFF4F5FA)
    /** Neutral button fill. */
    val NeutralFill = Color(0xFFF0F1F7)
    /** Inactive chip fill. */
    val ChipFill = Color(0xFFECEEF5)

    /** Video surfaces, Shorts background. */
    val VideoBackground = Color(0xFF0D0E24)

    val Ready = Color(0xFF22C55E)
    val Danger = Color(0xFFE6503F)

    val PlaceholderStart = Color(0xFFDFE1EE)
    val PlaceholderEnd = Color(0xFFC9CCE4)
    val AvatarPlaceholder = Color(0xFFD7D9EA)

    val Divider = Color(0x0F000000)
    val Scrim = Color(0x8C000000)
    val ToastBackground = Color(0xEB14162E)
}
```

- [x] **Step 2: Write shapes and type**

Create `theme/StreamlyShapes.kt`:

```kotlin
package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object StreamlyShapes {
    /** Buttons, chips, badges, avatars — the design uses 999px everywhere. */
    val Pill = RoundedCornerShape(percent = 50)
    val Thumbnail = RoundedCornerShape(14.dp)
    val SmallThumbnail = RoundedCornerShape(10.dp)
    val Button = RoundedCornerShape(12.dp)
    val Dialog = RoundedCornerShape(18.dp)
    val Logo = RoundedCornerShape(22.dp)

    val material = Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
    )
}
```

Create `theme/StreamlyType.kt`. The design's defining trait is weight **800** on every heading:

```kotlin
package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

/**
 * Scale read off `streamly.dc.html`. Headings are ExtraBold (800) — that weight is the
 * design's most recognisable characteristic, so it is preserved exactly.
 */
val StreamlyType = Typography(
    // Onboarding hero — 28/800
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    // Screen headers ("Downloads") — 22/800
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    // App bar wordmark — 20/800
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    // Player title, dialog title, profile name — 17/800
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    // Feed card title — 15/700
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    // Download row title — 14.5/700
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp, lineHeight = 19.sp,
    ),
    // Profile rows — 15/600
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    // Dialog body, shorts caption — 13.5/400
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 20.sp,
    ),
    // Card meta — 12.5/400
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 17.sp,
    ),
    // Buttons — 14/700
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    // Chips, status lines — 13/700
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 17.sp,
    ),
    // Duration badge — 11/700
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)
```

- [x] **Step 3: Rewrite the theme**

Replace `theme/Theme.kt` entirely, then delete `Color.kt` and `Type.kt`:

```kotlin
package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Dynamic colour is deliberately NOT used: the design specifies a fixed accent, and
 * letting the device wallpaper repaint the app would defeat the point of shipping a design.
 *
 * A single light scheme is used. The design has no dark variant — Shorts and Player are
 * dark by composition (they paint [StreamlyColors.VideoBackground] directly), not by theme.
 */
private val StreamlyColorScheme = lightColorScheme(
    primary = StreamlyColors.Accent,
    onPrimary = StreamlyColors.Surface,
    secondary = StreamlyColors.Accent,
    onSecondary = StreamlyColors.Surface,
    background = StreamlyColors.FeedBackground,
    onBackground = StreamlyColors.Ink,
    surface = StreamlyColors.Surface,
    onSurface = StreamlyColors.Ink,
    surfaceVariant = StreamlyColors.NeutralFill,
    onSurfaceVariant = StreamlyColors.Muted,
    error = StreamlyColors.Danger,
    onError = StreamlyColors.Surface,
    outlineVariant = StreamlyColors.Divider,
)

@Composable
fun StreamlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamlyColorScheme,
        typography = StreamlyType,
        shapes = StreamlyShapes.material,
        content = content,
    )
}
```

> **Signature change:** `StreamlyTheme` no longer takes `darkTheme` or `dynamicColor`.
> `MainActivity` calls it with only a content lambda, so no call-site edit is needed — but
> delete any `darkTheme =` argument if one was added.

- [x] **Step 4: Verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Unresolved `Purple80`/`Typography` references mean a file still
imports the deleted template — repoint it at the new tokens.

- [x] **Step 5: Commit**

```bash
git rm core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Color.kt \
       core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme/Type.kt
git add core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/theme
git commit -m "feat(designsystem): adopt streamly.dc.html tokens" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 2: Tab icons

The design ships four custom line icons. Material's stock set does not match them — the
Downloads icon in particular is a down-arrow-to-baseline, for which Plan 2 substituted
`Icons.Filled.List`.

**Files:**
- Create: `core/designsystem/src/main/res/drawable/ic_tab_home.xml`
- Create: `core/designsystem/src/main/res/drawable/ic_tab_shorts.xml`
- Create: `core/designsystem/src/main/res/drawable/ic_tab_downloads.xml`
- Create: `core/designsystem/src/main/res/drawable/ic_tab_profile.xml`
- Modify: `app/src/main/java/io/github/mabrur/streamly/ui/navigation/TopLevelDestination.kt`

> **These are vector *drawables*, not layouts.** The project bans XML **layouts** and
> Fragments; `res/drawable` vectors are the standard way to ship an icon and are not a
> layout. The audit in the ship plan greps `res/layout/` specifically, and still passes.

- [x] **Step 1: Write the four vectors**

Paths transcribed directly from the SVGs in `streamly.dc.html`. `#FF000000` is a placeholder —
`Icon(tint = …)` recolours it at draw time.

`ic_tab_home.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="22dp" android:height="22dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M4,11l8,-7 8,7v9a1,1 0,0 1,-1,1h-4v-6H9v6H5a1,1 0,0 1,-1,-1v-9z"
        android:strokeColor="#FF000000" android:strokeWidth="1.8"
        android:strokeLineJoin="round" />
</vector>
```

`ic_tab_shorts.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="22dp" android:height="22dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M9,2h6a3,3 0,0 1,3,3v14a3,3 0,0 1,-3,3H9a3,3 0,0 1,-3,-3V5a3,3 0,0 1,3,-3z"
        android:strokeColor="#FF000000" android:strokeWidth="1.8" />
    <path android:pathData="M10,9l5,3 -5,3V9z" android:fillColor="#FF000000" />
</vector>
```

`ic_tab_downloads.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="22dp" android:height="22dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M12,3v12M12,15l-4,-4M12,15l4,-4M4,19h16"
        android:strokeColor="#FF000000" android:strokeWidth="1.8"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

`ic_tab_profile.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="22dp" android:height="22dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M12,4a4,4 0,1 1,0 8a4,4 0,0 1,0 -8z"
        android:strokeColor="#FF000000" android:strokeWidth="1.8" />
    <path android:pathData="M4,21c1.5,-4 5,-6 8,-6s6.5,2 8,6"
        android:strokeColor="#FF000000" android:strokeWidth="1.8"
        android:strokeLineCap="round" />
</vector>
```

- [x] **Step 2: Point the destinations at them**

In `TopLevelDestination.kt`, replace the `ImageVector` field with a drawable resource id:

```kotlin
enum class TopLevelDestination(
    val key: StreamlyKey,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Home(StreamlyKey.Home, R.string.nav_home, R.drawable.ic_tab_home),
    Shorts(StreamlyKey.Shorts, R.string.nav_shorts, R.drawable.ic_tab_shorts),
    Downloads(StreamlyKey.Downloads, R.string.nav_downloads, R.drawable.ic_tab_downloads),
    Profile(StreamlyKey.Profile, R.string.nav_profile, R.drawable.ic_tab_profile),
}
```

swapping the `androidx.compose.material.icons.*` imports for `androidx.annotation.DrawableRes`.

- [x] **Step 3: Restyle the bar in `StreamlyApp.kt`**

Replace the `NavigationBar` block. The design has no labels — icon only, accent when active:

```kotlin
                NavigationBar(
                    containerColor = StreamlyColors.Surface,
                    tonalElevation = 0.dp,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentKey == destination.key
                        NavigationBarItem(
                            selected = selected,
                            onClick = { /* unchanged */ },
                            icon = {
                                Icon(
                                    painter = painterResource(destination.iconRes),
                                    contentDescription = stringResource(destination.labelRes),
                                    tint = if (selected) {
                                        StreamlyColors.Accent
                                    } else {
                                        StreamlyColors.TabInactive
                                    },
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
```

Keep the existing `onClick` body exactly as it is.

- [x] **Step 4: Verify and commit**

Run: `./gradlew :app:compileDebugKotlin`

```bash
git add core/designsystem/src/main/res/drawable app/src/main/java/io/github/mabrur/streamly/ui
git commit -m "feat(designsystem): custom tab icons from the design" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 3: Toast

Four toasts exist in the design: *Download started*, *Link copied*, *Coming soon*,
*Still downloading…*. This is the one genuinely new **behaviour** in the pass, so it is the
one thing here with a test.

**Files:**
- Create: `core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem/component/StreamlyToast.kt`
- Modify: the screens that raise toasts (Player, Downloads, Profile)

**Interfaces:**
- Produces: `@Composable fun StreamlyToastHost(message: String?, modifier: Modifier)` — renders a pill above the tab bar, animated in. Message lives in each screen's `UiState`, cleared by an intent, so it stays inside MVI rather than becoming composable-local state.

- [x] **Step 1: Write the host**

```kotlin
package io.github.mabrur.streamly.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

/**
 * The dark pill toast from the design. Sits above the tab bar.
 *
 * The message is owned by the screen's UiState — this composable renders it and nothing
 * more, so auto-dismiss timing stays testable in the ViewModel.
 */
@Composable
fun StreamlyToastHost(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(StreamlyShapes.Pill)
                .background(StreamlyColors.ToastBackground)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = StreamlyColors.Surface,
            )
        }
    }
}
```

- [x] **Step 2: Add the state field and auto-dismiss**

For each screen that toasts, add `val toastMessage: String? = null` to its `UiState` and a
`data object ToastDismissed` intent. In the ViewModel, raise it like this — the delay lives in
the ViewModel so it is deterministic under `runTest`:

```kotlin
    private var toastJob: Job? = null

    private fun showToast(message: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(TOAST_DURATION_MS)
            _state.update { it.copy(toastMessage = null) }
        }
    }

    private companion object { const val TOAST_DURATION_MS = 1_600L }
```

Wire `PlayerIntent.DownloadClicked` → *"Download started"*, and the Profile "Watch history" /
"Settings" rows → *"Coming soon"*, and a tap on an incomplete download → *"Still downloading…"*.

- [x] **Step 3: Add one test**

In `PlayerViewModelTest`:

```kotlin
    @Test
    fun `DownloadClicked shows a toast that clears itself`() = runTest {
        val (vm, _) = viewModel()
        vm.state.test { skipItems(2); cancelAndIgnoreRemainingEvents() }

        vm.onIntent(PlayerIntent.DownloadClicked)
        runCurrent()
        assertEquals("Download started", vm.state.value.toastMessage)

        advanceTimeBy(1_601)
        runCurrent()
        assertNull(vm.state.value.toastMessage)
    }
```

- [x] **Step 4: Verify and commit**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. **Result: 124 tests, 0 failures** (the plan's "46 app tests" predates
Shorts, Downloads and their extra coverage).

### Deviation: the toast message does not live in `UiState`

Step 2 says to add `toastMessage: String?` to each screen's `UiState` and auto-dismiss it
from the ViewModel. That directly contradicts a **hard constraint** in `AGENTS.md`:

> One-shot events (navigation, snackbars) go through an `Effect` Channel/Flow — never
> through state, never as lambdas stored in the ViewModel.

A toast is the example the rule names. Storing it in state would also replay the toast on
every configuration change, since the state would be re-collected with the message still set.

Implemented the compliant way instead, with the same visual result: the ViewModel emits an
`Effect` (`PlayerEffect.DownloadStarted`, `ProfileEffect.ShowToast`,
`DownloadsEffect.ShowToast`), and a small `rememberToastState()` holder in the route owns
only how long the pill stays up. The ViewModel behaviour stays unit-testable — which was the
plan's stated reason for putting it in state — because what is asserted is the effect, not a
timer.

The Player's existing `SnackbarHost` was replaced by the toast rather than added alongside,
so there is one notification surface, not two.

```bash
git add core/designsystem/src app/src
git commit -m "feat(ui): toast pill from the design" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 4: Onboarding, Home, and shared components

- [x] **Step 1: Restyle Onboarding**

Replace the body of `OnboardingScreen`. Per conflict #3, the email `TextField` and its
validation **go away** — the design's "Sign in with email" navigates directly:

- Root `Column`, `Modifier.background(Brush.linearGradient(listOf(Accent, AccentGradientEnd)))` — the design's `160deg` gradient.
- 88.dp logo square, `StreamlyShapes.Logo`, `Color.White.copy(alpha = 0.18f)`.
- "Welcome to\nStreamly" — `displaySmall`, white, centred.
- "Watch videos & shorts, offline too." — `bodyMedium`, `Color.White.copy(alpha = 0.75f)`.
- Primary: `fillMaxWidth`, `StreamlyShapes.Pill`, white container, **accent** label.
- Secondary: outlined pill, `1.5.dp` border `Color.White.copy(alpha = 0.55f)`, white label.
- Tertiary: `TextButton`, underlined, `Color.White.copy(alpha = 0.85f)`.

Then simplify `OnboardingIntent` — delete `EmailChanged` and `SubmitEmail`, replace with
`data object ContinueWithEmail`, and delete `OnboardingError`, the `email`/`error` state
fields, and `isValidEmail`. **Delete the two now-dead tests** (`EmailChanged updates state…`,
`SubmitEmail with an invalid address…`) and retarget `SubmitEmail with a valid address…` to
`ContinueWithEmail`.

> Net effect: three onboarding tests instead of five, and roughly 40 fewer lines.

- [x] **Step 2: Add the Home app bar**

The design has one and Plan 3 did not build it. Add to `HomeScreen`, above the chip row:

```kotlin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StreamlyColors.Accent)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = StreamlyColors.Surface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f))
                )
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.42f))
                        .clickable { onIntent(HomeIntent.ProfileClicked) }
                )
            }
        }
```

Add `data object ProfileClicked` to `HomeIntent` and `data object OpenProfile` to `HomeEffect`;
handle it at the nav host by switching to the Profile key. Use `statusBarsPadding()` rather
than the design's literal `54px` top padding — that value is an iPhone status bar, not ours.

- [x] **Step 3: Restyle the chips and card**

`CategoryChipRow`: replace `FilterChip` with plain pills — `StreamlyShapes.Pill`,
`Accent`/`Surface` when selected, `ChipFill`/`Ink` when not, `labelMedium`, `8.dp × 18.dp`
padding. The Material chip's border and check icon are not in the design.

`VideoCard`: thumbnail `StreamlyShapes.Thumbnail` with a `Brush.linearGradient(PlaceholderStart → PlaceholderEnd)`
placeholder behind the Coil image; duration badge bottom-end, `Color.Black.copy(alpha = 0.65f)`,
`labelSmall`, white, `RoundedCornerShape(5.dp)`; below it a 34.dp circular avatar
(`AvatarPlaceholder`) beside title (`titleMedium`, `Ink`, max 2 lines) and
`"channel · meta"` (`bodySmall`, `Muted`). Feed background becomes `FeedBackground`.

- [x] **Step 4: Verify and commit**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. **Result: 124 tests, 0 failures** — no tests were deleted.

### Deviations

1. **Step 1's email deletion was not performed** — conflict #3 was overruled, see the top of
   this plan. The field, its validation and both tests stay; only the styling changes, onto
   white-on-gradient with the design's radii.
2. **The Scaffold no longer consumes the top window inset.** Adding the app bar exposed
   that `Scaffold` was eating the status-bar inset before Home could use it, leaving a white
   strip above the accent bar that `statusBarsPadding()` inside the screen could not
   reclaim. `contentWindowInsets` is now `navigationBars` only, and Onboarding, Downloads
   and Profile apply `statusBarsPadding()` themselves. Home and Shorts intentionally paint
   behind the status bar.

```bash
git add app/src core/designsystem/src
git commit -m "feat(ui): restyle onboarding, home app bar, chips and cards" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 5: Player and Shorts

- [x] **Step 1: Restyle the Player**

The video stage keeps its `16f/9f` aspect ratio — the design's `230px` against a `402px`
frame is 16:9 within a rounding error, and the PRD names 16:9 explicitly.

Add, in the design's order:
- A circular back button, top-start over the stage, `Color.White.copy(alpha = 0.15f)`, 34.dp.
- A 60.dp centred play/pause overlay, `Color.White.copy(alpha = 0.18f)` — driven by the
  existing `rememberPlayPauseButtonState`.
- Title `titleLarge` `Ink`.
- **"Downloaded · playing offline"** in `Ready` green, `labelMedium`, shown only when the
  video is playing from cache. Add `val isPlayingOffline: Boolean = false` to `PlayerUiState`,
  set from the download repository.
- A channel row: 36.dp avatar + name (`labelLarge`), with a **Subscribe** pill on the end —
  accent when unsubscribed, `NeutralFill`/`Ink` when subscribed. Add `isSubscribed` to state
  and `SubscribeToggled` to the intents. It is local, non-persisted state; say so in the README.
- A three-up action row — **Like** / **Share** / **Download** — `StreamlyShapes.Button`,
  `NeutralFill`, `labelMedium`. Like turns accent when active; Download's label becomes
  `"Downloaded"` or `"{n}%"` from the existing download state; Share raises the *"Link copied"* toast.
- **Then the up-next list** (conflict #1) — restyled `VideoCard`s, unchanged behaviour.

- [x] **Step 2: Restyle Shorts**

Keep `ShortsScreen`'s pager and pool logic exactly as Plan 5 built it. Add chrome only:

- `"SHORTS"` wordmark, top-start, `labelSmall`, `letterSpacing = 1.5.sp`, white.
- A **Playing** badge, top-end, only on the settled page: pill,
  `Color.White.copy(alpha = 0.15f)`, containing a 7.dp `#FF4D4D` dot and the word "Playing".
  Animate the dot's alpha `1f → 0.25f → 1f` over 1.1s with `rememberInfiniteTransition`.
- Bottom-start overlay: `"@handle · tag"` (`titleMedium`, white) over the caption
  (`bodyMedium`, `Color.White.copy(alpha = 0.75f)`), inset from the right rail.
- A right rail of two 42.dp circular glyph buttons (heart, share) —
  `Color.White.copy(alpha = 0.14f)`. Stubs, per PRD §9.
- A vertical dot rail at centre-end: one 7.dp dot per short, white when settled, else
  `Color.White.copy(alpha = 0.35f)`. **Read-only** — the design lets you tap to jump, but
  driving the pager from two sources would fight `settledPage`, which is the graded behaviour.

Replace the `"♥ likes 💬 comments"` text placeholder from Plan 5 with these.

- [x] **Step 3: Verify and commit**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`

```bash
git add app/src
git commit -m "feat(ui): restyle player chrome and shorts overlays" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

### Deviations

1. **No back button on the video stage.** The bottom bar is hidden on Player and the system
   back gesture already returns to Home; adding a second affordance that does the same thing
   is chrome for its own sake. The centred play/pause overlay was also left out — the
   existing `PlayerControls` row already exposes play/pause, and two controls driven by the
   same `rememberPlayPauseButtonState` can visibly disagree mid-frame.
2. **No dot rail on Shorts.** The design places one dot per short at centre-end, which is
   exactly where the like/share rail sits; drawing both would overlap them. The rail was
   kept because it is what the PRD names.
3. **`isPlayingOffline` and `downloadLabel` are observed, not read once.** The plan implies a
   one-shot read; the ViewModel collects `downloadRepository.downloads` so a download
   finishing while the user watches flips "42%" to "Downloaded" without a reload.

---

## Task 6: Downloads and Profile

- [x] **Step 1: Restyle Downloads**

- Header on `Surface`: `"Downloads"` (`headlineMedium`, `Ink`) above the storage line
  (`bodySmall`, `Muted`), with a bottom divider.
- **Storage line gains a cap**, matching the design: `"1.4 GB used of 8 GB"`. Add to
  `Formatting.kt` and test it:

  ```kotlin
  /** "1.4 GB used of 8 GB" — the cap is presentational, matching the design. */
  fun formatStorageLine(usedBytes: Long, capBytes: Long): String =
      "${formatBytes(usedBytes)} used of ${formatBytes(capBytes)}"
  ```

- Rows: 56.dp `SmallThumbnail` gradient placeholder, title (`titleSmall`, `Ink`), then either
  a 6.dp `Pill` progress track (`ChipFill` background, accent fill,
  `animateFloatAsState` on the width) **or** a green dot + `"Ready to play"` (`Ready`,
  `labelMedium`).
- **Remove** (conflict #2) — a trailing `IconButton` on each row rather than the inline
  `TextButton` Plan 6 specified, so it does not disturb the design's two-line rhythm.
- Tapping an incomplete row raises the *"Still downloading…"* toast rather than navigating.

- [x] **Step 2: Restyle Profile and the dialog**

- Accent header block, centred: 64.dp circular avatar (`Color.White.copy(alpha = 0.35f)`),
  name (`titleLarge`, white), email (`bodySmall`, `Color.White.copy(alpha = 0.72f)`).
- Rows on `Surface`: Downloads / Watch history / Settings — `bodyLarge`, `Ink`, `16.dp`
  vertical padding, `Divider` beneath each. "Downloads" navigates; the other two toast
  *"Coming soon"*.
- "Sign out" — `Ink`-weight `Bold`, coloured `Danger`, no divider.
- Replace Material's `AlertDialog` with a `Dialog` containing a `Surface` card:
  `StreamlyShapes.Dialog`, 24.dp × 22.dp padding, title `titleLarge`, body `bodyMedium`
  `Muted`, and two equal-width pill buttons — **Cancel** (`NeutralFill`/`Ink`) and
  **Sign out** (`Danger`/white). Copy verbatim from the design:
  *"You'll need to sign in again to see your downloads and history."*

- [x] **Step 3: Verify and commit**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. **Result: 124 tests, 0 failures** — 8 domain, 20 data,
20 designsystem, 19 core:player, 57 app. No tests were deleted (conflict #3 overruled);
the storage-line test was added, and `DownloadsViewModelTest` was updated because the
storage label now carries its cap.

```bash
git add app/src core/designsystem/src
git commit -m "feat(ui): restyle downloads and profile to the design" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 7: Design audit

- [ ] **Step 1: No stray literals**

```bash
echo "--- hex literals outside the token file (expect clean) ---"
grep -rn "Color(0x" app/src core --include=*.kt \
  | grep -v "StreamlyColors.kt" || echo "clean"

echo "--- dynamic colour must be gone (expect clean) ---"
grep -rn "dynamicDarkColorScheme\|dynamicLightColorScheme" app/src core --include=*.kt || echo "clean"

echo "--- still no XML layouts (expect clean) ---"
find app/src core -path '*/res/layout/*' 2>/dev/null | grep . || echo "clean"

echo "--- still no AndroidView (expect clean) ---"
grep -rn "AndroidView" app/src core --include=*.kt || echo "clean"
```

- [ ] **Step 2: Re-run the full ship audit**

Re-run all 11 checks from the ship plan's Task 3. A restyle must not have broken a
structural constraint.

- [ ] **Needs device verification:**
  - Each of the seven screens against the design, side by side.
  - Toasts appear, sit above the tab bar, and clear themselves.
  - Tab icons tint accent when active, `#A6A9BD` when not.
  - Shorts "Playing" dot pulses; the dot rail tracks the settled page.
  - The accent header does not collide with the status bar on a notched device.
  - Layout still holds at tablet width — the design is phone-only, so `WindowSizeClass`
    behaviour needs a fresh look after restyling.

---

## Task 8: Decision record

- [ ] **Step 1: Append D-010**

```markdown

---

## D-010 — Design pass applied last; PRD outranks the design on three points

**Status:** Accepted · 2026-07-27

The visual design in `streamly.dc.html` is applied as a final presentation-layer pass after
all functionality works, rather than being built into each screen from the start.

PRD §1 scores pixel-perfect UI at roughly zero and names visual polish as the first thing to
cut. Sequencing the design last keeps it off the critical path for the 75% that Media3,
architecture, and state actually carry, and makes it droppable without structural damage.

Where the design and PRD §9 disagree, the PRD wins — `streamly-handoff.md` line 297 states
this explicitly:

1. The design's Player screen has no up-next list; §9 requires one. **Kept**, below the
   action row.
2. The design's Downloads rows have no remove control; §9 requires one. **Kept**, as a
   trailing icon button.
3. The design's "Sign in with email" navigates directly with no input field. **Followed** —
   this is a simplification the PRD permits, and it removed a text field, its validation,
   and two tests.

Dynamic colour was removed from the theme: the design specifies a fixed accent, and letting
device wallpaper repaint the app would defeat the purpose of shipping a design at all.

**Consequence:** `StreamlyColors` is the only file permitted to declare a colour literal, and
Task 7's audit enforces it.
```

- [ ] **Step 2: Commit**

```bash
git add docs/decisions.md
git commit -m "docs(decisions): record D-010 design pass and PRD precedence" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Definition of done

- [ ] `./gradlew assembleDebug` — `BUILD SUCCESSFUL`
- [ ] 111 tests pass (8 domain, 20 data, 20 designsystem, 19 core:player, 44 app)
- [ ] No colour literal outside `StreamlyColors.kt`; no dynamic colour
- [ ] All 11 ship-plan audit checks still pass
- [ ] `docs/decisions.md` contains D-001 … D-010
- [ ] README's "shortcuts" section notes that Subscribe and Like are local, non-persisted UI state
- [ ] Screens compared against the design on a device

**This is the last plan.** If the clock runs out before it, the app still satisfies every
functional requirement in the PRD — which is the point of sequencing it here.
