# Streamly Downloads + Offline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real Media3 offline downloads with genuine progress, a Downloads screen with remove support, and completed items that actually play in airplane mode.

**Architecture:** `DownloadManager` binds to the **same `SimpleCache` singleton** the Player plan created — one cache, two consumers. Downloads write through the `@Named("upstream")` factory; playback reads through the read-only `CacheDataSource.Factory`. `DownloadRepositoryImpl` lives in `:core:player` because it needs Media3, and implements the `:domain` interface declared back in the foundation plan.

**Tech Stack:** Media3 1.10.1 offline module · Hilt 2.60.1 · coroutines `callbackFlow`

**Prerequisites:** all four preceding plans complete. This plan is worth more of the grade than any other single phase — the PRD's demo ends with the offline sequence.

## Global Constraints

All prior constraints apply. Downloads-specific, quoted from the project rules:

- Downloads use Media3's offline module (`DownloadService` + `DownloadManager`). **No hand-rolled downloads, no fake progress.**
- `DownloadHelper` → `DownloadRequest` → `DownloadService.sendAddDownload`.
- Dedicated `SimpleCache` with `NoOpCacheEvictor` + `StandaloneDatabaseProvider`.
- Playback of completed downloads goes through `CacheDataSource.Factory` on the **same cache** and must work offline.
- Progress comes from observing `DownloadManager` into a `Flow`.

---

## Verified API reference

Read from `media3-exoplayer-1.10.1.aar` via `javap` on 2026-07-27. **Ground truth.**

| Symbol | Verified signature |
|---|---|
| `DownloadManager` | `DownloadManager(Context, DatabaseProvider, Cache, DataSource.Factory, Executor)` |
| `DownloadManager.Listener` | `onDownloadChanged(manager, download, finalException)`, `onDownloadRemoved(manager, download)`, `onInitialized(manager)`, `onIdle(manager)` — all `default` methods |
| `DownloadManager` accessors | `.currentDownloads: List<Download>`, `.downloadIndex: DownloadIndex`, `.addListener(l)`, `.resumeDownloads()` |
| `Download` states | `STATE_QUEUED`, `STATE_STOPPED`, `STATE_DOWNLOADING`, `STATE_COMPLETED`, `STATE_FAILED`, `STATE_REMOVING`, `STATE_RESTARTING` — **seven, not five** |
| `Download` fields | `.request`, `.state`, `.contentLength`, `getBytesDownloaded()`, `getPercentDownloaded()` |
| `DownloadHelper.Factory` | `.setDataSourceFactory(f).create(mediaItem): DownloadHelper` |
| `DownloadHelper` | `.prepare(Callback)`, `.getDownloadRequest(id: String, data: ByteArray?)`, `.release()` |
| `DownloadService` ctor | `DownloadService(notificationId, updateInterval, channelId, channelNameResId, channelDescriptionResId)` — creates the channel for you |
| `DownloadService` abstract | `getDownloadManager()`, `getScheduler()`, `getForegroundNotification(List<Download>, Int)` |
| `DownloadService` statics | `sendAddDownload(context, clazz, request, foreground)`, `sendRemoveDownload(context, clazz, id, foreground)` |
| `DownloadNotificationHelper` | `(Context, channelId)`, `.buildProgressNotification(context, smallIcon, contentIntent, message, downloads, notMetRequirements)` |

**Two things that differ from what memory would suggest:**

1. There are **seven** download states. `STATE_STOPPED` and `STATE_RESTARTING` are easy to
   forget, and a non-exhaustive `when` on them is how a download silently renders as
   "unknown". The mapper test covers all seven.
2. `getPercentDownloaded()` returns `C.PERCENTAGE_UNSET` (**-1f**), not 0, when the content
   length is not yet known. Rendering that directly gives a progress bar that jumps
   backwards on start.

---

## File Structure

**`:core:player`** (`download/`)
- `DownloadStatusMapper.kt` — pure `Int` → `DownloadStatus`. The tested unit.
- `DownloadModule.kt` — `DownloadManager` bound to the existing cache.
- `StreamlyDownloadService.kt`
- `DownloadRepositoryImpl.kt` — implements the `:domain` interface. See D-009.

