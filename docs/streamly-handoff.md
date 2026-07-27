# Streamly — Implementation Handoff Document

**Project:** Mid/Senior Android take-home (doc ref AND-2026-07-STREAMLY v1.1)
**Deadline:** 6 days from receipt of the brief (extensions possible if requested in advance)
**Deliverables:** GitHub repo (public or invite-only) · README · 2–4 min screen-recorded demo of all 7 screens including a real download playing offline · debug APK (or a build-from-source note)

---

## 1. Read this first — how the app is graded

This is not a feature-count contest. The rubric is:

| Area | Weight | What earns it |
|---|---|---|
| Media3 usage | **30%** | Correct player lifecycle, no leaks, real offline downloads, shorts player pooling |
| Architecture | **25%** | Clean layer separation, sensible MVI state/intent modeling, testability |
| Compose & state | **20%** | Idiomatic Compose, predictable recomposition, no state bugs on rotation/navigation |
| Code quality | 10% | Readability, naming, error handling, DI wiring, tests where it matters |
| Polish | 10% | Matches the reference screens' *intent*; empty/loading/error states everywhere |
| AI-first workflow | 5% | Agent config present and symlinked correctly; evidence of real agentic development |

**Time budget accordingly.** Playback correctness + architecture + state = 75% of the score. Pixel-perfect UI = ~0%. If you must cut scope on day 5, cut visual polish and secondary screens — never cut player lifecycle correctness or the downloads flow.

The brief explicitly allows **fake network data**. The architecture, player handling, and code quality must be real.

---

## 2. Hard constraints (violating any of these is an automatic red flag)

