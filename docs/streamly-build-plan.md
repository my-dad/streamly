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
| 2 | Design system + Nav3 shell | 2.0h | 3–7 | ✅ on `master` — **except 2.7**, see below |
| 3 | Home feed | 1.5h | 4 | ✅ on `master`, device checks open |
| 4 | **Player** (Media3 ⅓) | 3.0h | 6 | 🟡 built on `feat/player`, **device checks open — the blocker** |
| 5 | **Shorts** (Media3 ⅔) | 3.0h | — | 🟡 Task 1 on `feat/shorts-pool-policy`; Tasks 2–4 blocked on 4 |
| 6 | **Downloads + offline** (Media3 ³⁄₃) | 4.0h | — | 🟡 Task 1 on `feat/download-status-mapper`; Tasks 2–6 blocked on 4 |
| 7 | Onboarding, session, Profile, sign-out | 2.0h | — | 🟡 Tasks 1–2 on `feat/onboarding-profile`; Tasks 3–5 blocked on 5+6 |
| 8 | Polish, tests, deliverables | 2.5h | — | ⬜ not started — run last |
| | **Total** | **22.0h** | | |

---

## Current state — resume here

*Updated 2026-07-27. The per-task detail lives in each plan under
`docs/superpowers/plans/`; every plan has a "where this stands" section. This is the map.*

### The one blocker

**Phase 4's device verification.** `feat/player` is complete and green but unmerged, and
everything in Phases 5–7 that needs `PlayerModule` waits behind it.

The critical check is the Player plan's **Task 5 Step 4a**: confirm `PlayerViewModel.onCleared()`
actually fires when the Player entry is popped. This is the first real test that
`rememberViewModelStoreNavEntryDecorator()` scopes ViewModels to their `NavEntry` — the
check **Phase 2.7 deferred and nothing has performed since**. If it fails, the fix is in
`StreamlyApp.kt` / D-007, and Shorts' pool release depends on the same mechanism. Exact
steps, including the temporary log lines to add and revert, are in the Player plan.

### Branch map

| Branch | Contains | Commits | State |
|---|---|---|---|
| `master` | Phases 0–3 | — | integration branch |
| `feat/player` | Phase 4 | 9 | green; awaiting device checks |
| `feat/onboarding-profile` | Phase 7 Tasks 1–2 | 4 | green; **conflicts with `feat/player`**, see below |
| `feat/shorts-pool-policy` | Phase 5 Task 1 | 2 | green; merges clean |
| `feat/download-status-mapper` | Phase 6 Task 1 | 2 | green; merges clean |
| `docs/readme-architecture` | README | 1 | docs only; merges clean |

`feat/home-feed` and `docs/home-feed-plan-progress` are already merged into `master` and can
be deleted.

A trial merge of all five open branches was run and then discarded. Four merge clean in any
order. `feat/onboarding-profile` conflicts with `feat/player` in `StreamlyApp.kt` — both edit
the import block and the `entryProvider` body, which is structural, since every feature
branch registers its screen in the one nav host. Resolution: keep all three imports and all
three real entries, leaving only Shorts and Downloads as `PlaceholderScreen`.

**Integrated suite is green: 92 tests, 0 failures** — 8 domain, 20 data, 17 designsystem,
19 core:player, 28 app.

### Suggested order when the blocker clears

1. Run the Phase 4 device checks → merge `feat/player`, delete the branch.
2. Merge `feat/shorts-pool-policy` and `feat/download-status-mapper` (clean, any order).
3. Merge `feat/onboarding-profile`, resolving the `StreamlyApp.kt` conflict as above.
4. Merge `docs/readme-architecture`.
5. Shorts Tasks 2–4, then Downloads Tasks 2–6 — both now unblocked.
6. Phase 7 Tasks 3–5 (audit, docs, ship), then Phase 8.

### Open questions for the human

- **The design pass deletes work already built.** Plan 8's resolved-conflict #3 drops the
  email `TextField` and its validation from Onboarding, plus two passing tests, in favour of
  the design's direct sign-in. That is a deliberate design call, not an oversight — but it
  should be settled before Phase 8 rather than during it.
- **`Placeholders.kt` deletion is deferred.** Plan 7 Task 2 says to delete it because "every
  placeholder is now gone"; that only becomes true once Shorts and Downloads land. Whichever
  plan replaces the last placeholder should delete it.
- **Nothing has been verified on a device yet**, across any phase. The README status list is
  deliberately all-unticked for that reason.

Phases 4–6 are the 30% rubric block. PRD §1: *if the clock breaks, cut visual polish and Profile depth — never these.*

