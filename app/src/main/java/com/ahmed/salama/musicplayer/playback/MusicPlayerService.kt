package com.ahmed.salama.musicplayer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.ahmed.salama.musicplayer.MainActivity
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.model.AudioItem
import java.io.IOException

class MusicPlayerService : Service() {

    private val playlist = ArrayList<AudioItem>()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentIndex = -1
    private var preparing = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_PLAY_INDEX -> {
                val index = intent.getIntExtra(EXTRA_INDEX, -1)
                val cachedPlaylist = AudioLibraryCache.getPlaylistCopy()
                if (cachedPlaylist.isNotEmpty()) {
                    playlist.clear()
                    playlist.addAll(cachedPlaylist)
                    playIndex(index)
                } else {
                    stopSelf()
                }
            }
            ACTION_TOGGLE -> togglePlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopAndRelease(true)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAndRelease(false)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "SalamaMusicSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onStop() = stopAndRelease(true)
            })
            setActive(true)
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                                PlaybackState.ACTION_PAUSE or
                                PlaybackState.ACTION_PLAY_PAUSE or
                                PlaybackState.ACTION_SKIP_TO_NEXT or
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackState.ACTION_STOP
                    )
                    .setState(PlaybackState.STATE_STOPPED, 0, 0f)
                    .build()
            )
        }
    }

    private fun playIndex(index: Int) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size) {
            Toast.makeText(this, "Invalid audio item", Toast.LENGTH_SHORT).show()
            return
        }

        currentIndex = index
        val item = playlist[currentIndex]
        preparing = true
        startForegroundCompat(buildNotification("Preparing", item, false))
        broadcastState(item, "Preparing")

        releasePlayerOnly()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { player ->
                preparing = false
                player.start()
                updateMediaSessionMetadata(item)
                updatePlaybackState(PlaybackState.STATE_PLAYING)
                startForegroundCompat(buildNotification("Playing", item, true))
                broadcastState(item, "Playing")
            }
            setOnCompletionListener { playNextOrStop() }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what, extra=$extra")
                preparing = false
                broadcastState(item, "Playback error")
                stopAndRelease(true)
                true
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                }
                setDataSource(this@MusicPlayerService, Uri.parse(item.uriString))
                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to play selected audio", e)
                preparing = false
                Toast.makeText(
                    this@MusicPlayerService,
                    "Cannot play this audio file",
                    Toast.LENGTH_SHORT
                ).show()
                stopAndRelease(true)
            }
        }
    }

    private fun togglePlayback() {
        if (mediaPlayer == null) {
            if (playlist.isNotEmpty() && currentIndex >= 0) {
                playIndex(currentIndex)
            } else {
                stopSelf()
            }
            return
        }

        if (mediaPlayer!!.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun resumePlayback() {
        if (mediaPlayer == null || preparing) return
        try {
            mediaPlayer!!.start()
            val item = getCurrentItem()
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            if (item != null) {
                startForegroundCompat(buildNotification("Playing", item, true))
                broadcastState(item, "Playing")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot resume playback", e)
        }
    }

    private fun pausePlayback() {
        if (mediaPlayer == null || preparing) return
        try {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
            }
            val item = getCurrentItem()
            updatePlaybackState(PlaybackState.STATE_PAUSED)
            if (item != null) {
                val notification = buildNotification("Paused", item, false)
                getNotificationManager().notify(NOTIFICATION_ID, notification)
                broadcastState(item, "Paused")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot pause playback", e)
        }
    }

    private fun playNext() {
        if (playlist.isEmpty()) {
            stopSelf()
            return
        }
        var nextIndex = currentIndex + 1
        if (nextIndex >= playlist.size) nextIndex = 0
        playIndex(nextIndex)
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) {
            stopSelf()
            return
        }
        var previousIndex = currentIndex - 1
        if (previousIndex < 0) previousIndex = playlist.size - 1
        playIndex(previousIndex)
    }

    private fun playNextOrStop() {
        if (playlist.isEmpty()) {
            stopAndRelease(true)
            return
        }
        if (currentIndex < playlist.size - 1) {
            playIndex(currentIndex + 1)
        } else {
            stopAndRelease(true)
        }
    }

    private fun stopAndRelease(stopService: Boolean) {
        val item = getCurrentItem()
        releasePlayerOnly()
        preparing = false
        updatePlaybackState(PlaybackState.STATE_STOPPED)
        if (item != null) broadcastState(item, "Stopped")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        if (stopService) stopSelf()
    }

    private fun releasePlayerOnly() {
        mediaPlayer?.let {
            try {
                it.reset()
                it.release()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error releasing MediaPlayer", e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    private fun getCurrentItem(): AudioItem? {
        return if (currentIndex >= 0 && currentIndex < playlist.size) {
            playlist[currentIndex]
        } else {
            null
        }
    }

    private fun updateMediaSessionMetadata(item: AudioItem) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, item.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, item.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, item.durationMs)
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun updatePlaybackState(state: Int) {
        var position = 0L
        if (mediaPlayer != null) {
            try {
                position = mediaPlayer!!.currentPosition.toLong()
            } catch (ignored: IllegalStateException) {
                position = 0L
            }
        }

        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, if (state == PlaybackState.STATE_PLAYING) 1.0f else 0.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun buildNotification(status: String, item: AudioItem, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(this, 1, openAppIntent, pendingIntentFlags())

        val previousAction = Notification.Action.Builder(
            R.drawable.ic_launcher_background,
            "Previous",
            servicePendingIntent(ACTION_PREVIOUS, 2)
        ).build()

        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) R.drawable.ic_launcher_background else R.drawable.ic_launcher_background,
            if (isPlaying) "Pause" else "Play",
            servicePendingIntent(ACTION_TOGGLE, 3)
        ).build()

        val nextAction = Notification.Action.Builder(
            R.drawable.ic_launcher_background,
            "Next",
            servicePendingIntent(ACTION_NEXT, 4)
        ).build()

        val stopAction = Notification.Action.Builder(
            R.drawable.ic_launcher_background,
            "Stop",
            servicePendingIntent(ACTION_STOP, 5)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle(item.title)
            .setContentText("$status • ${item.artist}")
            .setContentIntent(contentIntent)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            setAction(action)
        }
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls for the music player"
            }
            getNotificationManager().createNotificationChannel(channel)
        }
    }

    private fun getNotificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun broadcastState(item: AudioItem, state: String) {
        val intent = Intent(BROADCAST_PLAYBACK_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TITLE, item.title)
            putExtra(EXTRA_ARTIST, item.artist)
            putExtra(EXTRA_STATE, state)
        }
        sendBroadcast(intent)
    }

    companion object {
        const val ACTION_PLAY_INDEX = "com.ahmed.salama.musicplayer.action.PLAY_INDEX"
        const val ACTION_TOGGLE = "com.ahmed.salama.musicplayer.action.TOGGLE"
        const val ACTION_NEXT = "com.ahmed.salama.musicplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.ahmed.salama.musicplayer.action.PREVIOUS"
        const val ACTION_STOP = "com.ahmed.salama.musicplayer.action.STOP"

        const val EXTRA_INDEX = "extra_index"

        const val BROADCAST_PLAYBACK_STATE = "com.ahmed.salama.musicplayer.broadcast.PLAYBACK_STATE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_STATE = "extra_state"

        private const val TAG = "MusicPlayerService"
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 2024
    }
}

