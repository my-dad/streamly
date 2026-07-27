# Streamly Foundation + Domain/Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the bare single-module Android template into five Gradle modules and build a fully unit-tested domain + data layer — Ktor/MockEngine → DTO → mapper → domain model → repository → use case — with no UI yet.

**Architecture:** `:domain` is a pure `kotlin("jvm")` module with zero Android dependencies, so the PRD's "no framework imports in domain" is enforced by Gradle rather than discipline. `:data` owns Ktor, DTOs, mappers, and DataStore, and exposes only domain interfaces through Hilt `@Binds`. `:app` depends inward on all of them and never sees a DTO or a Ktor type.

**Tech Stack:** Kotlin 2.2.10 · AGP 9.3.1 (built-in Kotlin support) · Hilt 2.60.1 + KSP 2.2.10-2.0.2 · Ktor 3.5.1 (MockEngine) · kotlinx-serialization 1.11.0 · kotlinx-coroutines 1.11.0 · DataStore 1.2.1 · JUnit 4.13.2 + Turbine 1.2.1

## Global Constraints

- Kotlin only. Coroutines + Flow for all concurrency. **No RxJava.**
- **Ktor** is the HTTP client. **No Retrofit/OkHttp API usage.**
- DI with **Hilt** everywhere. No service locators, no manual singletons outside DI.
- Declare dependencies in `gradle/libs.versions.toml` **only**, referenced as `libs.foo.bar`. **Never hardcode a version string in a `build.gradle.kts`.**
- **Never bump AGP, Kotlin, or Compose compiler versions.** AGP stays `9.3.1`, Kotlin stays `2.2.10`, composeBom stays `2026.02.01`.
- Package root: `io.github.mabrur.streamly`. `applicationId` unchanged.
- `minSdk = 25`, `targetSdk = 36`, `compileSdk = release(36) { minorApiLevel = 1 }`. Do not change these.
- Java toolchain: **11** (`sourceCompatibility`/`targetCompatibility` are `VERSION_11` in the existing `:app` build file — every new module must match, or Gradle will fail on inconsistent JVM targets).
- Dependency direction: `:app → :domain, :data, :core:*` · `:data → :domain` · `:core:player → :domain` · `:domain → nothing`. **`:domain` must never gain an Android dependency.**
- DTOs, Ktor types, and mappers never leave `:data`.
- Conventional commits. The `Co-authored-by: Claude <noreply@anthropic.com>` trailer is applied automatically by the repo-local `commit.template` — **do not type it manually**, and do not pass `-m` in a way that bypasses the template (see Task 1 Step 6 for the exact form to use).
- Doc updates ship in the **same commit** as the code they describe.
- Never run `./gradlew clean`. Use `./gradlew :app:compileDebugKotlin` while iterating.
- **Never edit `AGENTS.md` or its symlinks (`CLAUDE.md`, `.cursor/rules`, `.codex/instructions.md`, `.antigravity/rules`) without explicit sign-off from the repo owner.** Task 9 proposes an amendment; it does not apply one.

---

## Design refinement adopted in this plan

The build plan (`docs/streamly-build-plan.md`, task 1.3) said repositories return `Result<T>`, and D-003 said `AppError` is a sealed type. Kotlin's `Result<T>` can only carry a `Throwable`. Rather than hand-roll an `Outcome` type, **`AppError` is a sealed `Exception` hierarchy**, which keeps the stdlib combinators (`map`, `fold`, `getOrElse`) and still satisfies the PRD's "sealed type, not a raw String".

This carries one footgun the plan handles explicitly: **`runCatching` swallows `CancellationException`**, which silently breaks structured concurrency. Every repository uses an explicit `try/catch` that rethrows `CancellationException` first. Do not replace these with `runCatching`.

Recorded as **D-006** in Task 9.

---

## File Structure

**Gradle wiring**
- `gradle/libs.versions.toml` — every version and coordinate. Single source of truth.
- `settings.gradle.kts` — module includes.
- `build.gradle.kts` (root) — plugin aliases, `apply false`.
- `domain/build.gradle.kts` — `kotlin("jvm")`. No Android plugin.
- `data/build.gradle.kts`, `core/player/build.gradle.kts`, `core/designsystem/build.gradle.kts` — Android library modules.
- `app/build.gradle.kts` — adds Hilt/KSP/serialization plugins + module deps.

**`:domain`** (`domain/src/main/java/io/github/mabrur/streamly/domain/`)
- `model/Video.kt`, `model/Short.kt`, `model/Category.kt`, `model/UserProfile.kt`, `model/HomeFeed.kt` — one concept per file.
- `model/Session.kt` — `Session` + `SessionState`.
- `model/DownloadItem.kt` — `DownloadItem` + `DownloadStatus`.
- `error/AppError.kt` — sealed error hierarchy.
- `repository/CatalogRepository.kt`, `repository/SessionRepository.kt` — interfaces only.
- `usecase/*.kt` — one use case per file.

**`:data`** (`data/src/main/java/io/github/mabrur/streamly/data/`)
- `remote/dto/CatalogDto.kt` — all DTOs (they're one payload; they change together).
- `remote/mapper/CatalogMappers.kt` — pure DTO→domain functions.
- `remote/CatalogApi.kt` — Ktor call sites.
- `remote/MockCatalogEngine.kt` — MockEngine construction, latency, failure injection.
- `repository/CatalogRepositoryImpl.kt`, `repository/SessionRepositoryImpl.kt`
- `di/NetworkModule.kt`, `di/SessionModule.kt`, `di/RepositoryModule.kt` — split by what they provide.
- `data/src/main/assets/catalog.json` — the seed catalog.

**`:app`**
- `StreamlyApplication.kt` — `@HiltAndroidApp`.

---

## Task 1: Gradle module skeleton

Splits the project into five modules that all configure and compile. No app behaviour changes yet.

**Files:**
- Modify: `gradle/libs.versions.toml` (full rewrite)
- Modify: `settings.gradle.kts:26-27`
- Modify: `build.gradle.kts` (root, lines 2-5)
- Create: `domain/build.gradle.kts`
- Create: `data/build.gradle.kts`
- Create: `core/player/build.gradle.kts`
- Create: `core/designsystem/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Delete: `app/src/test/java/io/github/mabrur/streamly/ExampleUnitTest.kt`
- Delete: `app/src/androidTest/java/io/github/mabrur/streamly/ExampleInstrumentedTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: version-catalog aliases used by every later task — `libs.hilt.android`, `libs.hilt.compiler`, `libs.ktor.client.core`, `libs.ktor.client.mock`, `libs.ktor.client.content.negotiation`, `libs.ktor.serialization.kotlinx.json`, `libs.kotlinx.serialization.json`, `libs.kotlinx.coroutines.core`, `libs.kotlinx.coroutines.test`, `libs.androidx.datastore.preferences`, `libs.turbine`, `libs.javax.inject`. Gradle module paths `:domain`, `:data`, `:core:player`, `:core:designsystem`.

**Critical context:** AGP 9.3.1 provides **built-in Kotlin support**. The existing root build file applies only `android-application` and `kotlin-compose` — there is no `org.jetbrains.kotlin.android` anywhere, and adding one will conflict. Android library modules therefore apply **only** `android-library` (+ `kotlin-compose` where they hold composables). Only `:domain`, which is not an Android module, needs `org.jetbrains.kotlin.jvm`.

- [ ] **Step 1: Rewrite the version catalog**

Replace the entire contents of `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.3.1"
kotlin = "2.2.10"
ksp = "2.2.10-2.0.2"
composeBom = "2026.02.01"

coreKtx = "1.19.0"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigation3 = "1.1.4"

hilt = "2.60.1"
hiltNavigationCompose = "1.4.0"
javaxInject = "1"

ktor = "3.5.1"
kotlinxSerialization = "1.11.0"
kotlinxCoroutines = "1.11.0"
datastore = "1.2.1"

media3 = "1.10.1"
coil = "3.5.0"

junit = "4.13.2"
turbine = "1.2.1"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
leakcanary = "2.14"

[libraries]
# --- AndroidX / Compose ---
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material3-window-size = { group = "androidx.compose.material3", name = "material3-window-size-class" }

# --- Navigation 3 ---
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }

# --- DI ---
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
javax-inject = { group = "javax.inject", name = "javax.inject", version.ref = "javaxInject" }

# --- Ktor ---
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# --- Kotlinx ---
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }

# --- Storage ---
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# --- Media3 ---
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
androidx-media3-datasource = { group = "androidx.media3", name = "media3-datasource", version.ref = "media3" }
androidx-media3-ui-compose = { group = "androidx.media3", name = "media3-ui-compose", version.ref = "media3" }

# --- Images ---
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# --- Test ---
junit = { group = "junit", name = "junit", version.ref = "junit" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
leakcanary-android = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 2: Register the modules and root plugin aliases**

Replace line 26-27 of `settings.gradle.kts` (the `include(":app")` line) with:

```kotlin
rootProject.name = "streamly"
include(":app")
include(":domain")
include(":data")
include(":core:player")
include(":core:designsystem")
```

Replace the whole of the root `build.gradle.kts`:

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 3: Create the four new module build files**

`domain/build.gradle.kts` — note there is **no** Android plugin here, by design:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

`data/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.mabrur.streamly.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.mock)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