| Required | Forbidden |
|---|---|
| Kotlin end to end | Java |
| Coroutines + Flow for concurrency | RxJava |
| Jetpack Compose for **all** UI | XML layouts, Fragments |
| MVVM + MVI combined (one immutable `UiState` via `StateFlow`, sealed `Intent` in) | LiveData-driven UI, mutable state exposed from ViewModel, direct UI → data calls |
| Clean architecture: presentation / domain / data | Android or framework imports in the domain layer |
| Hilt **or** Koin (pick the one you're fluent in) | Manual service locators, no DI |
| Navigation 3 (Nav3) | Old navigation-compose (Nav2), Fragment navigation |
| Ktor as HTTP client (mock or real endpoint — your choice) | Retrofit/OkHttp as the client API |
| Media3 (ExoPlayer) as the only media stack | Any other player library, MediaPlayer |
| **HLS (.m3u8)** via `media3-exoplayer-hls` for normal videos AND shorts | Bundled/local MP4-only shortcuts, progressive-only sources |
| Downloads via Media3 `DownloadManager`/offline module | Fake progress bars, hand-rolled file downloads |
| Adaptive layouts built with `WindowSizeClass` from the start (must hold up on foldables/tablets) | Fixed-width assumptions, phone-only hardcoded layouts |
| One agent rules file (`AGENTS.md`) symlinked to per-tool configs | Hand-maintained duplicate configs per tool |

Bonus (explicitly optional, extra credit, no penalty for skipping): ship shared logic as Kotlin Multiplatform / Compose Multiplatform with an Android target.

---

## 3. Project structure

Keep it simple enough to finish in 6 days but layered enough to show clean architecture. Either a single `:app` module with strict packages, or (better signal) Gradle modules:

```
:app                  // wiring, navigation host, DI graph
:core:designsystem    // theme, typography, shared composables
:core:player          // ExoPlayer holder, player pool, download manager wiring
:data                 // Ktor client, DTOs, repositories impl, fake endpoint
:domain               // pure Kotlin: models, repository interfaces, use cases
:feature:onboarding
:feature:home
:feature:shorts
:feature:player
:feature:downloads
:feature:profile
```

**Do**
- Keep `:domain` a pure Kotlin/JVM module (`kotlin("jvm")`), no `android` plugin at all — this makes "no framework imports in domain" mechanically enforced, and it's the cheapest way to prove the point to a reviewer.
- Depend inward only: feature → domain, data → domain. Features never import data implementations; they get repository *interfaces* injected.
- Use version catalogs (`libs.versions.toml`) and a consistent Kotlin/AGP/Compose BOM set.

**Don't**
- Don't over-modularize into 20 modules; the reviewer will read the code, not admire the graph.
- Don't put mappers, DTOs, or Ktor types anywhere presentation can see them. DTO → domain model mapping happens in `:data`.
- Don't create a `utils` dumping-ground package.

---

## 4. MVI contract (Architecture — 25%)

One ViewModel per screen. Each screen package contains three sealed/immutable types plus the ViewModel:

```kotlin
// HomeContract.kt
data class HomeUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoUi> = emptyList(),
    val error: HomeError? = null,          // sealed type, not a raw String
    val selectedCategory: Category = Category.All,
)

sealed interface HomeIntent {
    data object Refresh : HomeIntent
    data class VideoClicked(val id: String) : HomeIntent
    data class CategorySelected(val category: Category) : HomeIntent
}

sealed interface HomeEffect {                // one-shot events (navigation, snackbars)
    data class OpenPlayer(val id: String) : HomeEffect
}
```

```kotlin
// Hilt shown; if you choose Koin, use koinViewModel() + constructor injection instead
@HiltViewModel
class HomeViewModel @Inject constructor(
    getHomeFeed: GetHomeFeedUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: HomeIntent) { /* single entry point */ }
}
```

**Do**
- State flows down, intents flow up. The UI has exactly two touchpoints: collect `state`, call `onIntent(...)`.
- Keep `UiState` immutable (`data class` + `copy`), one per screen, no `var`s.
- Model errors and empty states *in* the UiState so the Polish rubric line is satisfied by design.
- Use one-shot `Effect`s (Channel/SharedFlow) for navigation and toasts; never navigate from inside state.
- Put business rules in use cases in `:domain` so they're unit-testable without Robolectric.
- Write a handful of focused tests where they matter most: ViewModel intent → state transitions (use `runTest` + Turbine), and the download-state mapper. A few excellent tests beat 40 shallow ones.

**Don't**
- Don't expose `MutableStateFlow` or suspend functions returning UI models directly to composables.
- Don't let composables call repositories or Ktor. "No direct UI → data calls" is quoted verbatim in the brief.
- Don't put navigation lambdas inside the ViewModel; emit effects, handle them at the NavDisplay/host level.
- Don't reach for a heavyweight MVI framework (Orbit, MVIKotlin). Hand-rolled and readable is exactly what they asked for.

---

## 5. Media3 (30% — the make-or-break section)

### 5.1 Normal-video player

- **Single shared ExoPlayer instance** for long-form playback, "released/reused correctly across config changes and navigation" (brief wording). Practically: own the player *outside* the composable — in the player-screen ViewModel or a DI-scoped holder in `:core:player` — never `remember { ExoPlayer.Builder... }` in a composable, or rotation will recreate it.
- Release in `onCleared()` (ViewModel-owned) or when the owning scope dies. Exactly one `release()` per player, guaranteed.
- Wire lifecycle in the UI layer with `LifecycleStartEffect` / `LifecycleResumeEffect` (or a `DisposableEffect` observing the lifecycle): **pause on background (onStop), resume on return, release on screen exit**. The brief lists these three verbatim.
- Controls required: play/pause, seek/scrub, mute, and a **visible buffering state**. `PlayerView` inside `AndroidView`, or Compose-native controls over a `PlayerSurface` — either is fine; buffering indicator must be visibly demoed.
- Player screen layout: 16:9 player on top, metadata below, a **Download** action, and a related/"up next" list.

### 5.2 Shorts (pooled pager)

- Full-screen **vertical pager**, one short per page, **autoplay only the visible/settled item**, swipe up/down to advance. Like/comment/share can be non-functional stubs.
- Required approach: a **separate pooled/pager set of players** — or a documented single-player-per-visible-item strategy — such that **no more than 1–2 players are decoding at once**.
- Recommended concrete design: a `ShortsPlayerPool` in `:core:player` holding 2–3 ExoPlayers (pooled instances may exist, but only the settled one plays and at most one neighbor pre-buffers — that keeps you inside the "no more than 1–2 decoding" rule). The settled page's player plays; the adjacent page's player is `prepare()`d and paused; on page settle, swap roles and re-target the outgoing player to the next-adjacent item. Key off `pagerState.settledPage`, not `currentPage`, so half-swipes don't trigger playback.
- Mute-state and position handling per short should be deliberate (typically shorts restart from 0 and are unmuted; just be consistent).

**Don't (shorts):** one player per page composable; players kept alive off-screen; audio bleeding between pages or between Shorts and Home (the brief calls out "no audio bleeding between screens"); relying on `beyondViewportPageCount` defaults to accidentally instantiate many players.

### 5.3 Downloads (real offline)

- Use Media3's **offline module**: a `DownloadService` subclass + `DownloadManager` + `StandaloneDatabaseProvider` + a dedicated `SimpleCache` (with `NoOpCacheEvictor` — download caches must not evict).
- For HLS, create the `DownloadRequest` via `DownloadHelper` so the playlist and its segments/tracks are captured, then `DownloadService.sendAddDownload(...)`.
- Playback of downloaded content must go through a `CacheDataSource.Factory` pointing at the same download cache (`setCacheWriteDataSinkFactory(null)` for read-only playback), so "Ready to play" items genuinely play offline — the demo must show this **with network off / airplane mode**.
- Surface **real progress** by observing the `DownloadManager` (listener or polling `currentDownloads` percentDownloaded) into a `Flow` → UiState. No fake progress bars — the brief names this explicitly.
- Downloads screen: in-progress with progress state, completed ("Ready to play"), storage-used header, and **remove download** support (`sendRemoveDownload`).
- Manifest/plumbing that's easy to forget: declare the `DownloadService` in the manifest, foreground-service permission + type (`dataSync`), notification channel, and `POST_NOTIFICATIONS` runtime permission on API 33+.

### 5.4 HLS

- HLS is the **required delivery format** for both normal videos and shorts. Add `media3-exoplayer-hls` and feed `.m3u8` URLs; adaptive bitrate switching should work out of the box (don't disable track selection).
- Use public test streams in your fake feed — e.g. the well-known Mux test stream (`https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`) and Apple's sample streams; verify each URL actually plays before shipping the seed data.
- **Don't** bundle local MP4s as the primary path — the brief bans "local MP4-only shortcuts."

---

## 6. Compose & state (20%)

**Do**
- Collect with `collectAsStateWithLifecycle()` everywhere.
- Hoist state; keep composables stateless functions of `UiState` + `(Intent) -> Unit`.
- Prove rotation safety: playback position survives, feed doesn't reload, form/UI selections survive (`ViewModel` for screen state, `rememberSaveable` for purely local UI bits like scroll or text fields).
- Use `LazyColumn`/pager `key = { it.id }` so item state and player targeting stay stable.
- Keep lambdas stable (remember them or use method references) and mark UI models `@Immutable` where relevant — "predictable recomposition" is a graded line.
- Build screens against `WindowSizeClass` from day one (even a simple two-pane/compact split shows intent); no hardcoded widths.
- Give every screen loading, empty, and error states — reuse one `ContentState` wrapper composable so it's systematic, not ad hoc.

**Don't**
- Don't store `Context`, players, or navigation controllers in `remember` across config changes expecting survival.
- Don't do side effects in composition; use `LaunchedEffect`/`DisposableEffect` with correct keys.
- Don't pass ViewModels down the tree; pass state + callbacks.

---

## 7. Navigation 3

- Use Nav3's model: a developer-owned back stack of typed keys (`@Serializable` route objects), rendered by `NavDisplay` with an `entryProvider`. Persist the back stack (`rememberNavBackStack`) so process death/rotation don't lose it.
- Routes: `Onboarding`, `Home`, `Shorts`, `Player(videoId)`, `Downloads`, `Profile`. Session-gated start: if a session exists, start at `Home`, else `Onboarding`.
- Sign-out clears the session and resets the stack to `Onboarding` (clear, don't push).
- Leaving the Player screen must release/detach its player (tie player scope to the entry's ViewModel so Nav3 disposing the entry triggers `onCleared`).
- **Don't** mix in `androidx.navigation:navigation-compose` (Nav2) — the brief names Nav3 specifically, and this is an easy thing for a reviewer to check in the version catalog.

---

## 8. Data layer (Ktor + fake data)

- Ktor client with `ContentNegotiation` + `kotlinx.serialization`. Point it at either a tiny real endpoint or a **MockEngine** that serves a bundled JSON catalog — the brief says mock or real is your choice; MockEngine keeps the demo deterministic and offline-friendly.
- Even with fake data, keep the full pipeline honest: Ktor → DTO → mapper → domain model → repository interface → use case → ViewModel. The point is to show the seams.
- Simulate latency (small `delay`) and an occasional failure path so loading/error states are demonstrably real in the demo.
- Session persistence with `DataStore` (Preferences): onboarding writes a session, app start reads it, sign-out clears it. Returning users skip straight to Home — this is an explicit requirement.

---

## 9. Screen-by-screen acceptance checklist

| # | Screen | Must have | Explicitly allowed shortcuts |
|---|---|---|---|
| 01 | Onboarding | "Continue with Google" (mocked), "Sign in with email", "Continue as guest"; persists session; returning users skip it | No real auth API; social button may navigate straight to Home |
| 02 | Home feed | Scrollable video cards: thumbnail, title, channel, views/age; tap opens Player; category chips present | Chips can be static/non-functional |
| 03 | Shorts | Full-screen vertical pager, one per page, autoplay visible item only, swipe to advance | Like/comment/share = stubs |
| 04 | Player | 16:9 player, standard controls + buffering state, metadata below, Download action, related/"up next" list | — |
| 05 | Downloads | In-progress with real progress, completed "Ready to play" playing from local storage, storage header, remove download | — |
| 06 | Profile | Avatar/name/email, links to downloads/history/settings, sign-out entry | Links can be shallow |
| 07 | Sign out | Confirmation dialog gating sign-out; confirm clears session → back to Onboarding | — |

---

## 10. AI-assisted development (required, 5%, and a stated company value)

You have already rehearsed this workflow end-to-end; repeat it in the real repo:

1. Write **one** `AGENTS.md` at repo root — the single source of truth: project overview, stack constraints (the table in §2 is a good seed), architecture rules, commit conventions.
2. Symlink it to every per-tool path and **commit the symlinks**: `CLAUDE.md`, `.cursor/rules`, `.codex/instructions.md`, `.antigravity/rules`. Verify each is git mode `120000` (`git ls-files -s`).
3. Make the agent visible in history: `Co-authored-by:` trailers via the repo-local `commit.template`, and/or an agent changelog / prompt log. The brief: they're checking "the workflow is real, not retrofitted" — so commit the config **first**, before feature code, and keep agent-assisted commits flowing throughout, not one bulk commit at the end.
4. README must briefly describe the setup and prompting approach.

**Don't:** hand-maintain diverging copies per tool; retrofit the AI evidence on day 6; commit secrets or personal info inside prompt logs.

---

## 11. Repo hygiene & deliverables

**Do**
- Small, conventional commits telling a story (`feat(shorts): pooled pager playback`), green build at every commit if possible.
- README sections: what/why, screenshots, architecture diagram + decisions, module map, how to run, test instructions, AI workflow, **shortcuts taken and why** (the brief invites this — honest tradeoffs read as senior).
- Record the 2–4 min demo covering **all seven screens**, ending with: start a download → watch real progress → enable airplane mode → play it offline. That single sequence proves the 30% category.
- Attach a debug APK to a GitHub release (or state clearly how to build).
- CI (a simple GitHub Actions `build + test`) is cheap and signals professionalism.

**Don't**
- Don't force-push away history (history *is* part of the evidence).
- Don't leave TODOs on rubric-critical paths, dead code, or commented-out experiments.
- Don't submit with a broken cold build — test `git clone` → build on a clean machine/emulator.

---

## 12. Suggested 6-day plan

| Day | Focus |
|---|---|
| 1 | Repo + AGENTS.md + symlinks + commit template (AI workflow banked). Project skeleton, DI, Nav3 shell with placeholder screens, theme. |
| 2 | Data layer: Ktor + fake catalog with real HLS URLs; domain models/use cases; Home feed (MVI, states, cards). |
| 3 | Player screen: shared ExoPlayer, lifecycle handling, controls, rotation-safe position, metadata + up-next. |
| 4 | Shorts: vertical pager + player pool; kill audio bleed; settle-based playback. |
| 5 | Downloads: DownloadService/Manager, progress flow, offline playback, remove; Onboarding + session + Profile/sign-out. |
| 6 | Polish pass (empty/loading/error everywhere), tests, README, demo recording, APK, clean-clone build check. Buffer. |

If you slip a day, the compressible items are Profile depth and visual polish — not Days 3–5 content.

---

## 13. Final pre-submission checklist

- [ ] Rotate during long-form playback → position + play state survive, no player recreation
- [ ] Background app during playback → pauses; return → resumes; exit screen → released (verify with LeakCanary in debug)
- [ ] Shorts: swipe fast through 10 items → no audio overlap, ≤2 players alive (log pool acquisitions)
- [ ] Navigate Home → Player → back repeatedly → no leaks, feed state intact (no reload)
- [ ] Download → airplane mode → plays offline; remove download works
- [ ] Every screen shows sane loading / empty / error states
- [ ] `git ls-files -s` shows all four symlinks as `120000`; agent co-authorship visible across history
- [ ] Domain module has zero Android imports (it can't — it's a JVM module)
- [ ] No Nav2, no RxJava, no Retrofit, no XML layouts anywhere in the dependency tree
- [ ] Clean clone builds; APK attached; demo covers all 7 screens incl. offline playback
- [ ] README: architecture decisions, AI workflow, shortcuts-and-why

---

*Prepared from the Streamly take-home brief (AND-2026-07-STREAMLY v1.1). When the brief and this document disagree, the brief wins — and per its Questions section, ask the hiring team early rather than guessing.*
