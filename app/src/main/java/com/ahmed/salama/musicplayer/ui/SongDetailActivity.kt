package com.ahmed.salama.musicplayer.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.model.AudioItem
import com.ahmed.salama.musicplayer.playback.MusicPlayerService
import com.ahmed.salama.musicplayer.ui.AudioAdapter

/**
 * Displays details for a single audio item. The activity expects an
 * [AudioItem] as a Parcelable extra in the Intent under the key
 * "audioItem". It shows the title, artist, album and duration and
 * exposes a simple play button that will play the selected song in
 * isolation.
 */
class SongDetailActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_detail)

        val item: AudioItem? = intent.getParcelableExtra("audioItem")
        if (item == null) {
            // Nothing to show; exit gracefully
            finish()
            return
        }

        // Populate the UI fields
        findViewById<TextView>(R.id.textSongTitle).text = item.displayTitle
        findViewById<TextView>(R.id.textSongArtist).text = item.displayArtist
        findViewById<TextView>(R.id.textSongAlbum).text = item.displayAlbum
        // Format duration using the same helper as AudioAdapter
        val durationFormatted = AudioAdapter.formatDuration(item.durationMs)
        findViewById<TextView>(R.id.textSongDuration).text = durationFormatted

        // Setup play button to play this single item
        val playButton = findViewById<Button>(R.id.buttonPlaySong)
        playButton.setOnClickListener {
            // Set a playlist containing only this item
            AudioLibraryCache.setPlaylist(listOf(item))
            // Start playback at index 0
            val playIntent = Intent(this, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PLAY_INDEX
                putExtra(MusicPlayerService.EXTRA_INDEX, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(playIntent)
            } else {
                startService(playIntent)
            }
        }
    }
}