`core/player/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.mabrur.streamly.core.player"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

`core/designsystem/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.mabrur.streamly.core.designsystem"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 4: Update the app build file**

Replace the whole of `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.mabrur.streamly"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.mabrur.streamly"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:player"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)
}
```

- [ ] **Step 5: Delete the template test stubs and create source directories**

```bash
rm app/src/test/java/io/github/mabrur/streamly/ExampleUnitTest.kt
rm app/src/androidTest/java/io/github/mabrur/streamly/ExampleInstrumentedTest.kt
mkdir -p domain/src/main/java/io/github/mabrur/streamly/domain
mkdir -p domain/src/test/java/io/github/mabrur/streamly/domain
mkdir -p data/src/main/java/io/github/mabrur/streamly/data
mkdir -p data/src/test/java/io/github/mabrur/streamly/data
mkdir -p data/src/main/assets
mkdir -p core/player/src/main/java/io/github/mabrur/streamly/core/player
mkdir -p core/designsystem/src/main/java/io/github/mabrur/streamly/core/designsystem
```

- [ ] **Step 6: Verify the whole project configures and compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

If it fails with a Kotlin-plugin conflict, the cause is applying `org.jetbrains.kotlin.android` somewhere — remove it; AGP 9.3.1 supplies Kotlin for Android modules.

Then prove the domain module is Android-free:

Run: `./gradlew :domain:dependencies --configuration compileClasspath`
Expected: only `kotlinx-coroutines-core`, `javax.inject`, and Kotlin stdlib. **Zero `androidx.*` or `com.android.*` entries.** If any appear, a dependency was added to the wrong module — fix before continuing.

- [ ] **Step 7: Commit**

The repo has `commit.template` configured, which supplies the `Co-authored-by` trailer. Using `git commit -m` **bypasses the template**, so pass the trailer explicitly with a second `-m`:

```bash
git add gradle/libs.versions.toml settings.gradle.kts build.gradle.kts \
        app/build.gradle.kts domain/build.gradle.kts data/build.gradle.kts \
        core/player/build.gradle.kts core/designsystem/build.gradle.kts
git add -A app/src/test app/src/androidTest
git commit -m "chore(build): split into :app/:domain/:data/:core modules" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 2: Hilt application bootstrap

Makes the Hilt graph exist and compile. Nothing is injected yet — this task proves the annotation processor is wired before any real dependency relies on it.

**Files:**
- Create: `app/src/main/java/io/github/mabrur/streamly/StreamlyApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`

**Interfaces:**
- Consumes: `libs.hilt.android`, `libs.hilt.compiler` from Task 1.
- Produces: a live Hilt `SingletonComponent`. Every `@Module @InstallIn(SingletonComponent::class)` in Tasks 6–8 binds into it. `MainActivity` becomes an `@AndroidEntryPoint`, which is required before any `hiltViewModel()` call in later plans.

- [ ] **Step 1: Create the Application class**

Create `app/src/main/java/io/github/mabrur/streamly/StreamlyApplication.kt`:

```kotlin
package io.github.mabrur.streamly

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreamlyApplication : Application()
```

- [ ] **Step 2: Register it in the manifest**

In `app/src/main/AndroidManifest.xml`, add `android:name=".StreamlyApplication"` to the `<application>` tag. The tag should begin:

```xml
    <application
        android:name=".StreamlyApplication"
        android:allowBackup="true"
```

Leave every other existing attribute on that tag untouched.

- [ ] **Step 3: Annotate MainActivity**

In `app/src/main/java/io/github/mabrur/streamly/MainActivity.kt`, add the import and the annotation:

```kotlin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
```

Change nothing else in the file.

- [ ] **Step 4: Verify Hilt code generation succeeds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Hilt's KSP processor now runs; a failure here means the `hilt` plugin or `ksp(libs.hilt.compiler)` line is missing from `app/build.gradle.kts`.

Confirm generated output exists:

Run: `ls app/build/generated/ksp/debug/java/io/github/mabrur/streamly/`
Expected: generated Hilt files including `StreamlyApplication_GeneratedInjector.java`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mabrur/streamly/StreamlyApplication.kt \
        app/src/main/java/io/github/mabrur/streamly/MainActivity.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(di): add Hilt application bootstrap" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 3: Domain models and error hierarchy

Pure Kotlin value types with no behaviour beyond one computed helper. The helper is what the test targets.

**Files:**
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/Category.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/Video.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/Short.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/UserProfile.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/HomeFeed.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/Session.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/model/DownloadItem.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/error/AppError.kt`
- Test: `domain/src/test/java/io/github/mabrur/streamly/domain/model/HomeFeedTest.kt`

**Interfaces:**
- Consumes: Task 1's `:domain` module.
- Produces: `Video(id, title, channelName, channelAvatarUrl, thumbnailUrl, hlsUrl, durationMs, viewCount, publishedAtEpochSeconds, category)`; `Short(id, title, channelName, hlsUrl, likeCount, commentCount)`; `Category(name)` value class with `Category.All`; `UserProfile(name, email, avatarUrl)`; `HomeFeed(categories, videos)` with `fun filteredBy(Category): HomeFeed`; `Session(userId, displayName, email, isGuest)`; `SessionState.{Unknown, SignedOut, SignedIn(session)}`; `DownloadItem(videoId, title, thumbnailUrl, status, bytesDownloaded)`; `DownloadStatus.{Queued, InProgress(percent), Completed, Failed, Removing}`; `AppError.{Network, NotFound, Storage, Unknown(detail)}` — a sealed `Exception` hierarchy.

