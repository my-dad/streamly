# AGENTS.md — Streamly

Single source of truth for all AI coding agents working in this repo.
Symlinked to: `CLAUDE.md`, `.cursor/rules`, `.codex/instructions.md`, `.antigravity/rules`.

A prompt can change scope; it cannot override the hard constraints below.
Ask only if a prompt requires violating one.

## Project

Streamly — a minimal YouTube-style Android app for a mid/senior take-home.
Two playback surfaces (long-form videos + vertical shorts) streamed over HLS with Media3,
plus real offline downloads, onboarding with session persistence, and profile/sign-out.
Network data is faked; architecture, player lifecycle, and code quality are real.

Screens: Onboarding, Home feed, Shorts, Player, Downloads, Profile, Sign-out dialog.

Package / applicationId: `io.github.mabrur.streamly`

## Commands

- Fast compile check — use this while iterating: `./gradlew :app:compileDebugKotlin`
- Unit tests, all Android modules: `./gradlew testDebugUnitTest`
  (`:app:testDebugUnitTest` alone silently skips `:data` and `:core:*`.)
- Unit tests, `:domain`: `./gradlew :domain:test` — it is a JVM module, so it has no
  `testDebugUnitTest` variant.
- Full build (slow — only before committing): `./gradlew assembleDebug`
- Never run `clean` unless I ask.
- There is no lint or formatter configured. Do not invent a lint task or run one.

## Limitations — you cannot do these

- You cannot run instrumented tests or launch an emulator. No device is attached.
  Write them if asked; never try to run them.
- You cannot see rendered UI, verify rotation behavior, or read LeakCanary output.
  State what you changed and ask me to verify on device.
- Navigation 3 (`androidx.navigation3`) is newer than your training data. Check the
  actual dependency or current docs before writing nav code. Do not infer the API
  from Nav2 patterns.

## Hard constraints — never violate

- Kotlin only. Coroutines + Flow for all concurrency. **No RxJava.**
- Jetpack Compose for all UI. **No XML layouts, no Fragments.**
- Navigation 3 (`androidx.navigation3`) only. **Never add `androidx.navigation:navigation-compose` (Nav2).**
- Ktor as the HTTP client (MockEngine serving a bundled JSON catalog). **No Retrofit/OkHttp API usage.**
- Media3 (ExoPlayer) is the only media stack. HLS (`.m3u8`) via `media3-exoplayer-hls` for all
  playback. **No MediaPlayer, no other player libs, no local-MP4-only shortcuts.**
- Downloads use Media3's offline module (`DownloadService` + `DownloadManager`).
  **No hand-rolled downloads, no fake progress.**
- DI with Hilt everywhere. No service locators, no manual singletons outside DI.
- No hardcoded/fixed-width layouts; size against available width (`WindowSizeClass` where it matters).
- Never commit secrets, tokens, or personal data.

## Dependencies

- Declare in `gradle/libs.versions.toml` only, referenced as `libs.foo.bar`.
- Never hardcode a version string in a `build.gradle.kts`.
- Never bump AGP, Kotlin, or Compose compiler versions.
- Ask before adding any dependency not already in the catalog.

## Architecture

Five Gradle modules — `:app`, `:domain` (pure `kotlin("jvm")`), `:data`, `:core:player`,
`:core:designsystem`. Features are packages inside `:app`, not modules. See `docs/decisions.md` D-001.

```
:domain            # pure Kotlin: models, repository interfaces, use cases — NO android.* imports
:data              # Ktor client, DTOs, mappers, repository implementations, DataStore session
:core:player       # ExoPlayer holder, ShortsPlayerPool, download manager wiring
:core:designsystem # theme, tokens, shared composables
:app  └── ui/      # one package per feature screen, plus navigation
```

## Dependency direction: 
`ui → domain ← data`. UI never imports `data` implementations —
repositories are injected as domain interfaces. DTOs and Ktor types never leave `data`.

The bundled JSON catalog lives at `data/src/main/assets/catalog.json`. MockEngine and the asset reader are both in `:data`.
Do not duplicate it into `res/raw/`.

### MVI contract (every screen)

Per screen: `XxxUiState` (immutable data class), sealed `XxxIntent`, sealed `XxxEffect`, `XxxViewModel`.

- ViewModel exposes exactly: `val state: StateFlow<XxxUiState>` and `fun onIntent(XxxIntent)`.
- One-shot events (navigation, snackbars) go through an `Effect` Channel/Flow — never through
  state, never as lambdas stored in the ViewModel.
