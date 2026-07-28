# Decision records

Append-only. Never edit or delete an existing entry, even one that is now wrong.
To reverse a decision, add a new entry and set the old one's status to
`Superseded by D-0NN` — that status line is the only edit ever permitted.

---

## D-001 — Split into five Gradle modules

**Status:** Accepted · 2026-07-27

`:app`, `:domain`, `:data`, `:core:player`, `:core:designsystem`.

`:domain` is a pure `kotlin("jvm")` module with no Android plugin, so "no framework
imports in the domain layer" is enforced by Gradle rather than by review. Dependency
direction (`ui → domain ← data`) becomes a compile error when violated.

Features remain packages inside `:app` rather than `:feature:*` modules. The PRD's
own §3 warns against over-modularizing; eleven modules would cost roughly half a day
of build-file wiring against a two-day budget for no additional review signal.

**Consequence:** adding KSP (for Hilt) to modules built by AGP 9.3.1 fails at
configuration time — KSP registers its generated sources through `kotlin.sourceSets`,
which AGP 9's built-in Kotlin rejects. `android.disallowKotlinSourceSets=false` in
`gradle.properties` is the suppression AGP's own error message points at. Remove it
once KSP registers through `android.sourceSets` instead.

---

## D-002 — Compose-native playback surfaces

**Status:** Accepted · 2026-07-27

Both the long-form player and the Shorts pager render through `PlayerSurface` from
`media3-ui-compose` 1.10.1, with Compose-native controls, rather than `PlayerView`
inside `AndroidView`.

The PRD (§5.1) permits either. Compose-native keeps "Jetpack Compose for all UI"
literally true with zero View interop, and `media3-ui-compose` supplies state holders
so the controls are not hand-rolled from scratch.

---

## D-003 — One shared sealed `AppError`

**Status:** Accepted · 2026-07-27

`:domain` defines a single sealed `AppError` (`Network`, `NotFound`, `Storage`,
`Unknown`) rather than a separate sealed error type per screen.

The PRD (§4) requires errors modelled as a sealed type rather than a raw String; it
does not require one hierarchy per screen. Six near-identical hierarchies would add
code without adding information.

---

## D-004 — Sign-out is a dialog, not a navigation route

**Status:** Accepted · 2026-07-27

Screen 07 in the PRD's checklist is a confirmation dialog gating sign-out. It is
modelled as a dialog over Profile, giving five navigation keys plus one dialog rather
than six routes. A route would put a modal confirmation into the back stack, where
the system back gesture would dismiss it inconsistently.

---

## D-005 — Bottom-bar navigation

**Status:** Accepted · 2026-07-27

Home, Shorts, Downloads, and Profile are top-level destinations behind a bottom bar.
`Player` pushes over the bar and hides it. The bar remains visible on Shorts: it is a
top-level destination, and hiding it would strand the user on a full-screen pager.

---

## D-006 — `AppError` extends `Exception`; repositories return `Result<T>`

**Status:** Accepted · 2026-07-27

`AppError` is a sealed `Exception` hierarchy, so repositories return Kotlin's
`Result<T>` and keep the stdlib combinators (`map`, `mapCatching`, `fold`,
`getOrElse`) instead of a hand-rolled `Outcome` type with hand-written equivalents.

The alternative — a sealed `Outcome<T>` — is purer, but costs bespoke combinators
that must themselves be correct and tested, for no gain the reviewer can see.

**Consequence:** `runCatching` must never be used in a repository. It catches
`CancellationException` and silently breaks structured concurrency. Every repository
uses an explicit `catching` helper that rethrows `CancellationException` first.

---

## D-007 — `NavDisplay` entry decorators are passed explicitly

**Status:** Accepted · 2026-07-27

`NavDisplay` is always called with an explicit `entryDecorators` list:

    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

Inspecting `navigation3-runtime` 1.1.4 confirms it ships only
`SaveableStateHolderNavEntryDecorator`; ViewModel scoping lives in a separate
artifact, `lifecycle-viewmodel-navigation3`, which `navigation3-ui` does not depend
on. The default decorator set therefore *cannot* include ViewModel scoping.