---

## Phase 0 — Module skeleton, catalog, docs

**Goal:** `./gradlew :app:compileDebugKotlin` passes with all five modules wired and every dependency declared.

- [ ] **0.1** Add to `gradle/libs.versions.toml`: Hilt, KSP, Ktor (core, mock, content-negotiation, kotlinx-json), kotlinx-serialization, kotlinx-coroutines, Media3 (exoplayer, exoplayer-hls, ui-compose, datasource), Navigation3 (runtime, ui), lifecycle (viewmodel-compose, runtime-compose, viewmodel-navigation3), DataStore preferences, Coil, WindowSizeClass (material3-adaptive), Turbine, coroutines-test, LeakCanary (debug). **No version strings in any `build.gradle.kts`.**
- [ ] **0.2** `settings.gradle.kts`: include `:domain`, `:data`, `:core:player`, `:core:designsystem`.
- [ ] **0.3** `:domain/build.gradle.kts` — `kotlin("jvm")` **only**. No `android` plugin. Deps: coroutines-core, `javax.inject`. Nothing else.
- [ ] **0.4** `:data`, `:core:player`, `:core:designsystem` — Android library modules, Hilt + KSP where needed.
- [ ] **0.5** `:app/build.gradle.kts` — depend on all four; add Hilt, KSP, serialization plugins.
- [ ] **0.6** `StreamlyApplication` with `@HiltAndroidApp`; register in manifest. `MainActivity` gets `@AndroidEntryPoint`.
- [ ] **0.7** Author `:data/src/main/assets/catalog.json` — ≥12 videos, ≥6 shorts, categories, profile. **Verify every `.m3u8` URL actually plays *and* downloads before committing** (Mux `x36xhzz`, Mux `tos_ismc`, Apple `bipbop`).
- [ ] **0.8** ⚠️ **Propose `AGENTS.md` amendment — needs sign-off, do not edit silently.** Two rules become false: "Single Gradle module `:app`" (→ five modules) and `catalog.json` at `app/src/main/assets/` (→ `:data/src/main/assets/`).
- [ ] **0.9** Create `docs/decisions.md`; write **D-001** (module split) and **D-002** (Compose-native player surface). Append-only from here.
- [ ] **0.10** Delete `ExampleUnitTest.kt` / `ExampleInstrumentedTest.kt` template stubs.

**Verify:** `./gradlew :app:compileDebugKotlin` green · `./gradlew :domain:dependencies` shows zero Android artifacts.
**Commit:** `chore(build): split into :app/:domain/:data/:core modules` · `docs(decisions): record D-001, D-002`

---

## Phase 1 — Domain + data layer

**Goal:** full seam `Ktor(MockEngine) → DTO → Mapper → domain → Repository → UseCase`, unit-tested, with no UI yet.

- [ ] **1.1** `:domain` models: `Video`, `Short`, `Category`, `UserProfile`, `Session`, `DownloadItem`, `DownloadStatus`.
- [ ] **1.2** `:domain` sealed `AppError`: `Network`, `NotFound`, `Storage`, `Unknown`.
- [ ] **1.3** `:domain` repository **interfaces**: `CatalogRepository`, `SessionRepository`, `DownloadRepository`. All return `Result<T>` or `Flow<T>`.
- [ ] **1.4** `:domain` use cases (`@Inject constructor`, `javax.inject` only): `GetHomeFeedUseCase`, `GetShortsUseCase`, `GetVideoDetailUseCase`, `GetRelatedVideosUseCase`, `ObserveSessionUseCase`, `SignOutUseCase`.
- [ ] **1.5** `:data` DTOs — `@Serializable`, suffixed `Dto`. **Never leave `:data`.**
- [ ] **1.6** `:data` mappers `Dto → domain`. Pure functions, no Android.
- [ ] **1.7** `:data` `NetworkModule`: Ktor `HttpClient(MockEngine)` + `ContentNegotiation(Json)`. Engine reads `catalog.json` from assets via `@ApplicationContext`, adds 300–600 ms `delay`, and fails ~1-in-8 behind a debug flag so loading/error states are genuine.
- [ ] **1.8** `:data` repository impls + `RepositoryModule` `@Binds` to domain interfaces.
- [ ] **1.9** `:data` `SessionRepositoryImpl` on DataStore Preferences — write on sign-in, observe as `Flow<SessionState>` (`Unknown`/`SignedIn`/`SignedOut`), clear on sign-out.
- [ ] **1.10** Unit test: DTO→domain mapper, including a malformed-JSON path.