**`:app`** (`ui/downloads/`)
- `DownloadsContract.kt`, `DownloadsViewModel.kt`, `DownloadsScreen.kt`, `DownloadsRoute.kt`

---

## Task 1: Download status mapper

Named explicitly in the PRD as a test worth writing. Pure `Int` → sealed type, so it needs no
Android and no `Download` instance — building one would require `android.net.Uri`, which is
not available in plain JVM unit tests.

**Files:**
- Create: `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadStatusMapper.kt`
- Test: `core/player/src/test/java/io/github/mabrur/streamly/core/player/download/DownloadStatusMapperTest.kt`

**Interfaces:**
- Produces: `fun downloadStatusFor(state: Int, percentDownloaded: Float): DownloadStatus`. Consumed by `DownloadRepositoryImpl`.

- [x] **Step 1: Write the failing test**

Create `core/player/src/test/java/io/github/mabrur/streamly/core/player/download/DownloadStatusMapperTest.kt`:

```kotlin
package io.github.mabrur.streamly.core.player.download

import androidx.media3.exoplayer.offline.Download
import io.github.mabrur.streamly.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatusMapperTest {

    @Test
    fun `queued maps to Queued`() {
        assertEquals(DownloadStatus.Queued, downloadStatusFor(Download.STATE_QUEUED, 0f))
    }

    @Test
    fun `stopped maps to Queued`() {
        assertEquals(DownloadStatus.Queued, downloadStatusFor(Download.STATE_STOPPED, 12f))
    }

    @Test
    fun `restarting maps to Queued`() {
        assertEquals(DownloadStatus.Queued, downloadStatusFor(Download.STATE_RESTARTING, 30f))
    }

    @Test
    fun `downloading maps to InProgress with the reported percent`() {
        val status = downloadStatusFor(Download.STATE_DOWNLOADING, 42.5f)

        assertTrue(status is DownloadStatus.InProgress)
        assertEquals(42.5f, (status as DownloadStatus.InProgress).percent, 0.001f)
    }

    @Test
    fun `an unset percent is clamped to zero rather than going negative`() {
        // Media3 reports C.PERCENTAGE_UNSET (-1f) until the content length is known.
        val status = downloadStatusFor(Download.STATE_DOWNLOADING, -1f)

        assertEquals(0f, (status as DownloadStatus.InProgress).percent, 0.001f)
    }

    @Test
    fun `an over-range percent is clamped to one hundred`() {
        val status = downloadStatusFor(Download.STATE_DOWNLOADING, 140f)

        assertEquals(100f, (status as DownloadStatus.InProgress).percent, 0.001f)
    }

    @Test
    fun `completed maps to Completed`() {
        assertEquals(DownloadStatus.Completed, downloadStatusFor(Download.STATE_COMPLETED, 100f))
    }

    @Test
    fun `failed maps to Failed`() {
        assertEquals(DownloadStatus.Failed, downloadStatusFor(Download.STATE_FAILED, 70f))
    }

    @Test
    fun `removing maps to Removing`() {
        assertEquals(DownloadStatus.Removing, downloadStatusFor(Download.STATE_REMOVING, 100f))
    }

    @Test
    fun `an unrecognised state degrades to Failed rather than throwing`() {
        assertEquals(DownloadStatus.Failed, downloadStatusFor(Int.MIN_VALUE, 0f))
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:player:testDebugUnitTest --tests '*DownloadStatusMapperTest'`
Expected: FAIL — `Unresolved reference: downloadStatusFor`.

- [x] **Step 3: Write the mapper**

Create `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadStatusMapper.kt`:

```kotlin
@file:OptIn(UnstableApi::class)

package io.github.mabrur.streamly.core.player.download

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import io.github.mabrur.streamly.domain.model.DownloadStatus

/**
 * Maps a Media3 download state to the domain status.
 *
 * All seven states are handled. STOPPED and RESTARTING both present as Queued: from the
 * user's point of view the download is waiting, and distinguishing them would add a UI
 * state nobody can act on.
 *
 * [percentDownloaded] is clamped because Media3 reports `C.PERCENTAGE_UNSET` (-1f) until
 * the content length is known — rendering that raw gives a bar that jumps backwards.
 */
fun downloadStatusFor(state: Int, percentDownloaded: Float): DownloadStatus =
    when (state) {
        Download.STATE_QUEUED,
        Download.STATE_STOPPED,
        Download.STATE_RESTARTING -> DownloadStatus.Queued

        Download.STATE_DOWNLOADING ->
            DownloadStatus.InProgress(percentDownloaded.coerceIn(0f, 100f))

        Download.STATE_COMPLETED -> DownloadStatus.Completed
        Download.STATE_REMOVING -> DownloadStatus.Removing
        Download.STATE_FAILED -> DownloadStatus.Failed
        else -> DownloadStatus.Failed
    }
```

- [x] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:player:testDebugUnitTest --tests '*DownloadStatusMapperTest'`
Expected: PASS — 10 tests.

- [x] **Step 5: Commit**

```bash
git add core/player/src/main/java/io/github/mabrur/streamly/core/player/download \
        core/player/src/test/java/io/github/mabrur/streamly/core/player/download
git commit -m "feat(downloads): exhaustive download state mapper" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Plan split — Task 1 landed early, Tasks 2–6 blocked

**Branch:** `feat/download-status-mapper`, cut from `master`. Task 1 only.

This plan is explicitly split. Task 1 is a pure `Int` → sealed-type function with no
dependency on the shared `SimpleCache`, so it was pulled forward while `feat/player` is
parked awaiting device verification. 10 tests pass, no warnings.

The file lands in `:core:player`, which has no sources on `master`, so it cannot collide
with `feat/player`'s `PlayerModule.kt` / `PlayerHolder.kt` / `ExoPlayerHolder.kt` — or with
`feat/shorts-pool-policy`, which adds a different package in the same module.

**Tasks 2–6 remain blocked.** Task 2 binds `DownloadManager` to the `@Singleton SimpleCache`
that `PlayerModule` provides, and every task after it builds on that. They cannot start
until `feat/player` merges.

**Deviation:** the plan's `@file:OptIn(UnstableApi::class)` was dropped. In Media3 1.10.1
`UnstableApi` is not annotated `@RequiresOptIn`, so the opt-in is inert and the compiler
warns *"'@OptIn' has no effect"*. Without it the file compiles clean. The same applies to
every other file in these plans that carries that annotation.

---

## Task 2: `DownloadManager`, service, and manifest

The plumbing that is easiest to get wrong and fatal to the demo when it is.

**Files:**
- Create: `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadModule.kt`
- Create: `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/StreamlyDownloadService.kt`
- Create: `core/player/src/main/res/values/strings.xml`
- Create: `core/player/src/main/AndroidManifest.xml`
- Modify: `core/player/build.gradle.kts`

**Interfaces:**
- Consumes: `SimpleCache`, `DatabaseProvider`, `@Named("upstream") DataSource.Factory` from `PlayerModule`.
- Produces: `@Singleton DownloadManager` bound to that exact cache.

- [x] **Step 1: Write the DI module**

Create `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadModule.kt`:

```kotlin
@file:OptIn(UnstableApi::class)

package io.github.mabrur.streamly.core.player.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    /**
     * Bound to the SimpleCache singleton created in PlayerModule — the same instance
     * playback reads from. Two caches here would mean downloads land somewhere playback
     * never looks, and offline playback would fail while everything looked fine online.
     *
     * The upstream (network) factory is used deliberately: downloads must WRITE, so the
     * read-only CacheDataSource.Factory would be wrong here.
     */
    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: SimpleCache,
        @Named("upstream") upstreamFactory: DataSource.Factory,
    ): DownloadManager = DownloadManager(
        context,
        databaseProvider,
        cache,
        upstreamFactory,
        Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS),
    ).apply {
        maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
    }

    private const val MAX_PARALLEL_DOWNLOADS = 2
}
```

- [x] **Step 2: Write the service**