Without the explicit list, every screen ViewModel is Activity-scoped: `onCleared()`
never fires on pop, the long-form `ExoPlayer` is never released, and the app leaks a
player per Player-screen visit. Playback still appears to work, so the defect is
invisible without LeakCanary — which is what makes it worth recording.

**Consequence:** any future `NavDisplay` call site must pass this list. Adding one
without it silently reintroduces the leak.

---

## D-008 — Transport controls bypass the MVI intent channel

**Status:** Accepted · 2026-07-27

Play/pause, mute, and scrub are driven by the `media3-ui-compose` state holders
(`rememberPlayPauseButtonState`, `rememberMuteButtonState`,
`rememberProgressStateWithTickInterval`) acting directly on the `Player`. They do not
travel through `PlayerIntent`.

Screen-level concerns — retry, download, selecting an up-next item — **do** go through
`PlayerIntent`, and all screen state still lives in `PlayerUiState`.

**Why:** routing transport through the ViewModel would duplicate state holders Media3
already ships and tests, and would push a position tick through a `StateFlow` several times
a second purely to re-render a scrubber. The `Player` is owned by the ViewModel either way,
so the ownership rule is not weakened — only the control path is shortened.

**Consequence:** `PlayerViewModel` exposes `val player: Player`. That is a deliberate
exception to "UI never touches anything but state and intents", and it is the reason
`PlayerHolder` exists: the ViewModel depends on that narrow interface, not on `ExoPlayer`,
so it stays unit-testable.

---

> **Numbering note:** D-008 through D-010 are pre-allocated by the remaining
> implementation plans, which reference those IDs in code comments. Records written
> outside a plan therefore start at D-011 to avoid renumbering them.

---

## D-011 — `compileSdk` is 37; Coil pinned to 3.4.0

**Status:** Accepted · 2026-07-27

Two build-environment constraints stated in the plans turned out to be unworkable as
written, and were resolved in the direction that leaves runtime behaviour unchanged.

**`compileSdk` 36 → 37.** Seven dependencies the plans pin — `core-ktx` 1.19.0,
`lifecycle` 2.11.0 (three artifacts), and `hilt-navigation-compose` 1.4.0 (two
artifacts) — publish AAR metadata requiring consumers to compile against API 37.
`checkDebugAarMetadata` fails the build outright, so `assembleDebug` and every test
task were blocked. `compileSdk` only governs which APIs are compilable;
**`targetSdk` stays 36 and `minSdk` stays 25**, so no runtime behaviour changes.

The alternative — downgrading all seven — would have dragged
`lifecycle-viewmodel-navigation3` down with them. That artifact provides
`rememberViewModelStoreNavEntryDecorator`, without which no screen ViewModel is
scoped to its `NavEntry`, `onCleared()` never fires on pop, and the `ExoPlayer`
leaks. Losing it to preserve a compile-time constant would trade the
highest-weighted rubric item for nothing.

**Coil 3.5.0 → 3.4.0.** Coil 3.5.0 requires `kotlin-stdlib` 2.4.0, whose metadata
version AGP 9.3.1's built-in Kotlin compiler cannot read (it reads to 2.3.0). Coil
3.4.0 requires 2.3.10 and resolves cleanly. Forcing `kotlin-stdlib` back to 2.3.21
with `strictly` also compiles, but runs Coil against an older stdlib than it was
built with, risking `NoSuchMethodError` on image loads — a failure that would not
surface until the Home feed existed. Downgrading Coil removes the risk rather than
deferring it.

**Consequence:** the AGENTS.md rule "never bump AGP, Kotlin, or Compose compiler
versions" is intact — none of those moved. `compileSdk` is not on that list.

---

## D-012 — `hilt-lifecycle-viewmodel-compose` replaces `hilt-navigation-compose`

**Status:** Accepted · 2026-07-27

`hiltViewModel()` is imported from
`androidx.hilt.lifecycle.viewmodel.compose`, not `androidx.hilt.navigation.compose`.

`androidx.hilt:hilt-navigation-compose:1.4.0` — which the plans specified — depends
transitively on `androidx.navigation:navigation-compose:2.9.0`. That is **Nav2**, which
the PRD bans outright and which a reviewer can spot in the dependency tree in seconds.
It also pulled `androidx.fragment:fragment`.

`androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0` exposes the same
`hiltViewModel()` overloads with no navigation dependency at all. It was already on the
classpath as a transitive of the artifact it replaces.