- [ ] **Step 1: Write the failing test**

Create `domain/src/test/java/io/github/mabrur/streamly/domain/model/HomeFeedTest.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedTest {

    private fun video(id: String, category: String) = Video(
        id = id,
        title = "Title $id",
        channelName = "Channel",
        channelAvatarUrl = "https://example.com/a.png",
        thumbnailUrl = "https://example.com/t.png",
        hlsUrl = "https://example.com/$id.m3u8",
        durationMs = 60_000L,
        viewCount = 100L,
        publishedAtEpochSeconds = 1_700_000_000L,
        category = category,
    )

    private val feed = HomeFeed(
        categories = listOf(Category("Music"), Category("Gaming")),
        videos = listOf(video("a", "Music"), video("b", "Gaming"), video("c", "Music")),
    )

    @Test
    fun `filteredBy All returns every video`() {
        assertEquals(3, feed.filteredBy(Category.All).videos.size)
    }

    @Test
    fun `filteredBy a category returns only matching videos`() {
        val result = feed.filteredBy(Category("Music"))
        assertEquals(listOf("a", "c"), result.videos.map { it.id })
    }

    @Test
    fun `filteredBy preserves the full category list`() {
        val result = feed.filteredBy(Category("Music"))
        assertEquals(feed.categories, result.categories)
    }

    @Test
    fun `filteredBy an unknown category returns no videos`() {
        assertEquals(0, feed.filteredBy(Category("Cooking")).videos.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :domain:test`
Expected: FAIL — compilation error, `Unresolved reference: Video` / `HomeFeed` / `Category`.

- [ ] **Step 3: Write the models**

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/Category.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

@JvmInline
value class Category(val name: String) {
    companion object {
        val All = Category("All")
    }
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/Video.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    val hlsUrl: String,
    val durationMs: Long,
    val viewCount: Long,
    val publishedAtEpochSeconds: Long,
    val category: String,
)
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/Short.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

data class Short(
    val id: String,
    val title: String,
    val channelName: String,
    val hlsUrl: String,
    val likeCount: Long,
    val commentCount: Long,
)
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/UserProfile.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

data class UserProfile(
    val name: String,
    val email: String,
    val avatarUrl: String,
)
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/HomeFeed.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

data class HomeFeed(
    val categories: List<Category>,
    val videos: List<Video>,
) {
    fun filteredBy(category: Category): HomeFeed =
        if (category == Category.All) this
        else copy(videos = videos.filter { it.category == category.name })
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/Session.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

data class Session(
    val userId: String,
    val displayName: String,
    val email: String,
    val isGuest: Boolean,
)

sealed interface SessionState {
    /** Persisted state has not been read yet. Hold the splash while this is current. */
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/model/DownloadItem.kt`:

```kotlin
package io.github.mabrur.streamly.domain.model

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class InProgress(val percent: Float) : DownloadStatus
    data object Completed : DownloadStatus
    data object Failed : DownloadStatus
    data object Removing : DownloadStatus
}

data class DownloadItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
)
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/error/AppError.kt`:

```kotlin
package io.github.mabrur.streamly.domain.error

/**
 * Sealed error hierarchy for everything the UI must render.
 *
 * Extends [Exception] so repositories can return [Result], keeping the stdlib
 * combinators, while UiState still holds a sealed type rather than a raw String.
 */
