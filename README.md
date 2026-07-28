# Streamly

A minimal YouTube-style Android app: two playback surfaces (long-form + vertical shorts)
streamed over HLS with Media3, real offline downloads, onboarding with session persistence,
and profile/sign-out.

Network data is faked. The architecture, player lifecycle, and code quality are real.

## Status

A box is ticked only after the feature is verified on a device — not when the code
compiles and its unit tests pass.

- [x] **Player — HLS, lifecycle-correct, rotation-safe** — verified on an emulator: plays,
      buffering shutter clears, `onCleared()` fires on pop with exactly one player release,
      rotation never recreates the player or loses position, background pauses and return
      resumes, repeated navigation stays balanced, no leak reported
- [x] **Onboarding with session persistence** — verified on an emulator: guest sign-in, email
      sign-in, an invalid address showing "Enter a valid email address" rather than
      proceeding, and a relaunch after signing in landing straight on Home
- [x] **Home feed with categories, loading/empty/error states** — verified on an emulator:
      renders with real formatted metadata, category chips filter, the error state shows
      "No connection" with a working Retry, and rotation preserves scroll position exactly.
      One gap, listed under Known limitations: scroll is *not* preserved when returning
      from the Player
- [x] **Profile + sign-out confirmation** — verified on an emulator: avatar, name and email
      render, Cancel dismisses the dialog without signing out, and confirming clears the
      session and lands on Onboarding with the back stack reset
- [x] **Shorts — vertical pager, pooled players** — verified on an emulator: video renders
      and plays, exactly one `AudioTrack` is ever in `started` state (measured via
      `dumpsys audio`) including under fast swiping, backgrounding stops audio and returning
      resumes it, leaving the tab releases the pool, and rotation preserves the settled page
- [x] **Downloads — real progress, offline playback, remove** — verified on an emulator:
      download reaches "Ready to play", progress climbs monotonically and matches the cache
      growing on disk, the completed item **plays in airplane mode** (confirmed by
      screenshot, not just by a rising position), Remove drops storage to zero, and a
      force-stop and relaunch while still offline leaves the download listed and playable
- [x] **Adaptive layout via `WindowSizeClass`** — verified on an emulator: at Compact window
      height (a phone in landscape) the Player goes fullscreen, system bars hidden, transport
      controls overlaid; rotating back or leaving the screen restores the bars, and rotating
      mid-playback keeps the same player and position with no re-buffer. In portrait the
      stage stays pinned while the details and up-next list scroll under it. Every other
      screen is deliberately unchanged — see D-020, which supersedes D-019

Once Shorts and Downloads were merged together, the integrated build was re-checked for the
one failure neither branch could produce alone — audio bleeding between the two playback
surfaces. Walking Shorts → Home → Player → Shorts → Downloads, at most one `AudioTrack` is
ever in `started` state, and leaving either surface drops it to zero.

The device checks that gate these are listed per-feature in the corresponding plan under
`docs/superpowers/plans/`. They cover rotation safety, player release and leak checks, audio
bleed, and offline playback — none of which a unit test can establish. `docs/streamly-build-plan.md`
carries the phase-by-phase state.

## Architecture

Five Gradle modules:

    :app                 wiring, NavDisplay host, DI graph, one package per screen
    :domain              pure kotlin("jvm") — models, repository interfaces, use cases
    :data                Ktor + MockEngine, DTOs, mappers, DataStore session
    :core:player         ExoPlayer ownership, pooled Shorts playback, download stack
    :core:designsystem   theme, ContentState, shared composables

All five modules exist and are wired; the Status list above says which features have
landed inside them.

Dependency direction is `:app → :domain ← :data`. `:domain` has no Android plugin at all,
so "no framework imports in the domain layer" is enforced by Gradle rather than by review.
DTOs and Ktor types never leave `:data` — the UI injects domain repository interfaces.

Features are packages inside `:app`, not separate Gradle modules. That is a deliberate
call against over-modularizing a project this size, recorded as D-001.

Every screen follows the same MVI contract: an immutable `UiState`, a sealed `Intent`, and a
sealed `Effect` for one-shot events. ViewModels expose `state` and `onIntent` — never a
`MutableStateFlow`, never a navigation lambda. Shorts is the one deliberate exception: it
navigates nowhere and raises nothing, so it defines no `Effect` rather than carrying an
empty channel no call site ever uses. That deviation is recorded as D-013.

Loading, empty, and error states are modelled inside every `UiState`, with errors as a
sealed `AppError` rather than a raw `String`, and rendered through one shared `ContentState`
wrapper.

Decisions and their rationale are recorded in [`docs/decisions.md`](docs/decisions.md),
append-only. Several of them exist because a stated constraint turned out to be unworkable
and the workaround is worth explaining.

## Running it

    ./gradlew assembleDebug                        # build
    ./gradlew testDebugUnitTest                    # unit tests, all Android modules
    ./gradlew :domain:test                         # domain tests (pure JVM, no debug variant)
    ./gradlew :app:compileDebugKotlin              # fast compile check while iterating

`:app:testDebugUnitTest` on its own silently skips `:data` and `:core:*`. Use the
unqualified `testDebugUnitTest` to run them all.

No API keys or configuration are required — the catalog is bundled at
`data/src/main/assets/catalog.json` and served by Ktor's MockEngine. HLS media streams from
public test CDNs, so the first run needs a network.

