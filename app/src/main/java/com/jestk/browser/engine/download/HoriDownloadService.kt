package com.jestk.browser.engine.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.FileProvider
import com.jestk.browser.JestkApplication
import com.jestk.browser.R
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.ui.MainActivity
import com.jestk.browser.util.FileSizeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class HoriDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationManager: NotificationManager
    private val activeDownloadIds = ConcurrentHashMap.newKeySet<Long>()

    companion object {
        private const val TAG = "HoriDownloadService"

        const val CHANNEL_ID_DOWNLOADS = "hori_downloads"
        const val CHANNEL_ID_COMPLETED = "hori_downloads_completed"
        const val FOREGROUND_NOTIFICATION_ID = 9001

        const val ACTION_START_DOWNLOAD = "com.jestk.browser.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.jestk.browser.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.jestk.browser.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.jestk.browser.action.CANCEL_DOWNLOAD"
        const val ACTION_RETRY = "com.jestk.browser.action.RETRY_DOWNLOAD"
        const val ACTION_UPDATE_NOTIFICATION = "com.jestk.browser.action.UPDATE_NOTIFICATION"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_USER_AGENT = "extra_user_agent"

        fun formatFileSize(bytes: Long): String = FileSizeFormatter.formatFileSize(bytes)

        fun formatProgressText(bytesDownloaded: Long, totalBytes: Long): String =
            FileSizeFormatter.formatProgressText(bytesDownloaded, totalBytes)

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0L) return ""
            return "${formatFileSize(bytesPerSec)}/s"
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundStarted()

        val downloadManager = (applicationContext as? JestkApplication)?.container?.let {
            HoriDownloadManager.getInstance(applicationContext, it.downloadRepository)
        }

        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager could not be initialized")
            checkFinishService()
            return START_NOT_STICKY
        }

        val action = intent?.action
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        val referer = intent?.getStringExtra(EXTRA_REFERER)
        val userAgent = intent?.getStringExtra(EXTRA_USER_AGENT)

        when (action) {
            ACTION_START_DOWNLOAD, ACTION_RESUME -> {
                if (downloadId != -1L) {
                    activeDownloadIds.add(downloadId)
                    downloadManager.performDownload(
                        id = downloadId,
                        referer = referer,
                        userAgent = userAgent,
                        onProgress = { progress ->
                            updateOngoingNotification(progress)
                        },
                        onComplete = { completedItem ->
                            activeDownloadIds.remove(downloadId)
                            showCompletedNotification(completedItem)
                            checkFinishService()
                        },
                        onError = { id, errorMsg ->
                            activeDownloadIds.remove(id)
                            showFailedNotification(id, errorMsg)
                            checkFinishService()
                        }
                    )
                } else {
                    checkFinishService()
                }
            }
            ACTION_PAUSE -> {
                if (downloadId != -1L) {
                    activeDownloadIds.remove(downloadId)
                    downloadManager.pauseDownload(downloadId)
                    checkFinishService()
                } else {
                    checkFinishService()
                }
            }
            ACTION_CANCEL -> {
                if (downloadId != -1L) {
                    activeDownloadIds.remove(downloadId)
                    downloadManager.cancelDownload(downloadId)
                    notificationManager.cancel(downloadId.toInt())
                    checkFinishService()
                } else {
                    checkFinishService()
                }
            }
            ACTION_RETRY -> {
                if (downloadId != -1L) {
                    notificationManager.cancel(downloadId.toInt())
                    downloadManager.retryDownload(downloadId)
                } else {
                    checkFinishService()
                }
            }
            ACTION_UPDATE_NOTIFICATION -> {
                checkFinishService()
            }
            else -> {
                // If service was killed and restarted, check database for pending/interrupted downloads
                resumeInterruptedDownloads(downloadManager)
            }
        }

        return START_NOT_STICKY
    }

    private fun resumeInterruptedDownloads(downloadManager: HoriDownloadManager) {
        val app = applicationContext as? JestkApplication ?: return
        serviceScope.launch(Dispatchers.IO) {
            val pendingOrPaused = app.container.downloadRepository.getActiveOrPendingDownloads()
            for (download in pendingOrPaused) {
                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PENDING) {
                    activeDownloadIds.add(download.id)
                    downloadManager.performDownload(
                        id = download.id,
                        onProgress = { progress -> updateOngoingNotification(progress) },
                        onComplete = { completedItem ->
                            activeDownloadIds.remove(download.id)
                            showCompletedNotification(completedItem)
                            checkFinishService()
                        },
                        onError = { id, errorMsg ->
                            activeDownloadIds.remove(id)
                            showFailedNotification(id, errorMsg)
                            checkFinishService()
                        }
                    )
                }
            }
            if (activeDownloadIds.isEmpty()) {
                stopSelf()
            }
        }
    }

    private fun ensureForegroundStarted() {
        val initialNotif = NotificationCompat.Builder(this, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("Hori")
            .setContentText("Downloading file...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createAppPendingIntent())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, initialNotif)
        }
    }

    private fun updateOngoingNotification(progress: DownloadProgressInfo) {
        val progressText = formatProgressText(progress.bytesDownloaded, progress.totalBytes)
        val speedText = if (progress.speedBytesPerSec > 0) " · ${formatSpeed(progress.speedBytesPerSec)}" else ""
        val fullText = "$progressText$speedText"

        val pauseIntent = Intent(this, HoriDownloadService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_DOWNLOAD_ID, progress.id)
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            (progress.id * 10 + 1).toInt(),
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, HoriDownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, progress.id)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            (progress.id * 10 + 2).toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle(progress.fileName)
            .setContentText(fullText)
            .setSubText("Hori")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createAppPendingIntent())
            .setProgress(
                100,
                progress.progressPercentage ?: 0,
                progress.isIndeterminate
            )
            .addAction(0, "Pause", pausePendingIntent)
            .addAction(0, "Cancel", cancelPendingIntent)

        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, builder.build())
    }

    private fun showCompletedNotification(item: DownloadItem) {
        val file = File(item.filePath)
        val openIntent = createOpenFileIntent(file, item.mimeType)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            (item.id + 50000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeStr = formatFileSize(item.totalBytes)
        val contentText = if (sizeStr.isNotEmpty()) "${item.fileName} · $sizeStr" else item.fileName

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETED)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("Download complete")
            .setContentText(contentText)
            .setSubText("Hori")
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)

        val notifId = (item.id + 10000).toInt()
        notificationManager.notify(notifId, builder.build())
    }

    private fun showFailedNotification(id: Long, errorMsg: String) {
        val retryIntent = Intent(this, HoriDownloadService::class.java).apply {
            action = ACTION_RETRY
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        val retryPendingIntent = PendingIntent.getService(
            this,
            (id * 10 + 3).toInt(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("Download failed")
            .setContentText(errorMsg)
            .setSubText("Hori")
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Retry", retryPendingIntent)

        notificationManager.notify((id + 20000).toInt(), builder.build())
    }

    private fun checkFinishService() {
        if (activeDownloadIds.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenFileIntent(file: File, mimeType: String?): Intent {
        val uri: Uri = try {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } catch (_: Throwable) {
            Uri.fromFile(file)
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_ID_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing browser download progress notifications"
                setShowBadge(false)
            }

            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed browser downloads"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(downloadChannel)
            notificationManager.createNotificationChannel(completedChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
