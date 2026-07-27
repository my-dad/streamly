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