There is no lint or formatter configured, and no instrumented tests.

The debug APK is not committed — building it is one command and the artefact is ~19 MB:

    ./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

`minSdk` is 25. The build has been exercised on an API 33 emulator; see Known limitations
for the one behaviour that only appears from API 34 and therefore could not be confirmed.

## AI workflow

`AGENTS.md` is the single source of truth for all coding agents, symlinked to `CLAUDE.md`,
`.cursor/rules`, `.codex/instructions.md`, and `.antigravity/rules` (all committed as git mode
`120000`). Agent-assisted commits carry a `Co-authored-by` trailer via the repo-local
`commit.template`.

The work was planned before it was written: `docs/streamly-build-plan.md` for phasing, then
per-phase plans in `docs/superpowers/plans/`. Each plan is executed task-by-task, its
checkboxes ticked as steps land, and deviations from the plan recorded in the plan file
itself rather than silently absorbed. `docs/agent-log.md` is where the prompts behind each
session are recorded.

Both Navigation 3 and `media3-ui-compose` post-date the model's training data, so their APIs
were read from the published AARs with `javap` rather than recalled. That caught a real
defect before it was written: `NavDisplay`'s default entry decorators do not include
ViewModel scoping, which would have leaked an ExoPlayer on every Player-screen exit while
appearing to work correctly (see D-007).

## Shortcuts taken, and why

This list grows as features land; it currently covers what is built.

- **Auth is mocked.** "Continue with Google" writes a session directly. The PRD permits this;
  the session *pipeline* — DataStore persistence, gated start destination, sign-out clearing
  — is real.
- **Category chips filter client-side** against the bundled catalog rather than re-querying.
- **Transport controls act on the `Player` directly** rather than through the intent channel,
  because Media3 already ships tested state holders for them. Recorded as D-008.
- **Subscribe and Like are local, non-persisted state.** There is no subscription or
  reaction backend to talk to, so the buttons toggle in the ViewModel and reset when the
  screen is popped. The PRD permits the stub; pretending otherwise would be worse.
- **The visual design was applied last, as a presentation-layer pass** over working screens,
  and never changed behaviour. Where `streamly.dc.html` and PRD §9 disagreed, the PRD won all
  three times — see D-017.
- **`VideoCard` takes formatted primitives, not a model.** It lives in `:core:designsystem`,
  which cannot depend on `:app`, so passing pre-formatted strings keeps the component
  reusable and the module graph acyclic.

## Known limitations

- **Shorts restart from the beginning when you swipe back to them.** The pool holds only
  the settled page and its successor, so paging backwards re-prepares the stream at 0
  rather than resuming where you left it. Deliberate — it is what a shorts feed does.
- **Only the Player adapts to window size.** Home, Downloads and Profile keep their
  single-column layout at every size class, so in landscape a Home card is one very wide row.
  They stay usable; they are not laid out for the space. Deliberate, recorded as D-019 —
  breakpoints without a failing case behind them are layout code nobody asked for.
- **Landscape hides the Player's details rather than laying them out.** Title, actions and
  the up-next list are one rotation away, not on screen. That is the fullscreen convention
  (D-020), not an oversight. The fullscreen button rotates the device to get there (D-022),
  so on a device with rotation locked in system settings it is the only way in.
- **The Player's controls never auto-hide.** They sit over the video permanently rather than
  fading out after a few seconds. Deliberate — a timer is more code and less discoverable —
  but it does mean the bottom of the frame is always partly covered.
- The MockEngine fails roughly one request in eight by design, so error states are genuinely
  reachable in the demo. This is deliberate, not a bug.
- No instrumented tests. Unit tests cover ViewModel intent→state transitions and all display
  formatters; the rendering, lifecycle, and playback behaviour they cannot reach is what the
  device checks in the Status section exist for.
- **Returning from the Player resets the Home feed's scroll position.** The feed itself is
  not reloaded — no refetch, no loading flash, the data is retained — but the list returns to
  the top. Rotation preserves the same scroll perfectly, which isolates it to the Nav3
  `SaveableStateHolder` entry decorator not restoring on pop rather than to anything in the
  screen: `rememberSaveable` demonstrably works here. Two fixes were tried and measured
  (hoisting the `LazyListState` above `ContentState`, and reversing the decorator order);
  neither changed the behaviour, so both were reverted rather than left as unmotivated churn.
- `compileSdk` is 37 while `targetSdk` stays 36 — forced by AAR metadata on several AndroidX
  dependencies. See D-011.
- **Eight of the eighteen catalog videos are ~500 MB to download.** They point at a test
  stream that publishes exactly one rendition (1080p, 6.3 Mbps, ~10 min), so the bitrate cap
  in D-010 cannot shrink them. The other ten download at roughly 130 MB. Repointing those
  eight at a multi-rendition source is a catalog change, deliberately deferred.
- **The download foreground-service manifest cannot be verified here.** `foregroundServiceType`
  and `FOREGROUND_SERVICE_DATA_SYNC` are only enforced from API 34, and the emulator used
  throughout is API 33. Both are declared and confirmed present in the merged manifest, but
  no device that enforces them has run this build.
- **Downloads are not resumed after a reboot.** `DownloadService.getScheduler()` returns
  `null` deliberately — restoring them needs WorkManager, which the PRD does not ask for.