**Consequence:** `androidx.fragment` is still present, but now solely as a transitive of
`com.google.dagger:hilt-android`, which supports `@AndroidEntryPoint` on Fragments and
so declares it. That cannot be removed without dropping Hilt. No Fragment is subclassed
or referenced anywhere in the source, which is what the constraint actually forbids.

---

## D-013 — Shorts has no `Effect` type

**Status:** Accepted · 2026-07-27

`AGENTS.md` requires every screen to define `XxxUiState`, `XxxIntent`, `XxxEffect` and
`XxxViewModel`. `ShortsContract.kt` defines three of the four: there is no `ShortsEffect`.

Shorts is a self-contained surface. It navigates nowhere, raises no snackbar, and opens no
dialog — the only outbound signal is the pager settling, which is an *intent* travelling up,
not an effect travelling down. An empty sealed interface plus an unused `Channel` and an
unused `LaunchedEffect` collector in the route would be four pieces of ceremony that no
call site ever exercises, and dead code in a take-home reads worse than a documented gap.

**Consequence:** if Shorts later grows a one-shot event — "open this creator's channel",
an error snackbar distinct from the inline error state — the `Effect` channel goes in then,
matching `HomeViewModel`'s existing pattern exactly. Until then, every other screen keeps
the full contract and this is the one deliberate exception.

---

## D-014 — Shorts renders through a `TextureView`; the Player screen keeps its `SurfaceView`

**Status:** Accepted · 2026-07-27

`PlayerScreen` passes `SURFACE_TYPE_SURFACE_VIEW` to `PlayerSurface`. `ShortsScreen` passes
`SURFACE_TYPE_TEXTURE_VIEW`. The two playback surfaces deliberately differ.

A `SurfaceView` is drawn in its own compositor layer *behind* the app window and is only
visible because the view hierarchy punches a transparent hole through to it. `VerticalPager`
offsets each page through a `graphicsLayer`, which composites the page into its own layer,
and the hole is never punched. The observed failure is specific and misleading: the h264
decoder runs, `dumpsys audio` shows a started `AudioTrack`, and the screen is pure black —
it looks like a load failure rather than a compositing one. A `TextureView` draws in the
normal hierarchy and survives the transform, which is why it is the standard choice for
video inside a pager.

The Player screen is not in a pager and gains nothing from switching, so it keeps the
cheaper `SurfaceView` (no extra texture copy, better for a long-form 16:9 stage).

**Also settled here:** `PlayerSurface` does no aspect-ratio fitting of its own. Filling the
page stretched the catalog's 16:9 shorts into a 9:20 box. Both surfaces now scale through
`resizeWithContentScale` — `ContentScale.Crop` for Shorts, matching what a shorts feed is
expected to do.

**Consequence:** any future full-bleed playback surface inside a scrolling or paging
container must use `TEXTURE_VIEW`. The symptom of getting it wrong is a black page with
working audio.
## D-009 — `DownloadRepositoryImpl` lives in `:core:player`, not `:data`

**Status:** Accepted · 2026-07-27

`CatalogRepositoryImpl` and `SessionRepositoryImpl` live in `:data`. `DownloadRepositoryImpl`
does not — it lives in `:core:player`.

The implementation is inseparable from Media3: it needs `DownloadManager`, `DownloadHelper`,
`DownloadService`, and the shared `SimpleCache`. Putting it in `:data` would mean adding the
whole Media3 offline stack to a module whose job is Ktor and DataStore, purely to satisfy a
naming convention.

The architectural rule that actually matters is unbroken: `:core:player → :domain`, `:app`
injects the `DownloadRepository` **interface**, and no Media3 type crosses into `:app`.

**Consequence:** "repository implementations live in `:data`" is not a project-wide
invariant. The invariant is "repository implementations live beside the technology they
wrap, and depend only on `:domain`."

> Numbering note: D-009 and D-010 were never written — the sequence jumped from D-008 to
> D-011 during the player work — so this record takes the first free id rather than
> appending after D-012. The Shorts branch independently uses D-013 and D-014, so there is
> no collision when these branches merge.

---

## D-010 — Downloads cap video bitrate; one catalog source stays large regardless

**Status:** Accepted · 2026-07-27