Create `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/StreamlyDownloadService.kt`:

```kotlin
@file:OptIn(UnstableApi::class)

package io.github.mabrur.streamly.core.player.download

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import io.github.mabrur.streamly.core.player.R
import javax.inject.Inject

/**
 * The five-argument DownloadService constructor creates the notification channel for us,
 * which is why no manual NotificationChannel plumbing appears anywhere in this module.
 */
@AndroidEntryPoint
class StreamlyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    @Inject
    lateinit var downloadManager: DownloadManager

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = downloadManager

    /**
     * No Scheduler. Requiring one would pull in WorkManager to restart downloads after a
     * reboot — out of scope for this build, and the PRD does not ask for it.
     */
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification = notificationHelper.buildProgressNotification(
        /* context = */ this,
        /* smallIcon = */ android.R.drawable.stat_sys_download,
        /* contentIntent = */ null,
        /* message = */ null,
        /* downloads = */ downloads,
        /* notMetRequirements = */ notMetRequirements,
    )

    companion object {
        const val CHANNEL_ID = "streamly_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }
}
```

- [x] **Step 3: Add the channel strings**

Create `core/player/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="download_channel_name">Downloads</string>
    <string name="download_channel_description">Shows progress for videos you download</string>
</resources>
```

- [x] **Step 4: Declare the service and permissions**

Create `core/player/src/main/AndroidManifest.xml`. Declaring these in the library module means
they merge into `:app` automatically — no edit to the app manifest is needed:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name=".download.StreamlyDownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync">
            <intent-filter>
                <action android:name="androidx.media3.exoplayer.downloadService.action.RESTART" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

> **All four lines are load-bearing.** Missing `FOREGROUND_SERVICE_DATA_SYNC` crashes on
> API 34+. Missing `foregroundServiceType` crashes on API 34+. Missing `POST_NOTIFICATIONS`
> means the foreground notification never shows and the service is killed. Missing the
> service declaration means `sendAddDownload` silently does nothing.

- [x] **Step 5: Verify the merged manifest**

Run: `./gradlew :app:processDebugMainManifest && grep -A4 StreamlyDownloadService app/build/intermediates/merged_manifest/debug/*/AndroidManifest.xml`
Expected: the `<service>` element appears with `android:foregroundServiceType="dataSync"`.

- [x] **Step 6: Commit**

```bash
git add core/player/src/main
git commit -m "feat(downloads): DownloadManager, service, and manifest plumbing" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

### Deviations from the plan as executed

1. **The `@Inject` field cannot be called `downloadManager`.** As written, the plan does
   not compile: Kotlin synthesises `getDownloadManager()` for the property, which clashes
   on its JVM signature with the `getDownloadManager()` override immediately below it.
   Renamed to `injectedDownloadManager`.
2. **The `RESTART` intent-filter was dropped.** `DownloadService` in Media3 1.10.1 exposes
   no `ACTION_RESTART` constant (verified with `javap -constants`); the restart action is
   one an app defines for a `Scheduler` to broadcast. We deliberately have no `Scheduler`,
   so the filter was dead configuration.
3. **The service is declared by fully-qualified name**, not the relative `.download.…`.
   Relative names in a *library* manifest are expanded against the merged application id,
   not the library namespace, so the plan's form would have resolved to the wrong class.
4. **`@file:OptIn(UnstableApi::class)` omitted**, consistent with the rest of `:core:player` —
   `UnstableApi` is not `@RequiresOptIn` in 1.10.1, so the annotation only produces a warning.
5. **Pre-task probe: the catalog's streams are downloadable.** All three distinct HLS
   sources are `#EXT-X-PLAYLIST-TYPE:VOD` with `#EXT-X-ENDLIST`, carry no `#EXT-X-KEY`, and
   serve segments over plain HTTP 200 — so `DownloadHelper` has nothing to choke on. This
   retires the risk-register item that was never closed at task 0.7.
   **But `tos_ismc` has exactly one rendition — 1080p at 6.3 Mbps, ~10 min ≈ 480 MB.**
   `DownloadHelper` at default parameters selects by renderer capability, not size, so
   Task 3 must constrain `maxVideoBitrate` or that single video downloads half a gigabyte.

