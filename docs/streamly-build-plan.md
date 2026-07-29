# Streamly — Phased Build Plan

**Derived from:** `docs/streamly-handoff.md` (PRD, doc ref AND-2026-07-STREAMLY v1.1)
**Created:** 2026-07-27
**Budget:** ~2 days · ~22h estimated · full scope, no cuts

---

## Decisions locked before planning

| # | Decision | Rationale |
|---|---|---|
| D-001 | **Trimmed multi-module** — `:app`, `:domain`, `:data`, `:core:player`, `:core:designsystem` | Makes PRD §13 *"domain has zero Android imports (it can't)"* mechanically true. Layering violations become compile errors. Features stay packages, per §3's warning against over-modularizing. |
| D-002 | **Compose-native playback on both surfaces** | `media3-ui-compose` 1.10.1 supplies `PlayerSurface` + state holders. Keeps "Compose for ALL UI" literally true — zero `AndroidView` in the app. |
| D-003 | **One shared sealed `AppError`** in `:domain`, not six per-screen error types | Satisfies §4's "sealed type, not a raw String" with one hierarchy instead of six near-identical ones. |
| D-004 | **Sign-out is a dialog, not a nav route** | Screen 07 is a confirmation dialog over Profile. Five nav keys + one dialog, not six routes. |
| D-005 | **Bottom bar** for Home / Shorts / Downloads / Profile; `Player` pushes over it | Matches YouTube-style intent; keeps the back stack shallow. |

### Verified artifact versions (checked against `dl.google.com` on 2026-07-27)

| Artifact | Version |
|---|---|
| `androidx.navigation3:navigation3-runtime` / `-ui` | **1.1.4** (stable) |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | **2.11.0** (stable) |
| `androidx.media3:*` (exoplayer, -hls, ui-compose, datasource) | **1.10.1** (stable) |

> ⚠️ **Nav3 and `media3-ui-compose` post-date the agent's training data.** Every phase that touches them carries an explicit *verify-API-against-docs* task. Do not infer Nav3 from Nav2 patterns.

### Project facts

- `minSdk = 25`, `targetSdk = 36`, `compileSdk = 36.1` — downloads therefore need **`POST_NOTIFICATIONS`** (API 33+) **and** `foregroundServiceType="dataSync"` + `FOREGROUND_SERVICE_DATA_SYNC` (API 34+).
- AI-workflow evidence (5%) is **already banked**: four symlinks at git mode `120000`, `commit.template` wired to `.gitmessage`.

---

## Phase overview

| Phase | Focus | Est. | Blocks | State |
|---|---|---|---|---|
| 0 | Module skeleton, catalog, docs | 2.0h | everything | ✅ on `master` |
| 1 | Domain + data layer | 2.0h | 3–7 | ✅ on `master` |
| 2 | Design system + Nav3 shell | 2.0h | 3–7 | ✅ on `master` — 2.7 now verified, see below |
| 3 | Home feed | 1.5h | 4 | ✅ on `master`, some device checks open |
| 4 | **Player** (Media3 ⅓) | 3.0h | 6 | ✅ on `master`, **device-verified** |
| 5 | **Shorts** (Media3 ⅔) | 3.0h | — | 🟡 Task 1 on `master`; Tasks 2–4 not started — now unblocked |
| 6 | **Downloads + offline** (Media3 ³⁄₃) | 4.0h | — | 🟡 Task 1 on `master`; Tasks 2–6 not started — now unblocked |
| 7 | Onboarding, session, Profile, sign-out | 2.0h | — | 🟡 Tasks 1–2 on `master`; Tasks 3–5 need 5+6 |
| 8 | Polish, tests, deliverables | 2.5h | — | ⬜ not started — run last |
| | **Total** | **22.0h** | | |

---

## Current state — resume here

*Updated 2026-07-27, after the Player device-verification pass and the branch merge.
The per-task detail lives in each plan under `docs/superpowers/plans/`.*

### Everything is on `master`