**Verify:** `./gradlew :app:testDebugUnitTest` green · no Ktor or `Dto` type referenced outside `:data`.
**Commit:** `feat(domain): models, errors, repository contracts, use cases` · `feat(data): ktor mockengine catalog + datastore session`

---

## Phase 2 — Design system + Nav3 shell

**Goal:** app launches, bottom bar switches between six placeholder screens, session gating works, rotation loses nothing.

- [ ] **2.1** ⚠️ **Verify Nav3 1.1.4 API against current docs before writing any nav code.** Confirm the real shapes of `NavBackStack` / `rememberNavBackStack`, `NavDisplay`, `entryProvider`, and the `lifecycle-viewmodel-navigation3` ViewModel-scoping entry point. Do not assume Nav2 idioms.
- [ ] **2.2** `:core:designsystem`: move `Color.kt`/`Theme.kt`/`Type.kt` out of `:app`; add dark theme + dynamic color.
- [ ] **2.3** `:core:designsystem`: `ContentState<T>` wrapper composable handling loading / empty / error(`AppError`) / content. **Every screen reuses this** — it's how §1's Polish line gets satisfied systematically.
- [ ] **2.4** `:core:designsystem`: shared `VideoCard`, `CategoryChipRow`, `StreamlyScaffold`, error/empty illustrations.
- [ ] **2.5** `:app/ui/navigation`: `@Serializable` keys `Onboarding`, `Home`, `Shorts`, `Player(videoId)`, `Downloads`, `Profile`.
- [ ] **2.6** `NavDisplay` + `entryProvider` host in `MainActivity`; back stack via `rememberNavBackStack` so it survives rotation *and* process death.
- [ ] **2.7** Wire ViewModel-per-`NavEntry` scoping. **This is load-bearing:** popping `Player` must dispose the entry → `onCleared()` → `release()`. Verify the scoping works here, before Phase 4 depends on it.
- [ ] **2.8** Bottom bar for Home/Shorts/Downloads/Profile. The bar **stays visible on `Shorts`** (it's a top-level destination — hiding it would strand the user on a full-screen pager); it is hidden only on `Player`, which pushes over the bar.
- [ ] **2.9** Session gating: hold the splash while `SessionState.Unknown`, then seed the stack to `Home` or `Onboarding`. Sign-out **clears** to `[Onboarding]`, never pushes.
- [ ] **2.10** Plumb `WindowSizeClass` from the activity down; no hardcoded widths anywhere.
- [ ] **2.11** Six placeholder screens, each already using `ContentState`.

**Verify:** compiles · app launches to correct start destination · rotation preserves the back stack · bottom bar switches screens.
**Needs device verification:** rotation, process-death restore.
**Commit:** `feat(designsystem): theme, ContentState, shared composables` · `feat(nav): Nav3 back stack, entry-scoped ViewModels, session gating`

---

## Phase 3 — Home feed

**Goal:** the first real vertical slice, proving the MVI contract end to end.

- [ ] **3.1** `HomeContract.kt`: `HomeUiState` (immutable, `isLoading` / `videos` / `categories` / `selectedCategory` / `error: AppError?`), sealed `HomeIntent` (`Refresh`, `VideoClicked`, `CategorySelected`), sealed `HomeEffect` (`OpenPlayer(id)`).
- [ ] **3.2** `HomeViewModel`: exposes **exactly** `val state: StateFlow<HomeUiState>` and `fun onIntent(HomeIntent)`. Effects via `Channel(BUFFERED).receiveAsFlow()`. No `MutableStateFlow` escapes.
- [ ] **3.3** Stateless `HomeScreen(state, onIntent)` — `collectAsStateWithLifecycle()`, `LazyColumn` with `key = { it.id }`, Coil thumbnails, `@Immutable` UI models.
- [ ] **3.4** Video cards: thumbnail, title, channel, view count, relative age, duration badge.
- [ ] **3.5** Category chip row (may be non-functional per §9, but wire `CategorySelected` anyway — it's nearly free).
- [ ] **3.6** Effect collection at the `NavDisplay` host → push `Player(videoId)`. **No nav lambdas inside the ViewModel.**
- [ ] **3.7** Unit test: `Refresh` → loading → content, and → error; `CategorySelected` filters.

**Verify:** `testDebugUnitTest` green · loading, empty, and error all reachable · navigating away and back does **not** refetch.
**Commit:** `feat(home): MVI feed with loading/empty/error states`

---

## Phase 4 — Player  *(Media3 ⅓ — 30% block begins)*

**Goal:** long-form HLS playback that survives rotation and never leaks.

- [ ] **4.1** ⚠️ **Verify `media3-ui-compose` 1.10.1 API against current docs** — confirm real names/signatures for `PlayerSurface`, `rememberPresentationState`, `rememberPlayPauseButtonState` before building controls on them.
- [ ] **4.2** `:core:player` `PlayerModule`: the `@Singleton` `SimpleCache` (+ `StandaloneDatabaseProvider`, `NoOpCacheEvictor`), `DefaultDataSource.Factory`, `CacheDataSource.Factory` over that cache, and `HlsMediaSource.Factory`. **The cache singleton is created here, in Phase 4, and Phase 6 binds its `DownloadManager` to the same instance** — one cache, two consumers. Getting this ordering wrong is what makes offline playback silently miss.
- [ ] **4.3** `PlayerViewModel` **owns** the `ExoPlayer`. Built in the VM, never `remember`ed in a composable. Exactly one `release()`, in `onCleared()`.
- [ ] **4.4** `PlayerContract`: state carries `isBuffering`, `isPlaying`, `positionMs`, `durationMs`, `isMuted`, `video`, `related`, `downloadState`, `error`.
- [ ] **4.5** Player listener → `StateFlow` (buffering, playing, position ticker, `onPlayerError` → `AppError`).
- [ ] **4.6** Compose-native surface: `PlayerSurface` in a `16:9` `AspectRatio` box + custom controls — play/pause, scrubber, mute, **visible buffering indicator** (§5.1 requires all four).
- [ ] **4.7** `LifecycleStartEffect`: **pause on `onStop`, resume on return, release on screen exit.** All three are named verbatim in the PRD.
- [ ] **4.8** Rotation safety: position and play state survive; the player is **not** recreated (VM-owned, so this follows from Phase 2.7).
- [ ] **4.9** Metadata block + related/"up next" list below the player. Tapping an item **replaces the top back-stack key** (`Player(newId)` swapped in, not pushed) and retargets the *same* `ExoPlayer` via `setMediaItem`. Replacing rather than pushing keeps the route key honest about what's on screen, keeps Back returning to Home instead of walking a chain of players, and preserves the single-shared-player requirement.
- [ ] **4.10** **Download** action button, wired to the Phase 6 repository interface (stubbed until then).
- [ ] **4.11** Unit test: intent → state transitions with a fake player abstraction.

**Verify:** compiles · tests green · buffering indicator visibly appears.
**Needs device verification:** rotation keeps position · background pauses / return resumes · exit releases (LeakCanary clean) · Home→Player→back repeatedly leaks nothing and the feed does not reload.
**Commit:** `feat(player): VM-owned ExoPlayer with lifecycle-correct HLS playback`

---

## Phase 5 — Shorts  *(Media3 ⅔)*

**Goal:** vertical pager where at most 1–2 players decode and audio never bleeds.

- [ ] **5.1** `ShortsPlayerPool` in `:core:player`: 2–3 `ExoPlayer` instances, `acquire(index)` / `release(index)` / `releaseAll()`. Logs acquisitions so the ≤2-alive claim is demonstrable.
- [ ] **5.2** Pool policy: the **settled** page plays; **at most one** neighbour is `prepare()`d and paused; the outgoing player is retargeted to the next-adjacent item. Never more than two decoding.
- [ ] **5.3** `VerticalPager`, one short per page, `key = { it.id }`, `beyondViewportPageCount` pinned explicitly — never left at the default, which silently instantiates extra players.
- [ ] **5.4** Playback keyed on **`pagerState.settledPage`**, not `currentPage`, so half-swipes don't start audio.
- [ ] **5.5** `ShortsContract` + `ShortsViewModel`; the pool is released in `onCleared()`.
- [ ] **5.6** Kill audio bleed on screen exit: `LifecycleStartEffect` pauses all; leaving the entry releases the pool. Cross-check against the Phase 4 player — **Home and Shorts must never both be audible.**
- [ ] **5.7** Full-bleed centre-cropped surface + overlay chrome (title, channel, like/comment/share as stubs per §9).
- [ ] **5.8** Deliberate, documented mute/position policy: shorts restart at 0 and play unmuted. Be consistent.

**Verify:** compiles · pool logs show ≤2 players alive.
**Needs device verification:** swipe fast through 10 items → no audio overlap, ≤2 players · Shorts→Home → silence.
**Commit:** `feat(shorts): pooled pager playback keyed on settledPage`

---

## Phase 6 — Downloads + offline  *(Media3 ³⁄₃ — the demo-critical phase)*

**Goal:** a real download, with real progress, that genuinely plays in airplane mode.

- [ ] **6.1** `:core:player` download stack: `DownloadManager` with a bounded executor, bound to the **same `SimpleCache` singleton created in task 4.2** — do not construct a second cache. (`NoOpCacheEvictor` is already set there: download caches must never evict.)
- [ ] **6.2** `StreamlyDownloadService : DownloadService` + notification channel + `DownloadNotificationHelper`.
- [ ] **6.3** **Manifest plumbing — easiest thing to get wrong, fatal to the demo:** declare the service, `android:foregroundServiceType="dataSync"`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions, and `POST_NOTIFICATIONS`.
- [ ] **6.4** Runtime `POST_NOTIFICATIONS` request on API 33+ (minSdk is 25, so the code path must be version-guarded).
- [ ] **6.5** `DownloadHelper` → `DownloadRequest` → `DownloadService.sendAddDownload(...)`, so the HLS playlist **and** its segments/tracks are captured.
- [ ] **6.6** `DownloadManager.Listener` → `callbackFlow` → `Flow<List<DownloadItem>>` with **real** `percentDownloaded`. No fake progress.
- [ ] **6.7** `DownloadRepositoryImpl` implementing the `:domain` interface; map Media3 `Download.STATE_*` → domain `DownloadStatus`.
- [ ] **6.8** Downloads screen: in-progress with live progress, completed "Ready to play", storage-used header, remove via `sendRemoveDownload`.
- [ ] **6.9** **Offline playback:** completed items play through `CacheDataSource.Factory` on the *same* cache, with `setCacheWriteDataSinkFactory(null)` for read-only. This is the single most demo-critical line in the app.
- [ ] **6.10** Wire the Phase 4.10 Download button to the real repository.
- [ ] **6.11** Unit test: the download-state mapper — every `STATE_*` including `FAILED` and `REMOVING`. (§4 names this test explicitly.)

**Verify:** tests green · progress values move monotonically and are real.
**Needs device verification:** ▶ download → watch real progress → **airplane mode** → plays offline → remove works. *This sequence is the 30% category.*
**Commit:** `feat(downloads): Media3 offline downloads with real progress` · `feat(downloads): offline playback via CacheDataSource`

---

## Phase 7 — Onboarding, session, Profile, sign-out

**Goal:** the remaining three screens; session round-trip closed.

- [ ] **7.1** Onboarding: "Continue with Google" (mocked), "Sign in with email", "Continue as guest". Each writes a session and navigates to `Home`.
- [ ] **7.2** `OnboardingContract` + VM; local field state in `rememberSaveable`, screen state in the VM.
- [ ] **7.3** Returning users skip Onboarding entirely (Phase 2.9 gating — verify end to end here).
- [ ] **7.4** Profile: avatar, name, email; shallow links to Downloads / History / Settings; sign-out entry.
- [ ] **7.5** Sign-out **confirmation dialog** (screen 07); confirm → `SignOutUseCase` → DataStore cleared → back stack **cleared** to `[Onboarding]`.
- [ ] **7.6** Unit test: sign-out clears the session and emits the reset effect.

**Verify:** tests green · sign-out → relaunch lands on Onboarding · sign-in → relaunch lands on Home.
**Commit:** `feat(onboarding): session persistence and guest entry` · `feat(profile): profile screen with sign-out confirmation`

---

## Phase 8 — Polish, tests, deliverables

**Goal:** ship. Deliverables are graded; a perfect app that isn't demoed scores nothing.

- [ ] **8.1** Sweep all seven screens for loading / empty / error via `ContentState`. Force each state and confirm it renders.
- [ ] **8.2** Architecture audit: no `data` imports in `ui`, no `MutableStateFlow` exposed, no Ktor/DTO types outside `:data`, no `AndroidView`, no Nav2/RxJava/Retrofit/XML layouts anywhere in the dependency tree.
- [ ] **8.3** Fill in `docs/decisions.md` for anything Phases 3–7 changed structurally.
- [ ] **8.4** Update `docs/agent-log.md` — it's still an empty template, and §10 counts it as workflow evidence.
- [ ] **8.5** README: what/why, architecture diagram, module map, how to run, test instructions, AI workflow, **shortcuts and why** (landscape HLS reused for Shorts; mocked auth; static category chips). §11 invites this — honest tradeoffs read as senior. Status checklist ticks **only** what genuinely works.
- [ ] **8.6** Run the full §13 pre-submission checklist top to bottom.
- [ ] **8.7** Clean-clone build check: `git clone` to a fresh directory → `./gradlew assembleDebug`.
- [ ] **8.8** Build the debug APK; attach to a GitHub release or document the build steps.
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