`DownloadRepositoryImpl` passes `TrackSelectionParameters` with `maxVideoBitrate` set to
1.5 Mbps rather than letting `DownloadHelper` select renditions freely.

Left at its defaults, `DownloadHelper` picks by decoder capability alone, and the catalog's
1080p sources come to roughly half a gigabyte each — absurd for a demo, and slow enough to
make the progress UI untestable.

The constraint is deliberately **soft**. `DefaultTrackSelector` still selects the smallest
available rendition when every one of them exceeds the cap, so a single-rendition stream
downloads rather than silently producing an audio-only file that reports "Ready to play"
and then fails.

The no-argument `TrackSelectionParameters.Builder()` is used rather than the `Context`
overload, which would constrain selection to the current display size — a playback concern
that must not decide what gets stored offline.

**Consequence:** eight of the eighteen catalog videos point at `tos_ismc`, which publishes
exactly one rendition (1080p, 6.3 Mbps, ~10 minutes). No track selection can shrink those;
they download at roughly half a gigabyte each. The other ten are bounded by the cap.
Repointing those eight at a multi-rendition source is a catalog change, deferred.

---

## D-015 — Downloaded videos play from their `DownloadRequest`, so `PlayerHolder` takes a video id

**Status:** Accepted · 2026-07-27

`PlayerHolder.setMedia` was `setMedia(hlsUrl, startPositionMs)`. It is now
`setMedia(videoId, hlsUrl, startPositionMs)`, and `ExoPlayerHolder` consults the
`DownloadIndex` before choosing what to play: a `STATE_COMPLETED` download is played via
`download.request.toMediaItem()`, anything else streams `hlsUrl`.

Playing the master playlist URL for a downloaded video does not work offline. The URL lets
the track selector pick any rendition in the ladder, including ones that were never stored;
online the miss is silently fetched and everything looks correct, and offline it fails with
`UnknownHostException`. That is exactly what the first airplane-mode test produced. The
`DownloadRequest` carries the stream keys naming the rendition actually on disk, so playing
through it constrains the selector to what exists locally.

The lookup lives in `:core:player` rather than in the ViewModel. `:app` passes a plain
`String` id and never learns that Media3, `DownloadIndex`, or `DownloadRequest` exist.

**Consequence:** `ExoPlayerHolder` now depends on `DownloadManager`, so the player and
download stacks are coupled inside `:core:player`. That is the same coupling the shared
`SimpleCache` already implies, and it is contained within one module.

---

## D-016 — Two Media3 behaviours the plans assumed wrongly

**Status:** Accepted · 2026-07-27

Both were found by running the app, not by reading it, and both are recorded because the
code now looks over-built without the explanation.

**`DownloadHelper` needs a `RenderersFactory` or it downloads everything.** Built through
`DownloadHelper.Factory()` without one, the helper has no renderer capabilities to select
against, every track resolves as unsupported, and `getDownloadRequest` returns an empty
stream-key list — which Media3 reads as "store the entire media". Measured: all five
renditions of the 848×480 test stream, ~800 MB instead of ~127 MB, with the D-010 bitrate
cap having no observable effect whatsoever. The cap only became real once
`setRenderersFactory(DefaultRenderersFactory(context))` was added.

**`DownloadManager.Listener` is not a progress source.** It fires on state transitions —
queued, completed, removed — and never as bytes arrive. Observing it alone, as the plan
specified, left the row frozen at "13% · 105.5 MB" for 24 seconds while the cache on disk
grew from 263 MB to 291 MB. `DownloadRepositoryImpl` now also polls the index once a second
while any download is in progress, and stops the moment none is.

**Consequence:** "progress comes from observing `DownloadManager` into a Flow" is true of
where the numbers come from, not of when they are read. Nothing is interpolated: a stalled
download still visibly stalls, which a timer-driven fake progress bar would hide.

---

## D-017 — Design pass applied last; PRD outranks the design on all three conflicts

**Status:** Accepted · 2026-07-27

