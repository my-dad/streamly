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

## 2026-07-27 — Phase 5 Shorts, and the review that caught a screen rendering nothing

**Tool:** Claude Code (Opus 5)
**Prompts:**
- "Lets start, which plan is still pending?"
- "yes, cut the branch and start Task 2" · "start Task 3" · "start Task 4"
- "No review the shorts part on device and observe through adb and look for shorts loading
  issue, fast scrolling issue etc etc edge cases, if app goes to background is sound
  playing. LEts review this before moving on"

**Result:** Executed the Shorts plan on `feat/shorts-pager` — a two-player `ShortsPlayerPool`
behind a `ShortsPool` interface seam, the MVI contract, and a `VerticalPager` keyed on
`settledPage`. Four plan deviations recorded in the plan file, including a formatter rewrite
the plan specified that would have silently changed `formatViewCount(1_284_000)` from
`1.3M` to `1.2M` and broken a passing test.

**The fourth prompt is the important one.** I had reported Shorts as smoke-verified on the
strength of log evidence — pool assignments logged, an h264 decoder active, an `AudioTrack`
in `started` state. Every one of those signals said working. Pushed to actually look at the
screen, a screenshot showed **pure black**: `VerticalPager` composites each page through a
`graphicsLayer`, which defeats the hole-punching a `SurfaceView` depends on. Audio played,
frames decoded, and the user saw nothing. Fixed with `TEXTURE_VIEW` (D-014), along with a
stretched aspect ratio and an illegible caption — neither of which any log would have shown.

The lasting change was to method, not code: `dumpsys audio` was turned into an objective
audio-bleed probe (counting tracks in `started` state), and screenshots became mandatory
rather than optional. Both carried forward into Downloads.

## 2026-07-27 — Phase 6 Downloads, integration merge, and the architecture audit

**Tool:** Claude Code (Opus 5)
**Prompts:**
- "whats next?" · "Lets begin what are left?"
- "yes, merge both branches to master then continue with the rest of the plans and use
  required plugins and skill when ever needed"

**Result:** Retired a risk the register had carried since task 0.7 before writing any code —
probing every catalog `.m3u8` with `curl` to confirm the streams are `VOD`, terminated by
`#EXT-X-ENDLIST`, and unencrypted, so `DownloadHelper` had nothing to choke on. Then built
Tasks 2–6 on `feat/downloads`: the `DownloadManager`/service/manifest plumbing,
`DownloadRepositoryImpl`, the Downloads screen, the wired Player button, and D-009/D-010.

**Three defects were found only by running the seven-step offline sequence on a device, and
all three look correct online:**
1. `DownloadHelper` built without a `RenderersFactory` returns an empty stream-key list,
   which Media3 reads as "store the entire media" — ~800 MB instead of ~127 MB, with the
   bitrate cap having no effect at all.
2. `DownloadManager.Listener` never fires as bytes arrive, only on state changes. The row
   sat at "13% · 105.5 MB" for 24 seconds while the on-disk cache grew 263 → 291 MB.
3. Playing a downloaded video by its master playlist URL let the track selector pick a
   rendition that was never stored: fine on wifi, `UnknownHostException` in airplane mode.

Recorded as D-015 and D-016. A review conducted with the network on would have called this
feature finished.

Both branches then merged to `master`, resolving the long-predicted `StreamlyApp.kt`
conflict (each replaced a different placeholder in the same `entryProvider`) and deleting
`Placeholders.kt`, which had no callers left. The integrated build was re-checked on device
for the one risk neither branch could test alone — audio bleeding between the two playback
surfaces — measuring at most one `started` track across Shorts → Home → Player → Shorts →
Downloads. The 11-check architecture audit passed, after narrowing two `runCatching` calls
the Downloads work had introduced. **121 tests, 0 failures.**

**Not verified, and reported as such:** the API 34+ foreground-service manifest lines cannot
be exercised on the API 33 emulator available here, and no LeakCanary reading was taken.
