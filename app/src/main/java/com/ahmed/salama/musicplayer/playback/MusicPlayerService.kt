package com.ahmed.salama.musicplayer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import com.ahmed.salama.musicplayer.MainActivity
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.model.AudioItem
import java.io.IOException

class MusicPlayerService : Service() {

    private var currentArtworkBitmap: Bitmap? = null

    private val playlist = ArrayList<AudioItem>()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentIndex = -1
    private var preparing = false

    private var repeatMode = REPEAT_OFF
    private var shuffleEnabled = false

    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            val item = getCurrentItem()
            val player = mediaPlayer

            if (item != null && player != null && !preparing) {
                broadcastState(
                    item = item,
                    state = if (player.isPlaying) "Playing" else "Paused"
                )

                updatePlaybackState(
                    if (player.isPlaying) {
                        PlaybackState.STATE_PLAYING
                    } else {
                        PlaybackState.STATE_PAUSED
                    }
                )

                if (player.isPlaying) {
                    progressHandler.postDelayed(this, 1000L)
                }
            }
        }
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

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

                    val sameIndex = index == currentIndex
                    val alreadyActive = mediaPlayer != null && (mediaPlayer?.isPlaying == true || preparing)

                    if (sameIndex && alreadyActive) {
                        getCurrentItem()?.let {
                            broadcastState(it, if (mediaPlayer?.isPlaying == true) "Playing" else "Preparing")
                        }
                        return START_NOT_STICKY
                    }

                    playIndex(index)
                } else {
                    stopSelf()
                }
            }

            ACTION_TOGGLE -> togglePlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopAndRelease(true)

            ACTION_SEEK_TO -> {
                val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                seekTo(positionMs)
            }

            ACTION_SET_REPEAT_MODE -> {
                repeatMode = intent.getIntExtra(EXTRA_REPEAT_MODE, REPEAT_OFF)
                getCurrentItem()?.let {
                    broadcastState(it, if (mediaPlayer?.isPlaying == true) "Playing" else "Paused")
                }
            }

            ACTION_TOGGLE_SHUFFLE -> {
                shuffleEnabled = !shuffleEnabled
                getCurrentItem()?.let {
                    broadcastState(it, if (mediaPlayer?.isPlaying == true) "Playing" else "Paused")
                }
            }
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

    private fun loadArtworkBitmap(item: AudioItem): Bitmap? {
        val artworkUriString = item.artworkUriString

        // First try API 29+ album thumbnail using album id from the artwork URI.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !artworkUriString.isNullOrBlank()) {
            val albumId = artworkUriString.substringAfterLast("/").toLongOrNull()

            if (albumId != null && albumId > 0) {
                try {
                    val albumUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                    )

                    return contentResolver.loadThumbnail(
                        albumUri,
                        Size(512, 512),
                        null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load album thumbnail for ${item.title}", e)
                }
            }
        }

        // Second try classic album art URI stream.
        if (!artworkUriString.isNullOrBlank()) {
            try {
                contentResolver.openInputStream(Uri.parse(artworkUriString))?.use { stream ->
                    return android.graphics.BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode album art URI for ${item.title}", e)
            }
        }

        // Third fallback: embedded picture inside the audio file.
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(this, Uri.parse(item.uriString))

            val bytes = retriever.embeddedPicture
            retriever.release()

            if (bytes != null) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract embedded artwork for ${item.title}", e)
            null
        }
    }

    private fun setupMediaPlayer(item: AudioItem) {
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { player ->
                preparing = false
                player.start()
                startProgressUpdates()
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
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@MusicPlayerService, Uri.parse(item.uriString))
                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to play selected audio", e)
                preparing = false
                Toast.makeText(this@MusicPlayerService, "Cannot play this audio file", Toast.LENGTH_SHORT).show()
                stopAndRelease(true)
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "SalamaMusicSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onStop() = stopAndRelease(true)

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }
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
                                PlaybackState.ACTION_STOP or
                                PlaybackState.ACTION_SEEK_TO
                    )
                    .setState(PlaybackState.STATE_STOPPED, 0, 0f)
                    .build()
            )
        }
    }

    private fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        if (preparing) return

        try {
            val safePosition = positionMs
                .coerceAtLeast(0L)
                .coerceAtMost(player.duration.toLong())

            player.seekTo(safePosition.toInt())

            val state = if (player.isPlaying) {
                PlaybackState.STATE_PLAYING
            } else {
                PlaybackState.STATE_PAUSED
            }

            updatePlaybackState(state)

            getCurrentItem()?.let {
                broadcastState(it, if (player.isPlaying) "Playing" else "Paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot seek", e)
        }
    }


    private fun playIndex(index: Int) {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size) {
            Toast.makeText(this, "Invalid audio item", Toast.LENGTH_SHORT).show()
            return
        }

        currentIndex = index
        val item = playlist[currentIndex]

        // ✅ Load artwork synchronously here — playIndex is called from onStartCommand
        // which runs on the main thread. Offload to a thread to avoid ANR.
        // We already have a preparing state so the UI won't flicker.
        currentArtworkBitmap = null  // clear previous

        preparing = true
        startForegroundCompat(buildNotification("Preparing", item, false))
        broadcastState(item, "Preparing")

        // Load artwork off-thread, then set up the MediaPlayer
        Thread {
            currentArtworkBitmap = loadArtworkBitmap(item)
            // All MediaPlayer setup must run on main thread
            mainLooper.let { looper ->
                android.os.Handler(looper).post {
                    releasePlayerOnly()
                    setupMediaPlayer(item)
                }
            }
        }.start()
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
            startProgressUpdates()
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
                stopProgressUpdates()
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

        if (shuffleEnabled) {
            playRandom()
            return
        }

        var nextIndex = currentIndex + 1
        if (nextIndex >= playlist.size) {
            nextIndex = 0
        }

        playIndex(nextIndex)
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) {
            stopSelf()
            return
        }

        var previousIndex = currentIndex - 1
        if (previousIndex < 0) {
            previousIndex = playlist.size - 1
        }

        playIndex(previousIndex)
    }

    private fun playNextOrStop() {
        if (playlist.isEmpty()) {
            stopAndRelease(true)
            return
        }

        when {
            repeatMode == REPEAT_ONE -> {
                playIndex(currentIndex)
            }

            shuffleEnabled -> {
                playRandom()
            }

            currentIndex < playlist.size - 1 -> {
                playIndex(currentIndex + 1)
            }

            repeatMode == REPEAT_ALL -> {
                playIndex(0)
            }

            else -> {
                stopAndRelease(true)
            }
        }
    }

    private fun playRandom() {
        if (playlist.isEmpty()) {
            stopSelf()
            return
        }

        if (playlist.size == 1) {
            playIndex(0)
            return
        }

        var randomIndex: Int
        do {
            randomIndex = (playlist.indices).random()
        } while (randomIndex == currentIndex)

        playIndex(randomIndex)
    }

    private fun stopAndRelease(stopService: Boolean) {
        val item = getCurrentItem()
        releasePlayerOnly()
        preparing = false
        stopProgressUpdates()
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
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, item.displayTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, item.displayArtist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, item.displayAlbum)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, item.durationMs)

        currentArtworkBitmap?.let { bitmap ->
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
            builder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
        }

        mediaSession?.setMetadata(builder.build())
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
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO

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
            R.drawable.ic_play_previous, "Previous", servicePendingIntent(ACTION_PREVIOUS, 2)
        ).build()
        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_3,
            if (isPlaying) "Pause" else "Play",
            servicePendingIntent(ACTION_TOGGLE, 3)
        ).build()
        val nextAction = Notification.Action.Builder(
            R.drawable.ic_play_next, "Next", servicePendingIntent(ACTION_NEXT, 4)
        ).build()
        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_media_pause, "Stop", servicePendingIntent(ACTION_STOP, 5)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // ✅ Set small icon (required) and large icon from artwork bitmap
        builder.setSmallIcon(R.drawable.ic_music)                // ← was missing the argument — compile error
        currentArtworkBitmap?.let { builder.setLargeIcon(it) }   // ← artwork shown in notification

        return builder
            .setContentTitle(item.displayTitle)
            .setContentText("$status • ${item.displayArtist}")
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
        val player = mediaPlayer

        val positionMs = try {
            player?.currentPosition?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }

        val durationMs = try {
            player?.duration?.toLong()?.takeIf { it > 0 } ?: item.durationMs
        } catch (_: Exception) {
            item.durationMs
        }

        val intent = Intent(BROADCAST_PLAYBACK_STATE).apply {
            setPackage(packageName)

            putExtra(EXTRA_AUDIO_ID, item.id)
            putExtra(EXTRA_INDEX, currentIndex)
            putExtra(EXTRA_TITLE, item.displayTitle)
            putExtra(EXTRA_ARTIST, item.displayArtist)
            putExtra(EXTRA_STATE, state)

            putExtra(EXTRA_POSITION_MS, positionMs)
            putExtra(EXTRA_DURATION_MS, durationMs)
            putExtra(EXTRA_IS_PLAYING, state == "Playing")

            putExtra(EXTRA_REPEAT_MODE, repeatMode)
            putExtra(EXTRA_SHUFFLE_ENABLED, shuffleEnabled)
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

        const val ACTION_SEEK_TO = "com.ahmed.salama.musicplayer.action.SEEK_TO"
        const val ACTION_SET_REPEAT_MODE = "com.ahmed.salama.musicplayer.action.SET_REPEAT_MODE"
        const val ACTION_TOGGLE_SHUFFLE = "com.ahmed.salama.musicplayer.action.TOGGLE_SHUFFLE"

        const val EXTRA_AUDIO_ID = "extra_audio_id"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_REPEAT_MODE = "extra_repeat_mode"
        const val EXTRA_SHUFFLE_ENABLED = "extra_shuffle_enabled"

        const val REPEAT_OFF = 0
        const val REPEAT_ONE = 1
        const val REPEAT_ALL = 2
    }
}

