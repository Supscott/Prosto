package com.jestk.browser.engine.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.jestk.browser.R
import com.jestk.browser.ui.MainActivity

/**
 * Foreground service (type mediaPlayback) that keeps the process alive while a page plays media
 * in the background. It only shows the notification; the page is driven by
 * [BackgroundPlaybackController] through [BackgroundPlaybackSession].
 */
class BackgroundPlaybackService : Service() {
    private var mediaSession: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
        createChannel()
        mediaSession =
            MediaSessionCompat(this, "ProstoBackgroundPlayback").apply {
                setCallback(
                    object : MediaSessionCompat.Callback() {
                        override fun onPlay() = requestPlaying(true)

                        override fun onPause() = requestPlaying(false)
                    },
                )
                isActive = true
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PLAY -> requestPlaying(true)
            ACTION_PAUSE -> requestPlaying(false)
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
                val host = intent?.getStringExtra(EXTRA_HOST).orEmpty()
                val playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true
                showNotification(BackgroundPlaybackInfo(title, host, playing))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun requestPlaying(playing: Boolean) {
        BackgroundPlaybackSession.onSetPlaying?.invoke(playing)
    }

    private fun showNotification(info: BackgroundPlaybackInfo) {
        mediaSession?.apply {
            setMetadata(
                MediaMetadataCompat
                    .Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.host)
                    .build(),
            )
            setPlaybackState(
                PlaybackStateCompat
                    .Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE,
                    ).setState(
                        if (info.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        1f,
                    ).build(),
            )
        }

        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val toggle =
            PendingIntent.getService(
                this,
                1,
                Intent(this, BackgroundPlaybackService::class.java)
                    .setAction(if (info.isPlaying) ACTION_PAUSE else ACTION_PLAY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val actionIcon = if (info.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val actionLabel =
            getString(if (info.isPlaying) R.string.background_playback_pause else R.string.background_playback_play)

        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(info.title.ifBlank { info.host })
                .setContentText(info.host)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(actionIcon, actionLabel, toggle)
                .setStyle(
                    MediaStyle()
                        .setMediaSession(mediaSession?.sessionToken)
                        .setShowActionsInCompactView(0),
                ).build()

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.background_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val TAG = "BackgroundPlayback"
        private const val CHANNEL_ID = "prosto_background_playback"
        private const val NOTIFICATION_ID = 9002
        private const val ACTION_PLAY = "com.jestk.browser.action.BACKGROUND_PLAY"
        private const val ACTION_PAUSE = "com.jestk.browser.action.BACKGROUND_PAUSE"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_HOST = "extra_host"
        private const val EXTRA_PLAYING = "extra_playing"

        /** The started service; set and read on the main thread. */
        private var running: BackgroundPlaybackService? = null

        /**
         * Starts the service or refreshes its notification. A running service is updated in
         * place: Android 12+ refuses `startForegroundService` from the background, which is
         * where every later play/pause update comes from.
         */
        internal fun show(
            context: Context,
            info: BackgroundPlaybackInfo,
        ) {
            running?.let {
                it.showNotification(info)
                return
            }
            val intent =
                Intent(context, BackgroundPlaybackService::class.java)
                    .putExtra(EXTRA_TITLE, info.title)
                    .putExtra(EXTRA_HOST, info.host)
                    .putExtra(EXTRA_PLAYING, info.isPlaying)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) extends it: the app left
                // the screen too long ago for a new start. Playback goes on without the service.
                Log.w(TAG, "Background playback service not allowed to start", e)
            }
        }

        internal fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundPlaybackService::class.java))
        }
    }
}