sealed class AppError(message: String) : Exception(message) {
    data object Network : AppError("network unavailable")
    data object NotFound : AppError("resource not found")
    data object Storage : AppError("local storage failure")
    data class Unknown(val detail: String) : AppError(detail)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :domain:test`
Expected: PASS — 4 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/io/github/mabrur/streamly/domain/model \
        domain/src/main/java/io/github/mabrur/streamly/domain/error \
        domain/src/test/java/io/github/mabrur/streamly/domain/model
git commit -m "feat(domain): models and sealed AppError hierarchy" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 4: Repository interfaces and use cases

Defines the contracts `:data` will implement and the ViewModels will consume.

**Files:**
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/repository/CatalogRepository.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/repository/SessionRepository.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/repository/DownloadRepository.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetHomeFeedUseCase.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetShortsUseCase.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetVideoDetailUseCase.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetRelatedVideosUseCase.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/ObserveSessionUseCase.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/SignInUseCase.kt`
- Create: `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/SignOutUseCase.kt`
- Test: `domain/src/test/java/io/github/mabrur/streamly/domain/usecase/GetHomeFeedUseCaseTest.kt`

**Interfaces:**
- Consumes: every model from Task 3.
- Produces:
  - `interface CatalogRepository` — `suspend fun getHomeFeed(): Result<HomeFeed>`, `getShorts(): Result<List<Short>>`, `getVideo(id: String): Result<Video>`, `getRelated(id: String): Result<List<Video>>`, `getProfile(): Result<UserProfile>`
  - `interface SessionRepository` — `val state: Flow<SessionState>`, `suspend fun signIn(session: Session)`, `suspend fun signOut()`
  - `interface DownloadRepository` — `val downloads: Flow<List<DownloadItem>>`, `suspend fun download(video: Video)`, `suspend fun remove(videoId: String)`. **Declared here, implemented in the Downloads plan.** The Player plan's download button binds against this interface, so it must exist before that plan runs. No Hilt binding is provided in this plan — nothing injects it yet, so the graph stays valid.
  - `GetHomeFeedUseCase(repo)` — `suspend operator fun invoke(category: Category = Category.All): Result<HomeFeed>`
  - `GetShortsUseCase`, `GetVideoDetailUseCase`, `GetRelatedVideosUseCase`, `SignInUseCase`, `SignOutUseCase` — all `suspend operator fun invoke(...)`
  - `ObserveSessionUseCase` — `operator fun invoke(): Flow<SessionState>` (not suspend)

- [ ] **Step 1: Write the failing test**

Create `domain/src/test/java/io/github/mabrur/streamly/domain/usecase/GetHomeFeedUseCaseTest.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun video(id: String, category: String) = Video(
    id = id,
    title = "Title $id",
    channelName = "Channel",
    channelAvatarUrl = "https://example.com/a.png",
    thumbnailUrl = "https://example.com/t.png",
    hlsUrl = "https://example.com/$id.m3u8",
    durationMs = 60_000L,
    viewCount = 100L,
    publishedAtEpochSeconds = 1_700_000_000L,
    category = category,
)

private class FakeCatalogRepository(
    private val result: Result<HomeFeed>,
) : CatalogRepository {
    override suspend fun getHomeFeed(): Result<HomeFeed> = result
    override suspend fun getShorts(): Result<List<Short>> = Result.success(emptyList())
    override suspend fun getVideo(id: String): Result<Video> = Result.failure(AppError.NotFound)
    override suspend fun getRelated(id: String): Result<List<Video>> = Result.success(emptyList())
    override suspend fun getProfile(): Result<UserProfile> = Result.failure(AppError.NotFound)
}

class GetHomeFeedUseCaseTest {

    private val feed = HomeFeed(
        categories = listOf(Category("Music"), Category("Gaming")),
        videos = listOf(video("a", "Music"), video("b", "Gaming")),
    )

    @Test
    fun `returns the full feed for Category All`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.success(feed)))

        val result = useCase(Category.All)

        assertEquals(2, result.getOrThrow().videos.size)
    }

    @Test
    fun `filters the feed by the requested category`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.success(feed)))

        val result = useCase(Category("Gaming"))

        assertEquals(listOf("b"), result.getOrThrow().videos.map { it.id })
    }

    @Test
    fun `defaults to Category All when no category is given`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.success(feed)))

        val result = useCase()

        assertEquals(2, result.getOrThrow().videos.size)
    }

    @Test
    fun `propagates repository failure unchanged`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.failure(AppError.Network)))

        val result = useCase(Category.All)

        assertTrue(result.isFailure)
        assertEquals(AppError.Network, result.exceptionOrNull())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :domain:test --tests '*GetHomeFeedUseCaseTest'`
Expected: FAIL — `Unresolved reference: CatalogRepository` / `GetHomeFeedUseCase`.

- [ ] **Step 3: Write the interfaces and use cases**

Create `domain/src/main/java/io/github/mabrur/streamly/domain/repository/CatalogRepository.kt`:

```kotlin
package io.github.mabrur.streamly.domain.repository

import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video

interface CatalogRepository {
    suspend fun getHomeFeed(): Result<HomeFeed>
    suspend fun getShorts(): Result<List<Short>>
    suspend fun getVideo(id: String): Result<Video>
    suspend fun getRelated(id: String): Result<List<Video>>
    suspend fun getProfile(): Result<UserProfile>
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/repository/SessionRepository.kt`:

```kotlin
package io.github.mabrur.streamly.domain.repository

import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val state: Flow<SessionState>
    suspend fun signIn(session: Session)
    suspend fun signOut()
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/repository/DownloadRepository.kt`. There is
no implementation in this plan — the Downloads plan provides it. It is declared now so the
Player plan's download action has a domain contract to bind against:

```kotlin
package io.github.mabrur.streamly.domain.repository

import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    /** Emits the current download set whenever any download changes state. */
    val downloads: Flow<List<DownloadItem>>

    suspend fun download(video: Video)

    suspend fun remove(videoId: String)
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetHomeFeedUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetHomeFeedUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(category: Category = Category.All): Result<HomeFeed> =
        repository.getHomeFeed().map { it.filteredBy(category) }
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetShortsUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetShortsUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(): Result<List<Short>> = repository.getShorts()
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetVideoDetailUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetVideoDetailUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(videoId: String): Result<Video> = repository.getVideo(videoId)
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/GetRelatedVideosUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetRelatedVideosUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(videoId: String): Result<List<Video>> =
        repository.getRelated(videoId)
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/ObserveSessionUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    operator fun invoke(): Flow<SessionState> = repository.state
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/SignInUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    suspend operator fun invoke(session: Session) = repository.signIn(session)
}
```

Create `domain/src/main/java/io/github/mabrur/streamly/domain/usecase/SignOutUseCase.kt`:

```kotlin
package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    suspend operator fun invoke() = repository.signOut()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :domain:test`
Expected: PASS — 8 tests total (4 from Task 3, 4 here), `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/io/github/mabrur/streamly/domain/repository \
        domain/src/main/java/io/github/mabrur/streamly/domain/usecase \
        domain/src/test/java/io/github/mabrur/streamly/domain/usecase
git commit -m "feat(domain): repository contracts and use cases" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 5: Seed catalog, DTOs, and mappers

The catalog is authored here and the DTO→domain mapping is test-driven.

**Files:**
- Create: `data/src/main/assets/catalog.json`
- Create: `data/src/main/java/io/github/mabrur/streamly/data/remote/dto/CatalogDto.kt`
- Create: `data/src/main/java/io/github/mabrur/streamly/data/remote/mapper/CatalogMappers.kt`
- Test: `data/src/test/java/io/github/mabrur/streamly/data/remote/mapper/CatalogMappersTest.kt`

**Interfaces:**
- Consumes: `Video`, `Short`, `Category`, `UserProfile`, `HomeFeed` from Task 3.
- Produces: `CatalogDto(categories: List<String>, videos: List<VideoDto>, shorts: List<ShortDto>, profile: ProfileDto)`; extension functions `CatalogDto.toHomeFeed(): HomeFeed`, `CatalogDto.toShorts(): List<Short>`, `CatalogDto.toProfile(): UserProfile`, `VideoDto.toDomain(): Video`. **All are `internal` to `:data`.**

**HLS source note:** the three stream families below are the PRD's recommended public test streams. Before committing, task Step 1 requires confirming each URL returns HTTP 200 — a dead stream discovered in Phase 6 costs far more than checking now.

- [ ] **Step 1: Verify the HLS URLs are live**

```bash
for u in \
  "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" \
  "https://test-streams.mux.dev/tos_ismc/main.m3u8" \
  "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8" \
; do echo -n "$u -> "; curl -s -o /dev/null -w "%{http_code}\n" -L --max-time 15 "$u"; done
```

Expected: `200` for all three. If any returns non-200, substitute another public HLS test stream and record the substitution in the commit message.

- [ ] **Step 2: Write the seed catalog**

Create `data/src/main/assets/catalog.json`. Twelve videos across four categories, six shorts, and a profile:

```json
{
  "categories": ["All", "Music", "Gaming", "Tech", "Nature"],
  "videos": [
    { "id": "v01", "title": "Mountain Roads at Golden Hour", "channelName": "Wander Lens", "channelAvatarUrl": "https://i.pravatar.cc/150?img=11", "thumbnailUrl": "https://picsum.photos/seed/v01/640/360", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "durationMs": 596000, "viewCount": 1284000, "publishedAtEpochSeconds": 1750000000, "category": "Nature" },
    { "id": "v02", "title": "Tears of Steel — Full Short Film", "channelName": "Blender Studio", "channelAvatarUrl": "https://i.pravatar.cc/150?img=12", "thumbnailUrl": "https://picsum.photos/seed/v02/640/360", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "durationMs": 734000, "viewCount": 8420000, "publishedAtEpochSeconds": 1740000000, "category": "Tech" },
    { "id": "v03", "title": "Advanced Streaming, Explained", "channelName": "Bitrate Weekly", "channelAvatarUrl": "https://i.pravatar.cc/150?img=13", "thumbnailUrl": "https://picsum.photos/seed/v03/640/360", "hlsUrl": "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8", "durationMs": 1800000, "viewCount": 342000, "publishedAtEpochSeconds": 1745000000, "category": "Tech" },
    { "id": "v04", "title": "Synthwave Drive — One Hour Mix", "channelName": "Neon Grid", "channelAvatarUrl": "https://i.pravatar.cc/150?img=14", "thumbnailUrl": "https://picsum.photos/seed/v04/640/360", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "durationMs": 3600000, "viewCount": 2210000, "publishedAtEpochSeconds": 1738000000, "category": "Music" },
    { "id": "v05", "title": "Speedrunning the Impossible Level", "channelName": "Frame Perfect", "channelAvatarUrl": "https://i.pravatar.cc/150?img=15", "thumbnailUrl": "https://picsum.photos/seed/v05/640/360", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "durationMs": 921000, "viewCount": 5600000, "publishedAtEpochSeconds": 1752000000, "category": "Gaming" },
    { "id": "v06", "title": "Building a Home Studio on a Budget", "channelName": "Neon Grid", "channelAvatarUrl": "https://i.pravatar.cc/150?img=14", "thumbnailUrl": "https://picsum.photos/seed/v06/640/360", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "durationMs": 1140000, "viewCount": 187000, "publishedAtEpochSeconds": 1749000000, "category": "Music" },
    { "id": "v07", "title": "Deep Forest Ambience", "channelName": "Wander Lens", "channelAvatarUrl": "https://i.pravatar.cc/150?img=11", "thumbnailUrl": "https://picsum.photos/seed/v07/640/360", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "durationMs": 2700000, "viewCount": 934000, "publishedAtEpochSeconds": 1735000000, "category": "Nature" },
    { "id": "v08", "title": "Every Controller, Ranked", "channelName": "Frame Perfect", "channelAvatarUrl": "https://i.pravatar.cc/150?img=15", "thumbnailUrl": "https://picsum.photos/seed/v08/640/360", "hlsUrl": "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8", "durationMs": 1020000, "viewCount": 1450000, "publishedAtEpochSeconds": 1751000000, "category": "Gaming" },
    { "id": "v09", "title": "What Adaptive Bitrate Actually Does", "channelName": "Bitrate Weekly", "channelAvatarUrl": "https://i.pravatar.cc/150?img=13", "thumbnailUrl": "https://picsum.photos/seed/v09/640/360", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "durationMs": 660000, "viewCount": 96000, "publishedAtEpochSeconds": 1753000000, "category": "Tech" },
    { "id": "v10", "title": "Coastal Timelapse Collection", "channelName": "Wander Lens", "channelAvatarUrl": "https://i.pravatar.cc/150?img=11", "thumbnailUrl": "https://picsum.photos/seed/v10/640/360", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "durationMs": 480000, "viewCount": 612000, "publishedAtEpochSeconds": 1747000000, "category": "Nature" },
    { "id": "v11", "title": "Lo-Fi Beats for Deep Work", "channelName": "Neon Grid", "channelAvatarUrl": "https://i.pravatar.cc/150?img=14", "thumbnailUrl": "https://picsum.photos/seed/v11/640/360", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "durationMs": 5400000, "viewCount": 3900000, "publishedAtEpochSeconds": 1730000000, "category": "Music" },
    { "id": "v12", "title": "Co-op Night — Full Playthrough", "channelName": "Frame Perfect", "channelAvatarUrl": "https://i.pravatar.cc/150?img=15", "thumbnailUrl": "https://picsum.photos/seed/v12/640/360", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "durationMs": 4200000, "viewCount": 745000, "publishedAtEpochSeconds": 1754000000, "category": "Gaming" }
  ],
  "shorts": [
    { "id": "s01", "title": "60 seconds of pure ridgeline", "channelName": "Wander Lens", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "likeCount": 48200, "commentCount": 1210 },
    { "id": "s02", "title": "This bassline goes hard", "channelName": "Neon Grid", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "likeCount": 91400, "commentCount": 3320 },
    { "id": "s03", "title": "Frame-perfect, first try", "channelName": "Frame Perfect", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "likeCount": 152000, "commentCount": 8800 },
    { "id": "s04", "title": "Buffering, visualised", "channelName": "Bitrate Weekly", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "likeCount": 12300, "commentCount": 410 },
    { "id": "s05", "title": "Tide coming in, sped up 400x", "channelName": "Wander Lens", "hlsUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "likeCount": 67800, "commentCount": 2040 },
    { "id": "s06", "title": "One take, no edits", "channelName": "Neon Grid", "hlsUrl": "https://test-streams.mux.dev/tos_ismc/main.m3u8", "likeCount": 33100, "commentCount": 970 }
  ],
  "profile": {
    "name": "Mabrur Chowdhury",
    "email": "mabrur@example.com",
    "avatarUrl": "https://i.pravatar.cc/150?img=68"
  }
}
```

- [ ] **Step 3: Write the failing mapper test**

Create `data/src/test/java/io/github/mabrur/streamly/data/remote/mapper/CatalogMappersTest.kt`:

```kotlin
package io.github.mabrur.streamly.data.remote.mapper

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import io.github.mabrur.streamly.domain.model.Category
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val payload = """
        {
          "categories": ["All", "Music"],
          "videos": [
            { "id": "v01", "title": "T1", "channelName": "C1", "channelAvatarUrl": "a1",
              "thumbnailUrl": "t1", "hlsUrl": "h1", "durationMs": 1000, "viewCount": 10,
              "publishedAtEpochSeconds": 1700000000, "category": "Music" }
          ],
          "shorts": [
            { "id": "s01", "title": "S1", "channelName": "C1", "hlsUrl": "sh1",
              "likeCount": 5, "commentCount": 2 }
          ],
          "profile": { "name": "N", "email": "e@x.com", "avatarUrl": "av" }
        }
    """.trimIndent()

    private fun parse() = json.decodeFromString<CatalogDto>(payload)

    @Test
    fun `maps categories into domain Category values`() {
        val feed = parse().toHomeFeed()
        assertEquals(listOf(Category("All"), Category("Music")), feed.categories)
    }

    @Test
    fun `maps every video field across the boundary`() {
        val video = parse().toHomeFeed().videos.single()

        assertEquals("v01", video.id)
        assertEquals("T1", video.title)
        assertEquals("C1", video.channelName)
        assertEquals("a1", video.channelAvatarUrl)
        assertEquals("t1", video.thumbnailUrl)
        assertEquals("h1", video.hlsUrl)
        assertEquals(1000L, video.durationMs)
        assertEquals(10L, video.viewCount)
        assertEquals(1_700_000_000L, video.publishedAtEpochSeconds)
        assertEquals("Music", video.category)
    }

    @Test
    fun `maps shorts across the boundary`() {
        val short = parse().toShorts().single()

        assertEquals("s01", short.id)
        assertEquals("S1", short.title)
        assertEquals("C1", short.channelName)
        assertEquals("sh1", short.hlsUrl)
        assertEquals(5L, short.likeCount)
        assertEquals(2L, short.commentCount)
    }

    @Test
    fun `maps the profile across the boundary`() {
        val profile = parse().toProfile()

        assertEquals("N", profile.name)
        assertEquals("e@x.com", profile.email)
        assertEquals("av", profile.avatarUrl)
    }

    @Test
    fun `malformed json fails to decode`() {
        assertThrows(Exception::class.java) {
            json.decodeFromString<CatalogDto>("{ \"categories\": ")
        }
    }

    @Test
    fun `missing required field fails to decode`() {
        val missingId = """
            { "categories": [], "shorts": [], "profile": { "name": "N", "email": "e", "avatarUrl": "a" },
              "videos": [ { "title": "T1", "channelName": "C1", "channelAvatarUrl": "a1",
                            "thumbnailUrl": "t1", "hlsUrl": "h1", "durationMs": 1000, "viewCount": 10,
                            "publishedAtEpochSeconds": 1700000000, "category": "Music" } ] }
        """.trimIndent()

        assertThrows(Exception::class.java) {
            json.decodeFromString<CatalogDto>(missingId)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests '*CatalogMappersTest'`
Expected: FAIL — `Unresolved reference: CatalogDto`.

- [ ] **Step 5: Write the DTOs**

Create `data/src/main/java/io/github/mabrur/streamly/data/remote/dto/CatalogDto.kt`:

```kotlin
package io.github.mabrur.streamly.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogDto(
    val categories: List<String>,
    val videos: List<VideoDto>,
    val shorts: List<ShortDto>,
    val profile: ProfileDto,
)

@Serializable
internal data class VideoDto(
    val id: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    val hlsUrl: String,
    val durationMs: Long,
    val viewCount: Long,
    val publishedAtEpochSeconds: Long,
    val category: String,
)

@Serializable
internal data class ShortDto(
    val id: String,
    val title: String,
    val channelName: String,
    val hlsUrl: String,
    val likeCount: Long,
    val commentCount: Long,
)

@Serializable
internal data class ProfileDto(
    val name: String,
    val email: String,
    val avatarUrl: String,
)
```

- [ ] **Step 6: Write the mappers**

Create `data/src/main/java/io/github/mabrur/streamly/data/remote/mapper/CatalogMappers.kt`:

```kotlin
package io.github.mabrur.streamly.data.remote.mapper

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import io.github.mabrur.streamly.data.remote.dto.ProfileDto
import io.github.mabrur.streamly.data.remote.dto.ShortDto
import io.github.mabrur.streamly.data.remote.dto.VideoDto
import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video

internal fun VideoDto.toDomain(): Video = Video(
    id = id,
    title = title,
    channelName = channelName,
    channelAvatarUrl = channelAvatarUrl,
    thumbnailUrl = thumbnailUrl,
    hlsUrl = hlsUrl,
    durationMs = durationMs,
    viewCount = viewCount,
    publishedAtEpochSeconds = publishedAtEpochSeconds,
    category = category,
)

internal fun ShortDto.toDomain(): Short = Short(
    id = id,
    title = title,
    channelName = channelName,
    hlsUrl = hlsUrl,
    likeCount = likeCount,
    commentCount = commentCount,
)

internal fun ProfileDto.toDomain(): UserProfile = UserProfile(
    name = name,
    email = email,
    avatarUrl = avatarUrl,
)

internal fun CatalogDto.toHomeFeed(): HomeFeed = HomeFeed(
    categories = categories.map(::Category),
    videos = videos.map(VideoDto::toDomain),
)

internal fun CatalogDto.toShorts(): List<Short> = shorts.map(ShortDto::toDomain)

internal fun CatalogDto.toProfile(): UserProfile = profile.toDomain()
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests '*CatalogMappersTest'`
Expected: PASS — 6 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add data/src/main/assets/catalog.json \
        data/src/main/java/io/github/mabrur/streamly/data/remote \
        data/src/test/java/io/github/mabrur/streamly/data/remote
git commit -m "feat(data): seed HLS catalog with DTOs and domain mappers" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 6: Ktor MockEngine and CatalogRepository

Wires the real Ktor pipeline against a mocked transport, then implements the repository on top.

**Files:**
- Create: `data/src/main/java/io/github/mabrur/streamly/data/remote/CatalogApi.kt`
- Create: `data/src/main/java/io/github/mabrur/streamly/data/remote/MockCatalogEngine.kt`
- Create: `data/src/main/java/io/github/mabrur/streamly/data/repository/CatalogRepositoryImpl.kt`
- Test: `data/src/test/java/io/github/mabrur/streamly/data/repository/CatalogRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `CatalogDto` + mappers (Task 5), `CatalogRepository` (Task 4), `AppError` (Task 3).
- Produces:
  - `internal class CatalogApi(private val client: HttpClient)` — `suspend fun fetchCatalog(): CatalogDto`
  - `internal fun mockCatalogEngine(readAsset: () -> String, failEveryNth: Int, latencyMillis: LongRange): MockEngine`
  - `internal class CatalogRepositoryImpl(api: CatalogApi) : CatalogRepository`
  - Constant `internal const val CATALOG_URL = "https://streamly.local/catalog.json"`

**Cancellation footgun:** `runCatching` catches `CancellationException` and silently breaks structured concurrency. The `catching` helper below rethrows it first. **Do not replace it with `runCatching`.**

- [ ] **Step 1: Write the failing test**

Create `data/src/test/java/io/github/mabrur/streamly/data/repository/CatalogRepositoryImplTest.kt`:

```kotlin
package io.github.mabrur.streamly.data.repository

import io.github.mabrur.streamly.data.remote.CATALOG_URL
import io.github.mabrur.streamly.data.remote.CatalogApi
import io.github.mabrur.streamly.domain.error.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryImplTest {

    private val payload = """
        {
          "categories": ["All", "Music"],
          "videos": [
            { "id": "v01", "title": "T1", "channelName": "C1", "channelAvatarUrl": "a1",
              "thumbnailUrl": "t1", "hlsUrl": "h1", "durationMs": 1000, "viewCount": 10,
              "publishedAtEpochSeconds": 1700000000, "category": "Music" },
            { "id": "v02", "title": "T2", "channelName": "C2", "channelAvatarUrl": "a2",
              "thumbnailUrl": "t2", "hlsUrl": "h2", "durationMs": 2000, "viewCount": 20,
              "publishedAtEpochSeconds": 1700000001, "category": "Gaming" }
          ],
          "shorts": [
            { "id": "s01", "title": "S1", "channelName": "C1", "hlsUrl": "sh1",
              "likeCount": 5, "commentCount": 2 }
          ],
          "profile": { "name": "N", "email": "e@x.com", "avatarUrl": "av" }
        }
    """.trimIndent()

    private fun repository(engine: MockEngine): CatalogRepositoryImpl {
        val client = HttpClient(engine) {
            // Ktor does NOT throw on non-2xx by default; without this a 503 would be
            // handed to the deserializer and surface as AppError.Unknown, not Network.
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return CatalogRepositoryImpl(CatalogApi(client))
    }

    private fun okEngine() = MockEngine {
        respond(
            content = payload,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @Test
    fun `getHomeFeed returns mapped domain videos`() = runTest {
        val result = repository(okEngine()).getHomeFeed()

        assertEquals(listOf("v01", "v02"), result.getOrThrow().videos.map { it.id })
    }

    @Test
    fun `getShorts returns mapped domain shorts`() = runTest {
        val result = repository(okEngine()).getShorts()

        assertEquals(listOf("s01"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `getVideo returns the requested video`() = runTest {
        val result = repository(okEngine()).getVideo("v02")

        assertEquals("T2", result.getOrThrow().title)
    }

    @Test
    fun `getVideo returns NotFound for an unknown id`() = runTest {
        val result = repository(okEngine()).getVideo("nope")

        assertTrue(result.isFailure)
        assertEquals(AppError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `getRelated excludes the requested video itself`() = runTest {
        val result = repository(okEngine()).getRelated("v01")

        assertEquals(listOf("v02"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `getProfile returns the mapped profile`() = runTest {
        val result = repository(okEngine()).getProfile()

        assertEquals("e@x.com", result.getOrThrow().email)
    }

    @Test
    fun `transport failure surfaces as AppError Network`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

        val result = repository(engine).getHomeFeed()

        assertTrue(result.isFailure)
        assertEquals(AppError.Network, result.exceptionOrNull())
    }

    @Test
    fun `malformed payload surfaces as AppError Unknown`() = runTest {
        val engine = MockEngine {
            respond(
                content = "{ not json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = repository(engine).getHomeFeed()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Unknown)
    }

    @Test
    fun `catalog url is requested exactly once per call`() = runTest {
        val engine = okEngine()

        repository(engine).getHomeFeed()

        assertEquals(1, engine.requestHistory.size)
        assertEquals(CATALOG_URL, engine.requestHistory.first().url.toString())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests '*CatalogRepositoryImplTest'`
Expected: FAIL — `Unresolved reference: CatalogApi` / `CatalogRepositoryImpl` / `CATALOG_URL`.

- [ ] **Step 3: Write the API client**

Create `data/src/main/java/io/github/mabrur/streamly/data/remote/CatalogApi.kt`:

```kotlin
package io.github.mabrur.streamly.data.remote

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal const val CATALOG_URL = "https://streamly.local/catalog.json"

internal class CatalogApi(private val client: HttpClient) {
    suspend fun fetchCatalog(): CatalogDto = client.get(CATALOG_URL).body()
}
```

- [ ] **Step 4: Write the mock engine**

Create `data/src/main/java/io/github/mabrur/streamly/data/remote/MockCatalogEngine.kt`:

```kotlin
package io.github.mabrur.streamly.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * MockEngine serving the bundled catalog.
 *
 * Only the catalog API is faked. HLS media is fetched over the real network by
 * Media3's own DataSource — otherwise there would be no genuine adaptive streaming
 * and nothing real to download.
 *
 * @param readAsset supplies the raw catalog.json contents.
 * @param failEveryNth every Nth request fails, so loading and error states are real
 *   behaviour rather than staged. Pass 0 to disable.
 * @param latencyMillis artificial delay range, so the loading state is observable.
 */
internal fun mockCatalogEngine(
    readAsset: () -> String,
    failEveryNth: Int = 8,
    latencyMillis: LongRange = 300L..600L,
): MockEngine {
    val counter = AtomicInteger(0)
    return MockEngine { request ->
        delay(Random.nextLong(latencyMillis.first, latencyMillis.last + 1))

        val n = counter.incrementAndGet()
        if (failEveryNth > 0 && n % failEveryNth == 0) {
            return@MockEngine respondError(HttpStatusCode.ServiceUnavailable)
        }

        when (request.url.toString()) {
            CATALOG_URL -> respond(
                content = readAsset(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )

            else -> respondError(HttpStatusCode.NotFound)
        }
    }
}
```

- [ ] **Step 5: Write the repository implementation**

Create `data/src/main/java/io/github/mabrur/streamly/data/repository/CatalogRepositoryImpl.kt`:

```kotlin
package io.github.mabrur.streamly.data.repository

import io.github.mabrur.streamly.data.remote.CatalogApi
import io.github.mabrur.streamly.data.remote.mapper.toHomeFeed
import io.github.mabrur.streamly.data.remote.mapper.toProfile
import io.github.mabrur.streamly.data.remote.mapper.toShorts
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal class CatalogRepositoryImpl @Inject constructor(
    private val api: CatalogApi,
) : CatalogRepository {

    override suspend fun getHomeFeed(): Result<HomeFeed> =
        catching { api.fetchCatalog().toHomeFeed() }

    override suspend fun getShorts(): Result<List<Short>> =
        catching { api.fetchCatalog().toShorts() }

    override suspend fun getVideo(id: String): Result<Video> =
        catching { api.fetchCatalog().toHomeFeed().videos }
            .mapCatching { videos ->
                videos.firstOrNull { it.id == id } ?: throw AppError.NotFound
            }

    override suspend fun getRelated(id: String): Result<List<Video>> =
        catching { api.fetchCatalog().toHomeFeed().videos.filterNot { it.id == id } }

    override suspend fun getProfile(): Result<UserProfile> =
        catching { api.fetchCatalog().toProfile() }
}

/**
 * Like [runCatching], but rethrows [CancellationException] so structured
 * concurrency is not silently broken. Do not replace this with runCatching.
 */
private inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: AppError) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(AppError.Network)
} catch (e: ResponseException) {
    Result.failure(AppError.Network)
} catch (e: Throwable) {
    Result.failure(AppError.Unknown(e.message ?: e::class.simpleName.orEmpty()))
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests '*CatalogRepositoryImplTest'`
Expected: PASS — 9 tests, `BUILD SUCCESSFUL`.

If `transport failure surfaces as AppError Network` fails with `AppError.Unknown`, the Ktor exception type for a non-2xx response differs from `ResponseException` in 3.5.1 — inspect the actual exception class in the failure message and add it to the `catching` helper's `Network` branch.

- [ ] **Step 7: Commit**

```bash
git add data/src/main/java/io/github/mabrur/streamly/data/remote/CatalogApi.kt \
        data/src/main/java/io/github/mabrur/streamly/data/remote/MockCatalogEngine.kt \
        data/src/main/java/io/github/mabrur/streamly/data/repository \
        data/src/test/java/io/github/mabrur/streamly/data/repository
git commit -m "feat(data): ktor mockengine catalog repository" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 7: DataStore session repository

Persists the session so returning users skip Onboarding.

**Files:**
- Create: `data/src/main/java/io/github/mabrur/streamly/data/repository/SessionRepositoryImpl.kt`
- Test: `data/src/test/java/io/github/mabrur/streamly/data/repository/SessionRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `SessionRepository`, `Session`, `SessionState` (Tasks 3–4).
- Produces: `internal class SessionRepositoryImpl(dataStore: DataStore<Preferences>) : SessionRepository`. Emits `SessionState.SignedOut` when no user id is stored, `SessionState.SignedIn` otherwise, and `SessionState.Unknown` **never** — `Unknown` is the ViewModel's pre-first-emission placeholder, not a persisted value.

- [ ] **Step 1: Write the failing test**

Create `data/src/test/java/io/github/mabrur/streamly/data/repository/SessionRepositoryImplTest.kt`:

```kotlin
package io.github.mabrur.streamly.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionRepositoryImplTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>

    private val session = Session(
        userId = "u1",
        displayName = "Ada",
        email = "ada@example.com",
        isGuest = false,
    )

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "streamly-test-${System.nanoTime()}")
        tempDir.mkdirs()
        dataStore = PreferenceDataStoreFactory.create(scope = TestScope()) {
            File(tempDir, "session.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `emits SignedOut when nothing is persisted`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)

        repository.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits SignedIn after signIn`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)
        repository.signIn(session)

        repository.state.test {
            val state = awaitItem()
            assertTrue(state is SessionState.SignedIn)
            assertEquals(session, (state as SessionState.SignedIn).session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `round-trips every session field`() = runTest {
        val guest = Session(userId = "g1", displayName = "Guest", email = "", isGuest = true)
        val repository = SessionRepositoryImpl(dataStore)
        repository.signIn(guest)

        repository.state.test {
            assertEquals(guest, (awaitItem() as SessionState.SignedIn).session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits SignedOut after signOut`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)
        repository.signIn(session)
        repository.signOut()

        repository.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state emits again when the session changes`() = runTest {
        val repository = SessionRepositoryImpl(dataStore)

        repository.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())

            repository.signIn(session)
            assertTrue(awaitItem() is SessionState.SignedIn)

            repository.signOut()
            assertEquals(SessionState.SignedOut, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests '*SessionRepositoryImplTest'`
Expected: FAIL — `Unresolved reference: SessionRepositoryImpl`.

- [ ] **Step 3: Write the implementation**

Create `data/src/main/java/io/github/mabrur/streamly/data/repository/SessionRepositoryImpl.kt`:

```kotlin
package io.github.mabrur.streamly.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SessionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionRepository {

    override val state: Flow<SessionState> = dataStore.data.map { prefs ->
        val userId = prefs[KeyUserId]
        if (userId.isNullOrEmpty()) {
            SessionState.SignedOut
        } else {
            SessionState.SignedIn(
                Session(
                    userId = userId,
                    displayName = prefs[KeyDisplayName].orEmpty(),
                    email = prefs[KeyEmail].orEmpty(),
                    isGuest = prefs[KeyIsGuest] ?: false,
                )
            )
        }
    }

    override suspend fun signIn(session: Session) {
        dataStore.edit { prefs ->
            prefs[KeyUserId] = session.userId
            prefs[KeyDisplayName] = session.displayName
            prefs[KeyEmail] = session.email
            prefs[KeyIsGuest] = session.isGuest
        }
    }

    override suspend fun signOut() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KeyUserId = stringPreferencesKey("user_id")
        val KeyDisplayName = stringPreferencesKey("display_name")
        val KeyEmail = stringPreferencesKey("email")
        val KeyIsGuest = booleanPreferencesKey("is_guest")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests '*SessionRepositoryImplTest'`
Expected: PASS — 5 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/io/github/mabrur/streamly/data/repository/SessionRepositoryImpl.kt \
        data/src/test/java/io/github/mabrur/streamly/data/repository/SessionRepositoryImplTest.kt
git commit -m "feat(data): datastore-backed session persistence" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 8: Hilt modules wiring `:data` into the graph

Binds the implementations to their domain interfaces so `:app` only ever sees the interfaces.

**Files:**
- Create: `data/src/main/java/io/github/mabrur/streamly/data/di/NetworkModule.kt`
- Create: `data/src/main/java/io/github/mabrur/streamly/data/di/SessionModule.kt`
- Create: `data/src/main/java/io/github/mabrur/streamly/data/di/RepositoryModule.kt`

**Interfaces:**
- Consumes: `CatalogApi`, `mockCatalogEngine`, `CatalogRepositoryImpl`, `SessionRepositoryImpl` (Tasks 6–7).
- Produces: injectable `CatalogRepository` and `SessionRepository` in `SingletonComponent`. Every use case from Task 4 becomes constructible by Hilt, which the ViewModels in later plans depend on.

- [ ] **Step 1: Write the network module**

Create `data/src/main/java/io/github/mabrur/streamly/data/di/NetworkModule.kt`:

```kotlin
package io.github.mabrur.streamly.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mabrur.streamly.data.remote.CatalogApi
import io.github.mabrur.streamly.data.remote.mockCatalogEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(
        @ApplicationContext context: Context,
    ): HttpClient {
        val engine = mockCatalogEngine(
            readAsset = {
                context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            },
        )
        return HttpClient(engine) {
            // Required: Ktor does not throw on non-2xx by default, so without this
            // the injected failures would reach the deserializer and be misreported
            // as AppError.Unknown instead of AppError.Network.
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Provides
    @Singleton
    fun provideCatalogApi(client: HttpClient): CatalogApi = CatalogApi(client)
}
```

- [ ] **Step 2: Write the session module**

Create `data/src/main/java/io/github/mabrur/streamly/data/di/SessionModule.kt`:

```kotlin
package io.github.mabrur.streamly.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal object SessionModule {

    @Provides
    @Singleton
    fun provideSessionDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("streamly_session") },
    )
}
```

- [ ] **Step 3: Write the repository binding module**

Create `data/src/main/java/io/github/mabrur/streamly/data/di/RepositoryModule.kt`:

```kotlin
package io.github.mabrur.streamly.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mabrur.streamly.data.repository.CatalogRepositoryImpl
import io.github.mabrur.streamly.data.repository.SessionRepositoryImpl
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
```

- [ ] **Step 4: Verify the graph resolves**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. A `MissingBinding` error here means a `@Provides`/`@Binds` is absent — read the Dagger error, which names the exact missing type.

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew :domain:test :data:testDebugUnitTest`
Expected: PASS — 28 tests total (4 + 4 domain, 6 + 9 + 5 data), `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify layering has not been violated**

```bash
echo "--- Ktor/DTO leaking out of :data (expect no output) ---"
grep -rn "io.ktor\|remote.dto" app/src core domain/src --include=*.kt || echo "clean"

echo "--- android imports in :domain (expect no output) ---"
grep -rn "^import android\|^import androidx" domain/src --include=*.kt || echo "clean"
```

Expected: `clean` for both. Any hit is a layering violation that must be fixed before committing.

- [ ] **Step 7: Commit**

```bash
git add data/src/main/java/io/github/mabrur/streamly/data/di
git commit -m "feat(di): bind data repositories to domain interfaces" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Task 9: Decision records and AGENTS.md amendment proposal

Closes the documentation obligation this plan created. **This task writes `docs/decisions.md` but does NOT edit `AGENTS.md`** — that file requires the owner's sign-off.

**Files:**
- Create: `docs/decisions.md`
- Do **not** modify: `AGENTS.md` (or its symlinks)

**Interfaces:**
- Consumes: nothing.
- Produces: `docs/decisions.md` with D-001 … D-006, append-only from here. Later plans append; they never edit.

- [ ] **Step 1: Write the decision records**

Create `docs/decisions.md`:

```markdown
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
```

- [ ] **Step 2: Commit the decision records**

```bash
git add docs/decisions.md
git commit -m "docs(decisions): record D-001 through D-006" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

- [ ] **Step 3: Surface the AGENTS.md amendment for sign-off — do not apply it**

`AGENTS.md` now contains two statements that this plan has made false. Present this
diff to the repo owner and **wait for explicit approval before editing**:

**Under "## Architecture":**

> Current: `Single Gradle module :app, strict package layering:`
> Proposed: `Five Gradle modules — :app, :domain (pure kotlin("jvm")), :data, :core:player, :core:designsystem. Features are packages inside :app, not modules. See docs/decisions.md D-001.`

**Under "## Architecture", the catalog line:**

> Current: `The bundled JSON catalog lives at app/src/main/assets/catalog.json.`
> Proposed: `The bundled JSON catalog lives at data/src/main/assets/catalog.json.`

Report to the owner: *"Two AGENTS.md rules are now false — the module count and the
catalog path. Proposed replacements above. I have not edited the file. Approve and I
will apply them in the same commit as the change they describe."*

---

## Definition of done for this plan

- [ ] `./gradlew :app:compileDebugKotlin` — `BUILD SUCCESSFUL`
- [ ] `./gradlew :domain:test :data:testDebugUnitTest` — 28 tests, all passing
- [ ] `./gradlew :domain:dependencies --configuration compileClasspath` — zero `androidx.*` / `com.android.*`
- [ ] `grep -rn "io.ktor\|remote.dto" app/src core domain/src --include=*.kt` — no output
- [ ] `docs/decisions.md` exists with D-001 … D-006
- [ ] `AGENTS.md` amendment proposed and **awaiting owner sign-off** — not applied

**Not in this plan** (each gets its own): Nav3 shell and design system · Home feed · Player · Shorts · Downloads · Onboarding/Profile · ship.

**Next plan is blocked on research:** the Nav3 1.1.4 API must be read from current
docs before its plan is written. Do not infer it from Navigation 2 patterns.
