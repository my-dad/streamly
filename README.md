# Streamly

A minimal YouTube-style Android app: two playback surfaces (long-form + vertical shorts)
streamed over HLS with Media3, real offline downloads, onboarding with session persistence,
and profile/sign-out.

Network data is faked. The architecture, player lifecycle, and code quality are real.

## Status

Nothing below is ticked. A box gets ticked only after the feature is verified on a
physical device — not when the code compiles and its unit tests pass.

- [ ] Onboarding with session persistence — *implemented, awaiting device verification*
- [ ] Home feed with categories, loading/empty/error states — *implemented, awaiting device verification*
- [ ] Player — HLS, lifecycle-correct, rotation-safe — *implemented, awaiting device verification*
- [ ] Profile + sign-out confirmation — *implemented, awaiting device verification*
- [ ] Shorts — vertical pager, pooled players — *not started*
- [ ] Downloads — real progress, offline playback, remove — *not started*
- [ ] Adaptive layout via `WindowSizeClass` — *not started*

The device checks that gate these are listed per-feature in the corresponding plan under
`docs/superpowers/plans/`, under a "needs device verification" heading. They cover rotation
safety, player release and leak checks, audio bleed, and offline playback — none of which
a unit test can establish.

**Work in flight.** Home feed is on `master`. Player is on `feat/player` and
Onboarding/Profile on `feat/onboarding-profile`; both are complete with green unit tests
but are held unmerged until their device checks pass. Anything below that references those
features — including decision record D-008 — arrives on `master` with them.

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
`MutableStateFlow`, never a navigation lambda. Loading, empty, and error states are modelled
inside every `UiState`, with errors as a sealed `AppError` rather than a raw `String`, and
rendered through one shared `ContentState` wrapper.

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
- **`VideoCard` takes formatted primitives, not a model.** It lives in `:core:designsystem`,
  which cannot depend on `:app`, so passing pre-formatted strings keeps the component
  reusable and the module graph acyclic.

## Known limitations

- The MockEngine fails roughly one request in eight by design, so error states are genuinely
  reachable in the demo. This is deliberate, not a bug.
- No instrumented tests. Unit tests cover ViewModel intent→state transitions and all display
  formatters; the rendering, lifecycle, and playback behaviour they cannot reach is what the
  device checks in the Status section exist for.
- `compileSdk` is 37 while `targetSdk` stays 36 — forced by AAR metadata on several AndroidX
  dependencies. See D-011.
