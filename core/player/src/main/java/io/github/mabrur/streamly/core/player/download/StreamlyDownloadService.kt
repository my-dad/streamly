package io.github.mabrur.streamly.core.player.download

import android.app.Notification
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

    /**
     * Not named `downloadManager`: Kotlin would synthesise `getDownloadManager()` for the
     * property and clash with the override below on the same JVM signature.
     */
    @Inject
    lateinit var injectedDownloadManager: DownloadManager

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

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