All six working branches were merged. `master` builds, `assembleDebug` passes, and the
suite is green: **103 tests, 0 failures** — 8 domain, 20 data, 17 designsystem,
19 core:player, 39 app. Branches are kept, not deleted.

`Onboarding`, `Home`, `Player` and `Profile` are real routes. Only `Shorts` and `Downloads`
remain `PlaceholderScreen`, which is why `ui/placeholder/Placeholders.kt` still exists.

### The Phase 4 blocker is cleared

Player was device-verified on an emulator (API 33). Critically, **Step 4a passed**:
`PlayerViewModel.onCleared()` fires when the Player entry is popped, releasing exactly one
player. That was the check Phase 2.7 deferred and nothing had performed since — so D-007
holds and every lifecycle guarantee resting on the Nav3 ViewModel decorator is real rather
than assumed.

That pass also found a crash no unit test could reach: `android.permission.INTERNET` was
never declared, so Coil killed the process on the Home feed's first thumbnail. Fixed and
merged. The lesson generalises — the whole suite passes with no network at all, because
the catalog is served in-process by MockEngine.

### What is actually left

**Build work, all unblocked:**
1. ~~Shorts Tasks 2–4~~ — **done** on `feat/shorts-pager`, unmerged. `Shorts` is a real
   route; only `Downloads` is still a `PlaceholderScreen`. Smoke-verified on the emulator:
   two slots only, pool released on tab switch, settled page survives rotation, no crash.
   The audio-bleed check is still outstanding and is the one that grades the feature.
2. Downloads Tasks 2–6 — the download stack on top of the existing `DownloadStatusMapper`.
3. Phase 7 Tasks 3–5 — architecture audit, docs, ship. Needs 5 and 6 done first.
4. Phase 8 — design pass. Run last.

**Risks worth retiring early (both from the register below):**
- **Download-ability of the catalog streams was never verified.** Task 0.7 required every
  `.m3u8` to play *and* download. Playback is now proven on device; downloading is not. The
  catalog is 16 streams on `test-streams.mux.dev` and 2 on `devstreaming-cdn.apple.com`. The
  register says to find this out now, not at Phase 6.