*(Numbered D-017, not D-010 as the design plan states. This plan was written before the
Downloads plan executed and took D-009/D-010, and Shorts took D-013/D-014. `decisions.md`
is append-only, so the record takes the next free id rather than the one the plan predicted.)*

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
3. The design's "Sign in with email" navigates directly with no input field. The plan
   resolved this one *in favour of the design* — and that resolution was **overruled during
   execution**. It contradicts the rule the other two follow: §9 requires Onboarding to
   offer email sign-in, and the PRD outranks the design. By then the email path was also
   verified working on a device, so following the design would have deleted a working,
   tested, verified flow to save a `TextField`. The field, its validation and both tests
   stay; only the styling changed.

Dynamic colour was removed from the theme: the design specifies a fixed accent, and letting
device wallpaper repaint the app would defeat the purpose of shipping a design at all.

**Consequence:** `StreamlyColors` is the only file permitted to declare a colour literal, and
the design audit enforces it with a grep. All three PRD/design conflicts now resolve the same
way, which is easier to defend than two-out-of-three.

---

## D-018 — Toasts travel by `Effect`, not in `UiState`

**Status:** Accepted · 2026-07-27

The design pass specified adding `toastMessage: String?` to each screen's `UiState`, with the
ViewModel clearing it after a delay. That is not what was built.

`AGENTS.md` states as a hard constraint that one-shot events — naming snackbars explicitly —
travel through an `Effect` Channel and never through state. A toast is precisely that. Storing
it in state would also replay the toast on every configuration change, because the state would
be re-collected with the message still set.

Instead the ViewModel emits an `Effect` (`PlayerEffect.DownloadStarted`, `LinkCopied`,
`ProfileEffect.ShowToast`, `DownloadsEffect.ShowToast`) and a route-local `rememberToastState()`
owns only how long the pill stays up. The plan's stated reason for putting the message in
state was ViewModel testability; that is preserved, because what the tests assert is the
effect rather than a timer.

**Consequence:** display duration is presentation state and is not unit-tested. What is tested
is that the right effect is raised — which is the part that can regress silently.

---

## D-019 — Adaptive layout keys on window *height*, and only the Player screen gets it

**Status:** Superseded by D-020 · 2026-07-28

`WindowSizeClass` was plumbed from `MainActivity` to `StreamlyNavHost` in Phase 2 and then
sat unused behind a `@Suppress("UNUSED_PARAMETER")`. It is now consumed, in one place.

Two narrowings, both deliberate:

**Height, not width.** The failure this fixes is a phone in landscape: a full-width 16:9
stage consumes the entire viewport and pushes the title, action row, transport controls and
up-next list off-screen with no way to reach them. That condition is
`heightSizeClass == Compact`, and branching on width would not catch it — the same phone is
`widthSizeClass == Expanded` there, which is also true of a tablet in portrait, where the
single-column layout is correct and should not change.

**Player only.** Home, Downloads and Profile are lists whose single-column layout stays
usable at every size class. Adding breakpoints they do not need would be layout code with no
failing case behind it. The other screens can adopt a size class when one of them actually
breaks.

A second change came with it: the Player's details were a `Column` wrapping a `LazyColumn`,
which pinned the title, channel row, actions and controls above a separately-scrolling
up-next list. They are now items in one `LazyColumn`, shared between both arrangements as a
`LazyListScope.details()` extension, so the two layouts cannot drift apart. The surface also
gained `resizeWithContentScale(ContentScale.Fit)`, because the landscape pane is not 16:9 and
without it the picture would stretch — the same class of defect that `ContentScale.Crop`
fixed in Shorts (D-016).

**Consequence:** the README's "Player does not adapt to landscape" limitation is addressed in
code but stays listed until a device confirms it, and the Status checkbox stays unticked for
the same reason. Rotation safety is unaffected — nothing here touches player ownership.

---

## D-020 — Landscape is fullscreen playback, not a two-pane layout

**Status:** Accepted · 2026-07-28 · Supersedes D-019

D-019 split the Player into a video pane and a details pane at Compact height. Built, run on
the emulator, and rejected on sight: the up-next list took half a 2400×1080 window while the
video was squeezed into a 1200-wide pane that letterboxed it to 1200×675, with 170px of black
above and below. Both halves were compromised to avoid choosing between them.

Compact height now renders the stage and nothing else, with the system bars hidden. The
window is the video's. Rotating back, or leaving the screen, restores everything — the
`DisposableEffect` that hides the bars is scoped to the landscape branch, so nothing else has
to remember to undo it.