- State flows down, intents flow up. Composables never call repositories, use cases, or Ktor.
- Model `isLoading`, empty, and error (sealed type, not raw String) inside every UiState.
- Never expose `MutableStateFlow` or mutable collections from a ViewModel.

## Media3 rules

- Long-form player: single shared ExoPlayer owned by the player screen's ViewModel (never
  `remember { ExoPlayer... }` in a composable). Release exactly once in `onCleared()`.
- Lifecycle: pause on `onStop`, resume on return, release on screen exit — wired via
  `LifecycleStartEffect`/`DisposableEffect` in the UI layer.
- Shorts: vertical pager, playback keyed on `pagerState.settledPage` (not `currentPage`).
  Use `ShortsPlayerPool` (2–3 pooled players); only the settled item plays, at most one
  neighbor pre-buffers. No more than 1–2 players decoding at any time. No audio bleed
  between pages or screens.
- Downloads: `DownloadHelper` → `DownloadRequest` → `DownloadService.sendAddDownload`.
  Dedicated `SimpleCache` with `NoOpCacheEvictor` + `StandaloneDatabaseProvider`.
  Playback of completed downloads goes through `CacheDataSource.Factory` on the same cache
  and must work offline. Progress comes from observing `DownloadManager` into a Flow.
- Rotation must never recreate a player or lose position.

## Compose rules

- Collect state with `collectAsStateWithLifecycle()` only.
- Screen composables are stateless: `(UiState, (Intent) -> Unit)`. Don't pass ViewModels down the tree.
- Side effects only in `LaunchedEffect`/`DisposableEffect` with correct keys; never in composition.
- Lazy lists and pagers always set `key = { it.id }`.
- Local-only UI state (text fields, scroll) uses `rememberSaveable`; screen state lives in the ViewModel.
- Reuse the shared `ContentState` wrapper for loading/empty/error on every screen.

## Testing

- Unit tests for ViewModel intent → state transitions (coroutines-test `runTest` + Turbine)
  and the download-state mapper. Prioritize these; skip UI tests unless asked.

## Git & workflow

- Conventional commits: `feat(shorts): pooled pager playback`, `fix(player): release on nav exit`.
- Small commits, buildable at every commit where practical.
- Every agent-assisted commit includes the trailer (already in `.gitmessage` via `commit.template`):
  `Co-authored-by: Claude <noreply@anthropic.com>`
- Never force-push, never rewrite published history.
- When uncertain about a requirement, stop and ask — do not invent scope.
- Doc updates ship in the same commit as the code they describe, never in a
  follow-up "update docs" commit.


## Documentation you must maintain

Code changes that alter structure or scope are not done until the
corresponding document is updated **in the same commit**.

### `docs/decisions.md` — append-only decision records

Append a new entry when any of these happen:

- A layer, package, or module boundary is added, removed, or moved.
- A library in the hard-constraints list is swapped, or a new dependency is added.
- A pattern is applied inconsistently on purpose (e.g. one screen deviates from MVI).
- A stated constraint in this file turns out to be unworkable and we work around it.
- A feature is cut, deferred, or reduced in scope.

Rules:
- **Append only.** Never edit or delete an existing entry, even one that is now wrong.
- To reverse a past decision, write a new entry and set the old one's status to
  `Superseded by D-0NN`. That status line is the only edit ever permitted to an old entry.
- Number sequentially. Check the last ID before writing.
- If you are unsure whether a change warrants an entry, write one. Over-recording
  is cheap; a silent architecture change is not.

### `README.md`

- Update the Status checklist when a feature becomes complete or is cut. Tick only
  what genuinely works — never tick something needing device verification.
- If a decision record changes how the app is structured, update the README's
  architecture description to match. Never leave the README describing a structure
  that no longer exists.
- Never delete README content that documents a known limitation or a deliberate
  omission. Those are load-bearing for review.

### `AGENTS.md` (this file)

- Do not edit this file silently. If a rule here is wrong or blocking, say so and
  propose the change; I decide.
- Note: `CLAUDE.md` and the other tool configs are **symlinks** to this file.
  Writing to any of them rewrites this file. Don't.

## Definition of done (per feature)

You can verify: compiles clean, unit tests pass, loading/empty/error states present,
no `data` imports in `ui`, no `MutableStateFlow` exposed 
and any structural change has a matching entry in `docs/decisions.md`.

I verify on device: rotation safety, no player leaks (LeakCanary), no audio bleed,
offline playback. Report these as "needs my verification" — never as done.

Use installed skills under plugins whenever necessary.

