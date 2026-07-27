# Agent log

Workflow evidence per PRD §10: what was asked, what came back, and what was done about it.

## 2026-07-27 — Brainstorm, build plan, and seven implementation plans

**Tool:** Claude Code (Opus 5)
**Prompt:** "/superpowers:brainstorming — i am building this app for an interview process and docs/streamly-handoff.md is the PRD file."
**Result:** Settled five up-front decisions (module split, Compose-native playback, shared
AppError, dialog-not-route sign-out, bottom-bar nav). Produced `docs/streamly-build-plan.md`
and the task-level plans under `docs/superpowers/plans/`. Reviewed each plan before
executing; several were revised after self-review caught defects.

## 2026-07-27 — Navigation 3 and Media3 API verification

**Tool:** Claude Code (Opus 5)
**Prompt:** "go read the nav3 docs and write plan 2 … verify media3 first"
**Result:** Both libraries post-date the model's training data, so the APIs were read
directly from the published AARs with `javap` rather than recalled. This caught that
`NavDisplay`'s default entry decorators exclude ViewModel scoping (D-007) — which would have
leaked an ExoPlayer per Player-screen visit while appearing to work.

## 2026-07-27 — Phases 0–3: skeleton, domain/data, design system, Home feed

**Tool:** Claude Code (Opus 5)
**Prompt:** *(to be filled in — these sessions predate this log)*
**Result:** Five Gradle modules, Ktor + MockEngine over the bundled catalog, DataStore
session, design system with `ContentState`, Nav3 shell, and the Home feed. Merged to
`master` via PRs #1 and #2. Two build-environment constraints in the plans turned out to be
unworkable and were resolved rather than worked around silently: `compileSdk` had to move to
37 (D-011) and `hilt-navigation-compose` had to be swapped for
`hilt-lifecycle-viewmodel-compose` because it pulled in Nav2 (D-012). A final review of the
Home feed caught an out-of-order load race between category requests, fixed in `9f3205a`
with a deterministic regression test.

## 2026-07-27 — Phase 4 Player, Phase 7 Tasks 1–2, README, and two split tasks

**Tool:** Claude Code (Opus 5)
**Prompts:**
- "review this and check whats plans are pending? use skills plugins, subagents to leverage the work and report back to me"
- "Player is the next branch (feat/player off master). Want me to start it -> yes."
- "lets keep this branch intact and progress tracked so that we can revisit this after rest of the tasks are done for this plan"
- "besides the player changes as a blocker right now, what else can be done without the blocker?"

**Result:** Executed the Player plan end to end on `feat/player` — shared `SimpleCache` and
HLS data-source graph, the `PlayerHolder` seam, MVI contract with VM-owned lifecycle,
Compose-native `PlayerSurface` with a real buffering shutter, and lifecycle wiring. Held
unmerged: its device checks include the first genuine test of whether the Nav3 ViewModel
decorator scopes to `NavEntry`, which nothing had verified.

Three plan deviations, all recorded in the plan file: the plan's `hiltViewModel()` import is
the banned Nav2-pulling one (D-012); the Mockito test dependency it specifies was not needed,
since no test touches `PlayerHolder.player`; and `@file:OptIn(UnstableApi::class)` is inert
in Media3 1.10.1, where `UnstableApi` is not annotated `@RequiresOptIn`.

With Player blocked, work continued on what did not depend on it: Onboarding and Profile
(Phase 7 Tasks 1–2) off `master`, the README's durable sections with an all-unticked status
list, and the two pure-logic tasks from Shorts and Downloads — the pool assignment policy and
the download state mapper — each split onto its own branch. A trial merge of every open
branch confirmed one expected conflict in `StreamlyApp.kt` and a green 92-test suite.

**Reviewed:** every diff before commit; tests run at each step; no device available, so all
rendering, rotation, leak, and playback checks are reported as pending rather than done.
