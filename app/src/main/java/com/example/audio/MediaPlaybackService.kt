package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class MediaPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                MusicPlayerManager.togglePlayPause(applicationContext)
            }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: MusicPlayerManager.playerState.value.currentSong?.title ?: "AI Music World"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: MusicPlayerManager.playerState.value.currentSong?.genre ?: "Playing AI Track"
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true

        createChannel()
        val notification = buildNotification(title, subtitle, playing)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background playback controls for AI Music World"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, subtitle: String, playing: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePending = PendingIntent.getService(
            this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openPending)
            .setOngoing(playing)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                togglePending
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ai_music_playback"
        private const val NOTIFICATION_ID = 2048
        const val ACTION_START = "com.example.audio.START"
        const val ACTION_TOGGLE = "com.example.audio.TOGGLE"
        const val ACTION_STOP = "com.example.audio.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_PLAYING = "extra_playing"

        fun updateForeground(context: Context, title: String, subtitle: String, playing: Boolean) {
            try {
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SUBTITLE, subtitle)
                    putExtra(EXTRA_PLAYING, playing)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore background restriction errors when app is in background
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