> **Not verifiable here:** the emulator is API 33. `foregroundServiceType` and
> `FOREGROUND_SERVICE_DATA_SYNC` are inert below API 34, so the manifest lines that prevent
> `MissingForegroundServiceTypeException` are written from the platform contract and
> confirmed only in the merged manifest — never observed working on a device that enforces them.

---

## Task 3: `DownloadRepositoryImpl`

**Files:**
- Create: `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadRepositoryImpl.kt`
- Modify: `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadModule.kt`

**Interfaces:**
- Consumes: `DownloadRepository` (declared in `:domain` back in the foundation plan), `DownloadManager`, `downloadStatusFor`.
- Produces: a bound `DownloadRepository`, so `:app` injects only the domain interface.

- [x] **Step 1: Write the implementation**

Create `core/player/src/main/java/io/github/mabrur/streamly/core/player/download/DownloadRepositoryImpl.kt`:

```kotlin
@file:OptIn(UnstableApi::class)

package io.github.mabrur.streamly.core.player.download

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    @Named("upstream") private val upstreamFactory: DataSource.Factory,
) : DownloadRepository {

    /**
     * Real progress, straight from DownloadManager. No timers, no interpolation.
     *
     * Emits once immediately so the screen is populated on open, then on every change.
     */
    override val downloads: Flow<List<DownloadItem>> = callbackFlow {
        fun emitCurrent() {
            trySend(readAll())
        }

        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) = emitCurrent()

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) = emitCurrent()

            override fun onInitialized(downloadManager: DownloadManager) = emitCurrent()
        }

        downloadManager.addListener(listener)
        emitCurrent()

        awaitClose { downloadManager.removeListener(listener) }
    }

    /**
     * Reads the full index, not just `currentDownloads` — the latter only contains active
     * downloads, so completed items would vanish from the screen after a restart.
     */
    private fun readAll(): List<DownloadItem> {
        val items = mutableListOf<DownloadItem>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                items += cursor.download.toDomain()
            }
        }
        return items
    }

    override suspend fun download(video: Video) {
        val request = buildRequest(video)
        DownloadService.sendAddDownload(
            context,
            StreamlyDownloadService::class.java,
            request,
            /* foreground = */ false,
        )
    }

    override suspend fun remove(videoId: String) {
        DownloadService.sendRemoveDownload(
            context,
            StreamlyDownloadService::class.java,
            videoId,
            /* foreground = */ false,
        )
    }

    /**
     * DownloadHelper resolves the HLS playlist so segments and tracks are captured, not
     * just the .m3u8 manifest. Downloading the manifest alone would produce an item that
     * reports "complete" and then fails to play offline.
     */
    private suspend fun buildRequest(video: Video): DownloadRequest =
        suspendCancellableCoroutine { continuation ->
            val helper = DownloadHelper.Factory()
                .setDataSourceFactory(upstreamFactory)
                .create(MediaItem.fromUri(video.hlsUrl))

            continuation.invokeOnCancellation { helper.release() }

            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    val request = helper.getDownloadRequest(
                        /* id = */ video.id,
                        /* data = */ video.toMetadataBytes(),
                    )
                    helper.release()
                    continuation.resume(request)
                }

                override fun onPrepareError(helper: DownloadHelper, e: java.io.IOException) {
                    helper.release()
                    continuation.resumeWithException(e)
                }
            })
        }
}

/**
 * Title and thumbnail are stashed in the DownloadRequest's opaque `data` blob so the
 * Downloads screen can render properly offline, without a catalog fetch.
 */
private fun Video.toMetadataBytes(): ByteArray =
    JSONObject()
        .put("title", title)
        .put("thumbnailUrl", thumbnailUrl)
        .toString()
        .toByteArray()

private fun Download.toDomain(): DownloadItem {
    val metadata = runCatching { JSONObject(String(request.data)) }.getOrNull()
    return DownloadItem(
        videoId = request.id,
        title = metadata?.optString("title").orEmpty().ifEmpty { request.id },
        thumbnailUrl = metadata?.optString("thumbnailUrl").orEmpty(),
        status = downloadStatusFor(state, percentDownloaded),
        bytesDownloaded = bytesDownloaded,
    )
}
```

