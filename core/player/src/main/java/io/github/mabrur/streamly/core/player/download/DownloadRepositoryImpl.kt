package io.github.mabrur.streamly.core.player.download

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.model.DownloadStatus
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.DownloadRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    @Named("upstream") private val upstreamFactory: DataSource.Factory,
) : DownloadRepository {

    /**
     * Real progress: every number here is read back from DownloadManager. There is a
     * timer, but it only decides *when* to re-read — nothing is interpolated or estimated,
     * so a stalled download visibly stalls instead of drifting upward.
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

        // DownloadManager.Listener fires on state transitions only — never as bytes
        // arrive. Measured: with the listener alone the row sat at "13% · 105.5 MB" for
        // 24 seconds while the cache on disk grew from 263 MB to 291 MB. Polling is what
        // makes the progress real; it stops as soon as nothing is running.
        val poller = launch {
            while (isActive) {
                delay(PROGRESS_POLL_MS)
                val items = readAll()
                if (items.any { it.status is DownloadStatus.InProgress }) trySend(items)
            }
        }

        awaitClose {
            poller.cancel()
            downloadManager.removeListener(listener)
        }
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
                .setTrackSelectionParameters(downloadTrackParameters())
                // Mandatory, and not obviously so. Without a RenderersFactory the helper
                // has no renderer capabilities to select against, every track comes back
                // unsupported, and getDownloadRequest emits an empty stream-key list —
                // which means "download the entire media". Measured: omitting this pulled
                // all five renditions of the 848x480 test stream, ~800 MB instead of ~67 MB,
                // with the bitrate cap below having no observable effect at all.
                .setRenderersFactory(DefaultRenderersFactory(context))
                .create(MediaItem.fromUri(video.hlsUrl))

            continuation.invokeOnCancellation { helper.release() }

            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper, hasSelections: Boolean) {
                    val request = helper.getDownloadRequest(
                        /* id = */ video.id,
                        /* data = */ video.toMetadataBytes(),
                    )
                    helper.release()
                    continuation.resume(request)
                }

                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    helper.release()
                    continuation.resumeWithException(e)
                }
            })
        }

    /**
     * Without a cap, DownloadHelper picks renditions by decoder capability alone, and the
     * catalog's 1080p/6.3 Mbps sources come to roughly half a gigabyte each. Capping the
     * video bitrate keeps a download proportionate to a demo.
     *
     * This is a soft constraint by design: DefaultTrackSelector still selects the smallest
     * available rendition when every one of them exceeds the cap, so a single-rendition
     * stream downloads rather than silently producing an audio-only file.
     *
     * The no-argument Builder is deliberate. The Context overload constrains selection to
     * the current display size, which is a playback concern — a download must not be
     * capped by whatever screen happened to request it.
     */
    private fun downloadTrackParameters(): TrackSelectionParameters =
        TrackSelectionParameters.Builder()
            .setMaxVideoBitrate(MAX_DOWNLOAD_VIDEO_BITRATE)
            .build()

    private companion object {
        const val MAX_DOWNLOAD_VIDEO_BITRATE = 1_500_000
        const val PROGRESS_POLL_MS = 1_000L
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
    // Narrow on purpose: a request written by an older build may carry a data blob this
    // parser does not understand, and that must degrade to the id rather than be fatal.
    val metadata = try {
        JSONObject(String(request.data))
    } catch (e: JSONException) {
        null
    }
    return DownloadItem(
        videoId = request.id,
        title = metadata?.optString("title").orEmpty().ifEmpty { request.id },
        thumbnailUrl = metadata?.optString("thumbnailUrl").orEmpty(),
        status = downloadStatusFor(state, percentDownloaded),
        bytesDownloaded = bytesDownloaded,
    )
}