- **Download manifest plumbing.** `master` declares only `INTERNET`. Phase 6 adds
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` and
  `foregroundServiceType="dataSync"`. Note the available emulator is **API 33**, and the
  missing-FGS-type crash is an **API 34+** failure — that specific bug cannot be reproduced
  there. An API 34+ image is needed before Phase 6 is trustworthy.

**Device checks still outstanding:** Onboarding email sign-in and its error path; returning
user skips Onboarding (7.3); sign-out dialog returning to Onboarding; Home error state and
Retry; scroll preservation on rotation. Profile has never been opened on a device.

**Known defects, non-blocking:** the Player screen does not adapt to landscape (the 16:9
stage pushes everything else off-screen with no scroll); and one unexplained playback
position drop seen once and never reproduced.

### Open questions for the human

- **The design pass deletes work already built.** Plan 8's resolved-conflict #3 drops the
  email `TextField`, its validation, and two passing tests from Onboarding. Settle this
  before Phase 8 — and before spending device time verifying email sign-in, which that
  decision would make moot.
- **`Placeholders.kt` deletion** belongs to whichever plan replaces the last placeholder.

## Status — 2026-07-28

Phases 0–7 are complete and every feature in them is verified on an API 33 emulator; the
README's Status checklist is the authority on *what was verified how*, and this file is the
authority on *which plan tasks ran*. Phase 8 is complete except for two items, left
unticked deliberately:

- **8.1** — **done 2026-07-28.** Results, including two findings, are in the sweep table at
  the end of Phase 8.
- **8.9** — the demo recording. Not startable by an agent; the last graded deliverable.
- **8.10** — CI, marked optional in this plan and not attempted.

One audit check needs an asterisk. Check 8 in the ship plan greps the dependency graph for
`androidx.fragment:fragment` and expects nothing; it finds `1.5.1`, pulled in transitively by
`hilt-android`. There is no `Fragment` in any source file in this repo. The check is
over-broad — with Hilt on the classpath it can never be clean — and the constraint it stands
for ("no Fragments") does hold. The other ten checks pass as written.

Test count has moved with the work: the plans predicted 111, the ship audit measured 121, and
it now stands at **124**.

---

## Phase 0 — Module skeleton, catalog, docs

**Goal:** `./gradlew :app:compileDebugKotlin` passes with all five modules wired and every dependency declared.

- [x] **0.1** Add to `gradle/libs.versions.toml`: Hilt, KSP, Ktor (core, mock, content-negotiation, kotlinx-json), kotlinx-serialization, kotlinx-coroutines, Media3 (exoplayer, exoplayer-hls, ui-compose, datasource), Navigation3 (runtime, ui), lifecycle (viewmodel-compose, runtime-compose, viewmodel-navigation3), DataStore preferences, Coil, WindowSizeClass (material3-adaptive), Turbine, coroutines-test, LeakCanary (debug). **No version strings in any `build.gradle.kts`.**
- [x] **0.2** `settings.gradle.kts`: include `:domain`, `:data`, `:core:player`, `:core:designsystem`.
- [x] **0.3** `:domain/build.gradle.kts` — `kotlin("jvm")` **only**. No `android` plugin. Deps: coroutines-core, `javax.inject`. Nothing else.
- [x] **0.4** `:data`, `:core:player`, `:core:designsystem` — Android library modules, Hilt + KSP where needed.
- [x] **0.5** `:app/build.gradle.kts` — depend on all four; add Hilt, KSP, serialization plugins.
- [x] **0.6** `StreamlyApplication` with `@HiltAndroidApp`; register in manifest. `MainActivity` gets `@AndroidEntryPoint`.
- [x] **0.7** Author `:data/src/main/assets/catalog.json` — ≥12 videos, ≥6 shorts, categories, profile. **Verify every `.m3u8` URL actually plays *and* downloads before committing** (Mux `x36xhzz`, Mux `tos_ismc`, Apple `bipbop`).
- [x] **0.8** ⚠️ **Propose `AGENTS.md` amendment — needs sign-off, do not edit silently.** Two rules become false: "Single Gradle module `:app`" (→ five modules) and `catalog.json` at `app/src/main/assets/` (→ `:data/src/main/assets/`).
- [x] **0.9** Create `docs/decisions.md`; write **D-001** (module split) and **D-002** (Compose-native player surface). Append-only from here.
- [x] **0.10** Delete `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt` template stubs.

**Verify:** `./gradlew :app:compileDebugKotlin` green · `./gradlew :domain:dependencies` shows zero Android artifacts.
**Commit:** `chore(build): split into :app/:domain/:data/:core modules` · `docs(decisions): record D-001, D-002`

---

## Phase 1 — Domain + data layer

**Goal:** full seam `Ktor(MockEngine) → DTO → Mapper → domain → Repository → UseCase`, unit-tested, with no UI yet.

- [x] **1.1** `:domain` models: `Video`, `Short`, `Category`, `UserProfile`, `Session`, `DownloadItem`, `DownloadStatus`.
- [x] **1.2** `:domain` sealed `AppError`: `Network`, `NotFound`, `Storage`, `Unknown`.
- [x] **1.3** `:domain` repository **interfaces**: `CatalogRepository`, `SessionRepository`, `DownloadRepository`. All return `Result<T>` or `Flow<T>`.
- [x] **1.4** `:domain` use cases (`@Inject constructor`, `javax.inject` only): `GetHomeFeedUseCase`, `GetShortsUseCase`, `GetVideoDetailUseCase`, `GetRelatedVideosUseCase`, `ObserveSessionUseCase`, `SignOutUseCase`.
- [x] **1.5** `:data` DTOs — `@Serializable`, suffixed `Dto`. **Never leave `:data`.**
- [x] **1.6** `:data` mappers `Dto → domain`. Pure functions, no Android.
- [x] **1.7** `:data` `NetworkModule`: Ktor `HttpClient(MockEngine)` + `ContentNegotiation(Json)`. Engine reads `catalog.json` from assets via `@ApplicationContext`, adds 300–600 ms `delay`, and fails ~1-in-8 behind a debug flag so loading/error states are genuine.
- [x] **1.8** `:data` repository impls + `RepositoryModule` `@Binds` to domain interfaces.
- [x] **1.9** `:data` `SessionRepositoryImpl` on DataStore Preferences — write on sign-in, observe as `Flow<SessionState>` (`Unknown`/`SignedIn`/`SignedOut`), clear on sign-out.
- [x] **1.10** Unit test: DTO→domain mapper, including a malformed-JSON path.

**Verify:** `./gradlew :app:testDebugUnitTest` green · no Ktor or `Dto` type referenced outside `:data`.
**Commit:** `feat(domain): models, errors, repository contracts, use cases` · `feat(data): ktor mockengine catalog + datastore session`

---

## Phase 2 — Design system + Nav3 shell

**Goal:** app launches, bottom bar switches between six placeholder screens, session gating works, rotation loses nothing.

- [x] **2.1** ⚠️ **Verify Nav3 1.1.4 API against current docs before writing any nav code.** Confirm the real shapes of `NavBackStack` / `rememberNavBackStack`, `NavDisplay`, `entryProvider`, and the `lifecycle-viewmodel-navigation3` ViewModel-scoping entry point. Do not assume Nav2 idioms.
- [x] **2.2** `:core:designsystem`: move `Color.kt`/`Theme.kt`/`Type.kt` out of `:app`; add dark theme + dynamic color.
- [x] **2.3** `:core:designsystem`: `ContentState<T>` wrapper composable handling loading / empty / error(`AppError`) / content. **Every screen reuses this** — it's how §1's Polish line gets satisfied systematically.
- [x] **2.4** `:core:designsystem`: shared `VideoCard`, `CategoryChipRow`, `StreamlyScaffold`, error/empty illustrations.
- [x] **2.5** `:app/ui/navigation`: `@Serializable` keys `Onboarding`, `Home`, `Shorts`, `Player(videoId)`, `Downloads`, `Profile`.
- [x] **2.6** `NavDisplay` + `entryProvider` host in `MainActivity`; back stack via `rememberNavBackStack` so it survives rotation *and* process death.
- [x] **2.7** Wire ViewModel-per-`NavEntry` scoping. **This is load-bearing:** popping `Player` must dispose the entry → `onCleared()` → `release()`. Verify the scoping works here, before Phase 4 depends on it.
- [x] **2.8** Bottom bar for Home/Shorts/Downloads/Profile. The bar **stays visible on `Shorts`** (it's a top-level destination — hiding it would strand the user on a full-screen pager); it is hidden only on `Player`, which pushes over the bar.
- [x] **2.9** Session gating: hold the splash while `SessionState.Unknown`, then seed the stack to `Home` or `Onboarding`. Sign-out **clears** to `[Onboarding]`, never pushes.
- [x] **2.10** Plumb `WindowSizeClass` from the activity down; no hardcoded widths anywhere.
- [x] **2.11** Six placeholder screens, each already using `ContentState`.

**Verify:** compiles · app launches to correct start destination · rotation preserves the back stack · bottom bar switches screens.
**Needs device verification:** rotation, process-death restore.
**Commit:** `feat(designsystem): theme, ContentState, shared composables` · `feat(nav): Nav3 back stack, entry-scoped ViewModels, session gating`

---

## Phase 3 — Home feed

**Goal:** the first real vertical slice, proving the MVI contract end to end.

- [x] **3.1** `HomeContract.kt`: `HomeUiState` (immutable, `isLoading` / `videos` / `categories` / `selectedCategory` / `error: AppError?`), sealed `HomeIntent` (`Refresh`, `VideoClicked`, `CategorySelected`), sealed `HomeEffect` (`OpenPlayer(id)`).
- [x] **3.2** `HomeViewModel`: exposes **exactly** `val state: StateFlow<HomeUiState>` and `fun onIntent(HomeIntent)`. Effects via `Channel(BUFFERED).receiveAsFlow()`. No `MutableStateFlow` escapes.
- [x] **3.3** Stateless `HomeScreen(state, onIntent)` — `collectAsStateWithLifecycle()`, `LazyColumn` with `key = { it.id }`, Coil thumbnails, `@Immutable` UI models.
- [x] **3.4** Video cards: thumbnail, title, channel, view count, relative age, duration badge.
- [x] **3.5** Category chip row (may be non-functional per §9, but wire `CategorySelected` anyway — it's nearly free).
- [x] **3.6** Effect collection at the `NavDisplay` host → push `Player(videoId)`. **No nav lambdas inside the ViewModel.**
- [x] **3.7** Unit test: `Refresh` → loading → content, and → error; `CategorySelected` filters.

**Verify:** `testDebugUnitTest` green · loading, empty, and error all reachable · navigating away and back does **not** refetch.
**Commit:** `feat(home): MVI feed with loading/empty/error states`

---

## Phase 4 — Player  *(Media3 ⅓ — 30% block begins)*

**Goal:** long-form HLS playback that survives rotation and never leaks.

- [x] **4.1** ⚠️ **Verify `media3-ui-compose` 1.10.1 API against current docs** — confirm real names/signatures for `PlayerSurface`, `rememberPresentationState`, `rememberPlayPauseButtonState` before building controls on them.
- [x] **4.2** `:core:player` `PlayerModule`: the `@Singleton` `SimpleCache` (+ `StandaloneDatabaseProvider`, `NoOpCacheEvictor`), `DefaultDataSource.Factory`, `CacheDataSource.Factory` over that cache, and `HlsMediaSource.Factory`. **The cache singleton is created here, in Phase 4, and Phase 6 binds its `DownloadManager` to the same instance** — one cache, two consumers. Getting this ordering wrong is what makes offline playback silently miss.
- [x] **4.3** `PlayerViewModel` **owns** the `ExoPlayer`. Built in the VM, never `remember`ed in a composable. Exactly one `release()`, in `onCleared()`.
- [x] **4.4** `PlayerContract`: state carries `isBuffering`, `isPlaying`, `positionMs`, `durationMs`, `isMuted`, `video`, `related`, `downloadState`, `error`.
- [x] **4.5** Player listener → `StateFlow` (buffering, playing, position ticker, `onPlayerError` → `AppError`).
- [x] **4.6** Compose-native surface: `PlayerSurface` in a `16:9` `AspectRatio` box + custom controls — play/pause, scrubber, mute, **visible buffering indicator** (§5.1 requires all four).
- [x] **4.7** `LifecycleStartEffect`: **pause on `onStop`, resume on return, release on screen exit.** All three are named verbatim in the PRD.
- [x] **4.8** Rotation safety: position and play state survive; the player is **not** recreated (VM-owned, so this follows from Phase 2.7).
- [x] **4.9** Metadata block + related/"up next" list below the player. Tapping an item **replaces the top back-stack key** (`Player(newId)` swapped in, not pushed) and retargets the *same* `ExoPlayer` via `setMediaItem`. Replacing rather than pushing keeps the route key honest about what's on screen, keeps Back returning to Home instead of walking a chain of players, and preserves the single-shared-player requirement.
- [x] **4.10** **Download** action button, wired to the Phase 6 repository interface (stubbed until then).
- [x] **4.11** Unit test: intent → state transitions with a fake player abstraction.

**Verify:** compiles · tests green · buffering indicator visibly appears.
**Needs device verification:** rotation keeps position · background pauses / return resumes · exit releases (LeakCanary clean) · Home→Player→back repeatedly leaks nothing and the feed does not reload.
**Commit:** `feat(player): VM-owned ExoPlayer with lifecycle-correct HLS playback`

---

## Phase 5 — Shorts  *(Media3 ⅔)*

**Goal:** vertical pager where at most 1–2 players decode and audio never bleeds.

- [x] **5.1** `ShortsPlayerPool` in `:core:player`: 2–3 `ExoPlayer` instances, `acquire(index)` / `release(index)` / `releaseAll()`. Logs acquisitions so the ≤2-alive claim is demonstrable.
- [x] **5.2** Pool policy: the **settled** page plays; **at most one** neighbour is `prepare()`d and paused; the outgoing player is retargeted to the next-adjacent item. Never more than two decoding.
- [x] **5.3** `VerticalPager`, one short per page, `key = { it.id }`, `beyondViewportPageCount` pinned explicitly — never left at the default, which silently instantiates extra players.
- [x] **5.4** Playback keyed on **`pagerState.settledPage`**, not `currentPage`, so half-swipes don't start audio.
- [x] **5.5** `ShortsContract` + `ShortsViewModel`; the pool is released in `onCleared()`.
- [x] **5.6** Kill audio bleed on screen exit: `LifecycleStartEffect` pauses all; leaving the entry releases the pool. Cross-check against the Phase 4 player — **Home and Shorts must never both be audible.**
- [x] **5.7** Full-bleed centre-cropped surface + overlay chrome (title, channel, like/comment/share as stubs per §9).
- [x] **5.8** Deliberate, documented mute/position policy: shorts restart at 0 and play unmuted. Be consistent.

**Verify:** compiles · pool logs show ≤2 players alive.
**Needs device verification:** swipe fast through 10 items → no audio overlap, ≤2 players · Shorts→Home → silence.
**Commit:** `feat(shorts): pooled pager playback keyed on settledPage`

---

## Phase 6 — Downloads + offline  *(Media3 ³⁄₃ — the demo-critical phase)*

**Goal:** a real download, with real progress, that genuinely plays in airplane mode.

- [x] **6.1** `:core:player` download stack: `DownloadManager` with a bounded executor, bound to the **same `SimpleCache` singleton created in task 4.2** — do not construct a second cache. (`NoOpCacheEvictor` is already set there: download caches must never evict.)
- [x] **6.2** `StreamlyDownloadService : DownloadService` + notification channel + `DownloadNotificationHelper`.
- [x] **6.3** **Manifest plumbing — easiest thing to get wrong, fatal to the demo:** declare the service, `android:foregroundServiceType="dataSync"`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions, and `POST_NOTIFICATIONS`.
- [x] **6.4** Runtime `POST_NOTIFICATIONS` request on API 33+ (minSdk is 25, so the code path must be version-guarded).
- [x] **6.5** `DownloadHelper` → `DownloadRequest` → `DownloadService.sendAddDownload(...)`, so the HLS playlist **and** its segments/tracks are captured.
- [x] **6.6** `DownloadManager.Listener` → `callbackFlow` → `Flow<List<DownloadItem>>` with **real** `percentDownloaded`. No fake progress.
- [x] **6.7** `DownloadRepositoryImpl` implementing the `:domain` interface; map Media3 `Download.STATE_*` → domain `DownloadStatus`.
- [x] **6.8** Downloads screen: in-progress with live progress, completed "Ready to play", storage-used header, remove via `sendRemoveDownload`.
- [x] **6.9** **Offline playback:** completed items play through `CacheDataSource.Factory` on the *same* cache, with `setCacheWriteDataSinkFactory(null)` for read-only. This is the single most demo-critical line in the app.
- [x] **6.10** Wire the Phase 4.10 Download button to the real repository.
- [x] **6.11** Unit test: the download-state mapper — every `STATE_*` including `FAILED` and `REMOVING`. (§4 names this test explicitly.)

**Verify:** tests green · progress values move monotonically and are real.
**Needs device verification:** ▶ download → watch real progress → **airplane mode** → plays offline → remove works. *This sequence is the 30% category.*
**Commit:** `feat(downloads): Media3 offline downloads with real progress` · `feat(downloads): offline playback via CacheDataSource`

---

## Phase 7 — Onboarding, session, Profile, sign-out

**Goal:** the remaining three screens; session round-trip closed.

- [x] **7.1** Onboarding: "Continue with Google" (mocked), "Sign in with email", "Continue as guest". Each writes a session and navigates to `Home`.
- [x] **7.2** `OnboardingContract` + VM; local field state in `rememberSaveable`, screen state in the VM.
- [x] **7.3** Returning users skip Onboarding entirely (Phase 2.9 gating — verify end to end here).
- [x] **7.4** Profile: avatar, name, email; shallow links to Downloads / History / Settings; sign-out entry.
- [x] **7.5** Sign-out **confirmation dialog** (screen 07); confirm → `SignOutUseCase` → DataStore cleared → back stack **cleared** to `[Onboarding]`.
- [x] **7.6** Unit test: sign-out clears the session and emits the reset effect.

**Verify:** tests green · sign-out → relaunch lands on Onboarding · sign-in → relaunch lands on Home.
**Commit:** `feat(onboarding): session persistence and guest entry` · `feat(profile): profile screen with sign-out confirmation`

---

## Phase 8 — Polish, tests, deliverables

**Goal:** ship. Deliverables are graded; a perfect app that isn't demoed scores nothing.

- [x] **8.1** Sweep all seven screens for loading / empty / error via `ContentState`. Force each state and confirm it renders. **Done 2026-07-28 on the API 33 emulator** — see the sweep results below.
- [x] **8.2** Architecture audit: no `data` imports in `ui`, no `MutableStateFlow` exposed, no Ktor/DTO types outside `:data`, no `AndroidView`, no Nav2/RxJava/Retrofit/XML layouts anywhere in the dependency tree.
- [x] **8.3** Fill in `docs/decisions.md` for anything Phases 3–7 changed structurally.
- [x] **8.4** Update `docs/agent-log.md` — it's still an empty template, and §10 counts it as workflow evidence.
- [x] **8.5** README: what/why, architecture diagram, module map, how to run, test instructions, AI workflow, **shortcuts and why** (landscape HLS reused for Shorts; mocked auth; static category chips). §11 invites this — honest tradeoffs read as senior. Status checklist ticks **only** what genuinely works.
- [x] **8.6** Run the full §13 pre-submission checklist top to bottom.
- [x] **8.7** Clean-clone build check: `git clone` to a fresh directory → `./gradlew assembleDebug`.
- [x] **8.8** Build the debug APK; attach to a GitHub release or document the build steps.
### Task 8.1 — `ContentState` sweep results (2026-07-28)

Five of the seven screens use `ContentState`. Onboarding does not — it loads nothing remote,
so it has no state to force — and the sign-out dialog is a confirmation over Profile rather
than a data-bearing screen. Each state was forced with a throwaway build, captured, and the
sources restored afterwards (`git status` clean before the real APK was rebuilt):

- **loading** — `latencyMillis` raised to ~4 s in `mockCatalogEngine`.
- **error** — `failEveryNth = 1` for Home/Shorts/Profile; `= 2` for the Player, so Home
  succeeds on request 1 and the Player's detail fetch fails on request 2.
- **empty** — `videos` and `shorts` emptied in `catalog.json`; Downloads reached by removing
  the one download on the real build.

| Screen | Loading | Empty | Error |
|---|---|---|---|
| Home | ✅ spinner | ✅ "Nothing here yet" (chips still shown) | ✅ "No connection" + Retry |
| Shorts | ✅ spinner | ✅ "Nothing here yet" | ✅ "No connection" + Retry |
| Player | ✅ spinner | n/a — no `isEmpty` predicate | ✅ "No connection" + Retry |
| Downloads | ⚠️ not observable | ✅ "Nothing here yet", header reads `0 B used` | n/a — field dropped, D-025 |
| Profile | ✅ spinner | n/a — no `isEmpty` predicate | ✅ "No connection" + Retry |

**Two findings. The first was acted on.**

`DownloadsUiState.error` was declared, rendered by `ContentState`, and **never set** — the
flow assigned `error = null` unconditionally. A failing download surfaces as a row status
rather than a screen error, which is the right design, so the field was **dropped** rather
than wired up. `ContentState`'s `error` parameter now defaults to `null` for screens that
cannot fail. Recorded as D-025, because it deviates from the MVI rule in `AGENTS.md`.

Downloads' loading state is real but not observable: its data comes from `DownloadManager` on
the device, not through the MockEngine, so there is no latency to catch it in. Nothing to fix
— worth knowing before someone tries to screenshot it.

**Player and Profile pass no `isEmpty` predicate**, so their empty branch defaults to false
and cannot render. That is correct: a video detail or a profile that loaded successfully is
never meaningfully "empty". Recorded so the gap reads as a decision rather than an oversight.

### Task 8.1 — re-run on a physical device (2026-07-29)

The sweep above ran on the API 33 emulator. It was re-run in full on a physical Pixel 6 Pro
(Android 17 / API 37) after the D-027 navigation-padding fix, since that fix changed how
every top-level screen is measured and these states are exactly what a bad reservation would
break. Same three throwaway builds, same knobs, sources restored and `git status` clean
before the real APK went back on.

| Screen | Loading | Empty | Error |
|---|---|---|---|
| Home | ✅ spinner, app bar kept | ✅ "Nothing here yet", chips still shown | ✅ "No connection" + Retry |
| Shorts | ✅ spinner | ✅ "Nothing here yet" | ✅ "No connection" + Retry |
| Player | ✅ spinner, full height, no bar | n/a — no `isEmpty` predicate | ✅ "No connection" + Retry |
| Downloads | ⚠️ not observable, as before | ✅ reached for real by removing the download, header `0 B used` | n/a — field dropped, D-025 |
| Profile | ✅ spinner | n/a — no `isEmpty` predicate | ✅ "No connection" + Retry |

Two things this run establishes that the emulator run did not:

- **Retry was proven to recover, not just to render.** With `failEveryNth = 2` the Player's
  detail fetch fails on request 2; tapping Retry issued request 3, which succeeded, and the
  screen went from "No connection" to a playing video. The earlier sweep only recorded that
  the button appeared and was tappable on Home.
- **Downloads' empty state was reached without editing anything** — it is what Remove leaves
  behind, confirmed in the same session as D-028's download run.

Every state still renders correctly above the bottom bar after D-027, which is the
regression this re-run was really looking for. No behaviour differed from the API 33 results.

- [ ] **8.9** Record the 2–4 min demo covering all seven screens, **ending with** download → real progress → airplane mode → offline playback.
- [ ] **8.10** *(optional, only if time survives)* GitHub Actions `build + test`.

**Verify:** clean clone builds · demo recorded · APK attached · README complete.
**Commit:** `docs: README, decisions, agent log` · `chore: debug APK for review`

---

## Standing rules for every phase

- Conventional commits; `Co-authored-by: Claude <noreply@anthropic.com>` comes from the template automatically.
- **Doc updates ship in the same commit as the code they describe** — never a follow-up "update docs" commit.
- Structural change ⇒ a `docs/decisions.md` entry in that same commit. Append-only; reverse by superseding, never by editing.
- `./gradlew :app:compileDebugKotlin` while iterating; `assembleDebug` only before committing. Never `clean`.
- Never edit `AGENTS.md` (or its symlinks) without sign-off.
- Anything requiring a device — rotation, leaks, audio bleed, offline playback — is reported as **"needs verification"**, never as done.

## Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Nav3 / `media3-ui-compose` APIs post-date training data | Wrong code written confidently | Explicit doc-verification tasks at 2.1 and 4.1, **before** dependent code |
| Download manifest plumbing (FGS type, notif permission) | Silent failure → demo dies | Isolated as task 6.3, called out as fatal |
| Public HLS streams may not download cleanly | Phase 6 unshippable | Verify download-ability in task 0.7, not at Phase 6 |
| No emulator in this environment | All device checks are manual + serial | Every phase lists its device checks explicitly so they can be batched |
| 22h estimate vs ~16–20h available | Overrun | Phases 4–6 are protected; 8.10, Profile depth, and visual polish are the compressible items |