- [x] **Step 2: Bind it**

Append to `DownloadModule.kt`, as a separate module in the same file:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadBindingModule {

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}
```

adding imports `dagger.Binds`, `io.github.mabrur.streamly.domain.repository.DownloadRepository`.

- [x] **Step 3: Verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit**

```bash
git add core/player/src/main/java/io/github/mabrur/streamly/core/player/download
git commit -m "feat(downloads): repository with real DownloadManager progress" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

### Deviations from the plan as executed

1. **`DownloadHelper.Callback.onPrepared` takes two arguments in Media3 1.10.1**
   (`onPrepared(DownloadHelper, boolean)`), not one. The plan's single-argument override
   does not compile.
2. **Track selection is constrained to 1.5 Mbps of video** — not in the plan at all. Left
   at defaults, `DownloadHelper` selects renditions by decoder capability alone and the
   catalog's 1080p sources come to ~480 MB each. The constraint is deliberately soft
   (`DefaultTrackSelector` still picks the smallest rendition when all of them exceed the
   cap) so a single-rendition stream downloads rather than yielding an audio-only file.
3. **`TrackSelectionParameters.Builder()` with no Context.** The Context overload
   constrains selection to the current display size — a playback concern that must not
   leak into what gets stored offline.
4. **`@file:OptIn(UnstableApi::class)` omitted**, as in Task 2.

> **Still unmeasured:** eight of the eighteen catalog videos point at `tos_ismc`, which
> publishes exactly one rendition — 1080p at 6.3 Mbps, ~10 minutes. No track selection can
> shrink it, so those eight download at roughly half a gigabyte each. The other ten are
> bounded by the cap. Whether to repoint those eight at a multi-rendition source is a
> catalog decision, not a code one.

---

## Task 4: Downloads screen

**Files:**
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsContract.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsViewModel.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsScreen.kt`
- Create: `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsRoute.kt`
- Modify: `app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt`

- [x] **Step 1: Write the contract**

Create `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsContract.kt`:

```kotlin
package io.github.mabrur.streamly.ui.downloads

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.DownloadStatus

@Immutable
data class DownloadRowUi(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val status: DownloadStatus,
    val sizeLabel: String,
)

@Immutable
data class DownloadsUiState(
    val isLoading: Boolean = false,
    val items: List<DownloadRowUi> = emptyList(),
    val storageLabel: String = "",
    val error: AppError? = null,
)

sealed interface DownloadsIntent {
    data class RemoveClicked(val videoId: String) : DownloadsIntent
    data class PlayClicked(val videoId: String) : DownloadsIntent
}