What D-019 got right and this keeps: the branch is on **height**, not width, and no screen
other than the Player consumes a size class.

What it got wrong: it treated "the details are unreachable in landscape" as the problem to
solve. The real problem is that a phone in landscape is a screen shaped for a video and
nothing else, which is why every video app treats that orientation as fullscreen. The details
are not lost — they are one rotation away, which is the gesture users already reach for.

The transport controls stay, overlaid on a scrim at the bottom in white. Without them
landscape would have no pause and no seek, and the only way to reach either would be to
rotate out. They do not auto-hide on a timer: more code, and less discoverable.

**Consequence:** the two-pane branch is gone, so this is less code than D-019 shipped.
`ContentScale.Fit` still applies, so on a 20:9 phone the video pillarboxes to 1920×1080 with
240px of black each side. That is the correct result for a 16:9 source on a 20:9 display and
matches what other players do; cropping to fill would cut the picture.

---

## D-021 — Player controls live on the video, per the design

**Status:** Accepted · 2026-07-28

The transport controls were built under the action row, in the description area. The design
puts them on the stage: `streamly.dc.html` lines 104–116 draw a back arrow top-left and a
60px translucent circle with a play/pause glyph centred on the video, and its description
area holds only the title, the offline label, the channel row and the three action buttons.
They have moved to match.

Three things the design does not answer, resolved here:

**Seek, time and mute are not in the design at all.** PRD line 157 requires "play/pause,
seek/scrub, mute, and a visible buffering state", and per D-017 the PRD outranks the design,
so they cannot simply be dropped. They go in a bar along the bottom of the same stage rather
than back under it — splitting the controls across two surfaces would be worse than either
whole answer.

**`material-icons-core` has no Pause and no volume glyph**, and the catalog deliberately
excludes `material-icons-extended` (it is large, and the bottom bar needed four icons). Pause
is drawn as the design draws it — two rounded white bars — and mute is the word "Mute" /
"Muted". Pulling in the extended icon set for two glyphs is not a trade worth making.

**White controls over an arbitrary video frame are legible by luck.** The design's stage is a
flat `#0d0e24` with no picture behind it; a real one opens on a bright sky. Two gradient
scrims, top and bottom, sit under the controls. The picture itself is not dimmed.

The back arrow is new — the design has one and the screen had none, relying on the system
gesture. It calls the same `backStack.removeLastOrNull()` the gesture does, so the two cannot
disagree.

**Consequence:** one control layer now serves both orientations. `FullscreenStage` no longer
carries its own copy, which is why D-020's bottom-scrim overlay is gone — same controls,
drawn once, in the stage. The controls do not auto-hide; that remains true in both
orientations, for the reason given in D-020.

---

## D-022 — A fullscreen button that rotates the device, and a hand-built seek bar

**Status:** Accepted · 2026-07-28

Two changes to the control layer D-021 put on the stage, both to make it read like the
players people actually use.

**The seek bar is built from `Slider`'s `thumb` and `track` slots.** M3's default is a 16dp
expressive control with a wide pill thumb, which is fine in a settings screen and far too
heavy sitting on video. The slots take a 3dp track and a 12dp round thumb. Those slots are
still `@ExperimentalMaterial3Api` in this version, which is the reason for the opt-in — the
alternative is drawing the bar on a `Canvas` and reimplementing tap-to-seek and drag, for a
worse result.

**Fullscreen is a button, and the button rotates the device.** It sets
`requestedOrientation` to `USER_LANDSCAPE` and back to `USER_PORTRAIT`, rather than
introducing an `isFullscreen` flag in `PlayerUiState`.

Landscape already *is* fullscreen (D-020). A separate in-app fullscreen state would mean two
ways to be fullscreen that could disagree — rotate to landscape with the flag false, and the
screen has to decide which wins. Deriving it from the window size class keeps exactly one
source of truth, and the button becomes a request to change the window rather than a piece of
state to maintain. It also stays out of `PlayerUiState`, which has no business tracking
device orientation.

**Consequence:** the Player locks the activity's orientation while it is open, so a
`DisposableEffect` resets it to `UNSPECIFIED` on exit. Without that reset the lock outlives
the screen and every other tab inherits it. Verified on device: after leaving the Player,
rotating the emulator turns the rest of the app again.