sealed interface DownloadsEffect {
    data class OpenPlayer(val videoId: String) : DownloadsEffect
}
```

- [x] **Step 2: Add a byte formatter with its test**

Append to `core/designsystem/.../format/Formatting.kt`:

```kotlin
/** "0 B", "512 B", "1.4 MB". Binary units, one decimal above KB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
```

Append to `FormattingTest`:

```kotlin
    @Test
    fun `formats byte sizes in binary units`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }
```

- [x] **Step 3: Write the ViewModel**

Create `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsViewModel.kt`:

```kotlin
package io.github.mabrur.streamly.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.core.designsystem.format.formatBytes
import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.repository.DownloadRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState(isLoading = true))
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    private val _effects = Channel<DownloadsEffect>(Channel.BUFFERED)
    val effects: Flow<DownloadsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            downloadRepository.downloads.collect { items ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = items.map(DownloadItem::toRowUi),
                        storageLabel = formatBytes(items.sumOf { item -> item.bytesDownloaded }),
                        error = null,
                    )
                }
            }
        }
    }

    fun onIntent(intent: DownloadsIntent) {
        when (intent) {
            is DownloadsIntent.RemoveClicked -> viewModelScope.launch {
                downloadRepository.remove(intent.videoId)
            }
            is DownloadsIntent.PlayClicked -> viewModelScope.launch {
                _effects.send(DownloadsEffect.OpenPlayer(intent.videoId))
            }
        }
    }
}

private fun DownloadItem.toRowUi() = DownloadRowUi(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    status = status,
    sizeLabel = formatBytes(bytesDownloaded),
)
```

- [x] **Step 4: Write the screen**

Create `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsScreen.kt`:

```kotlin
package io.github.mabrur.streamly.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.domain.model.DownloadStatus

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onIntent: (DownloadsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Storage used: ${state.storageLabel}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(16.dp),
        )

        ContentState(
            isLoading = state.isLoading,
            error = state.error,
            data = state.items,
            isEmpty = { it.isEmpty() },
        ) { items ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = items, key = { it.videoId }) { item ->
                    DownloadRow(item = item, onIntent = onIntent)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadRowUi,
    onIntent: (DownloadsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlayable = item.status is DownloadStatus.Completed

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isPlayable) {
                onIntent(DownloadsIntent.PlayClicked(item.videoId))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = item.title, style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (val status = item.status) {
                    DownloadStatus.Completed -> "Ready to play · ${item.sizeLabel}"
                    DownloadStatus.Queued -> "Queued"
                    DownloadStatus.Failed -> "Failed"
                    DownloadStatus.Removing -> "Removing…"
                    is DownloadStatus.InProgress ->
                        "${status.percent.toInt()}% · ${item.sizeLabel}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onIntent(DownloadsIntent.RemoveClicked(item.videoId)) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Remove")
            }
        }

        val status = item.status
        if (status is DownloadStatus.InProgress) {
            LinearProgressIndicator(
                progress = { status.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
```

- [x] **Step 5: Write the route and wire it in**

Create `app/src/main/java/io/github/mabrur/streamly/ui/downloads/DownloadsRoute.kt`:

```kotlin
package io.github.mabrur.streamly.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun DownloadsRoute(
    onOpenPlayer: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is DownloadsEffect.OpenPlayer -> onOpenPlayer(effect.videoId)
                }
            }
        }
    }

    DownloadsScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
```

In `StreamlyApp.kt`, replace the Downloads entry:

```kotlin
                entry<StreamlyKey.Downloads> {
                    DownloadsRoute(
                        onOpenPlayer = { videoId -> backStack.add(StreamlyKey.Player(videoId)) },
                    )
                }
```

and add `import io.github.mabrur.streamly.ui.downloads.DownloadsRoute`.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/mabrur/streamly/ui/downloads \
        app/src/main/java/io/github/mabrur/streamly/ui/StreamlyApp.kt core/designsystem/src
git commit -m "feat(downloads): screen with real progress, storage header, remove" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

### Deviations from the plan as executed

1. **`hiltViewModel` is imported from `androidx.hilt.lifecycle.viewmodel.compose`**, not
   `androidx.hilt.navigation.compose` as the plan writes — the latter drags in Nav2, which
   D-012 forbids. Same correction as every other route in this app.
2. **The row renders its thumbnail.** `DownloadRowUi.thumbnailUrl` exists in the plan's
   contract but nothing in the plan's screen ever reads it. A downloads list with no
   artwork looks broken, and Coil is already wired for Home and Profile.
3. **Added `DownloadsViewModelTest` (5 tests)** — the plan specifies no test for this
   ViewModel, but AGENTS.md requires intent→state coverage for every screen. Covers the
   loading→loaded transition, item mapping, the summed storage label, progress updates not
   re-entering loading, `RemoveClicked` delegation, and `PlayClicked` emitting an effect
   rather than mutating state.

Suite after this task: **109 tests, 0 failures.**

> **Unverified on a device.** Nothing here has been run: no download has been started, so
> no row, no progress bar, and no empty state has ever been rendered. That is the next
> thing to do, and it is exactly the check I skipped on Shorts.

---

## Task 5: Wire the Player download button and notification permission

- [x] **Step 1: Inject the repository into `PlayerViewModel`**

Add the constructor parameter and replace the stubbed `DownloadClicked` branch:

```kotlin
    private val downloadRepository: DownloadRepository,
```

```kotlin
            PlayerIntent.DownloadClicked -> viewModelScope.launch {
                currentVideo?.let { downloadRepository.download(it) }
                _effects.send(PlayerEffect.DownloadStarted)
            }
```

`currentVideo` is a new `private var currentVideo: Video? = null` assigned inside `load()`'s
`onSuccess` — `PlayerUiState` holds `VideoUi`, which has no `hlsUrl`, so the domain object is
needed here.

Update `PlayerViewModelTest`'s `viewModel()` helper to pass a fake:

```kotlin
private class FakeDownloadRepository : DownloadRepository {
    val requested = mutableListOf<String>()
    override val downloads: Flow<List<DownloadItem>> = flowOf(emptyList())
    override suspend fun download(video: Video) { requested += video.id }
    override suspend fun remove(videoId: String) { requested -= videoId }
}
```

and add:

```kotlin
    @Test
    fun `DownloadClicked forwards the loaded video to the repository`() = runTest {
        val downloads = FakeDownloadRepository()
        val (vm, _) = viewModel(downloadRepository = downloads)

        vm.state.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.onIntent(PlayerIntent.DownloadClicked)
        runCurrent()

        assertEquals(listOf("v01"), downloads.requested)
    }
```

- [x] **Step 2: Request `POST_NOTIFICATIONS` at runtime**

In `MainActivity`, add inside `onCreate` before `setContent`:

```kotlin
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
```

with imports `android.Manifest`, `android.os.Build`, `androidx.activity.result.contract.ActivityResultContracts`.

> `minSdk` is 25, so the version guard is mandatory — the permission does not exist below 33
> and requesting it unguarded throws.

- [x] **Step 3: Verify**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Needs device verification — this sequence is the 30% category:**
  1. Open a video → tap **Download**.
  2. Go to Downloads → **progress moves and is real** (matches network activity, never jumps backwards).
  3. Wait for "Ready to play".
  4. **Enable airplane mode.**
  5. Tap the completed item → **it plays**.
  6. Tap **Remove** → the item disappears and storage-used drops.
  7. Kill and relaunch the app → completed downloads are still listed.

- [x] **Step 4: Commit**

```bash
git add app/src/main
git commit -m "feat(downloads): wire player download action and notification permission" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

### Deviations from the plan as executed

1. **Download failures are handled.** The plan's branch calls `downloadRepository.download(it)`
   bare. `DownloadHelper` parses the playlist over the network, so an `IOException` there
   would escape the coroutine and take the Player screen down on a bad connection. Wrapped
   in `runCatching`, with a new `PlayerEffect.DownloadFailed` for the failure path.
2. **`DownloadStarted` is actually surfaced.** `PlayerRoute` collected it into `Unit` with a
   comment deferring to this plan; leaving it there would mean the Download button gives no
   feedback whatsoever. The route now hosts a `SnackbarHost` and shows a message for both
   outcomes.
3. **Two extra tests** beyond the plan's one: tapping Download before the video loads must
   be a no-op, and a failing download must report rather than crash.

Suite after this task: **112 tests, 0 failures.**

---

## Task 6: Decision record

- [ ] **Step 1: Append D-009**

```markdown

---

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
```

- [ ] **Step 2: Commit**

```bash
git add docs/decisions.md
git commit -m "docs(decisions): record D-009 download repository placement" \
           -m "Co-authored-by: Claude <noreply@anthropic.com>"
```

---

## Definition of done

- [ ] `./gradlew assembleDebug` — `BUILD SUCCESSFUL`
- [ ] 100 tests — 8 domain, 20 data, 19 designsystem, 19 core:player, 34 app
- [ ] Merged manifest shows the service with `foregroundServiceType="dataSync"`
- [ ] Exactly **one** `SimpleCache` construction site in the codebase (`grep -rn "SimpleCache(" core app`)
- [ ] `docs/decisions.md` contains D-001 … D-009
- [ ] The seven-step offline sequence verified on device

**Next plan:** Onboarding, Profile, sign-out, and ship.
