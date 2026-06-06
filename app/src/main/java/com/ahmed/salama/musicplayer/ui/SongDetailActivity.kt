package com.ahmed.salama.musicplayer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.databinding.ActivitySongDetailBinding
import com.ahmed.salama.musicplayer.model.AudioItem
import com.ahmed.salama.musicplayer.playback.MusicPlayerService

/**
 * Displays details for a single audio item. The activity expects an
 * [AudioItem] as a Parcelable extra in the Intent under the key
 * "audioItem". It shows the title, artist, album and duration and
 * exposes a simple play button that will play the selected song in
 * isolation.
 */
class SongDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val item: AudioItem? = intent.getParcelableExtra("audioItem")
        if (item == null) {
            // Nothing to show; exit gracefully
            finish()
            return
        }

        // Populate the UI fields
        binding.tvTitle.text = item.displayTitle
        binding.tvArtist.text = item.displayArtist
        binding.tvAlbum.text = item.displayAlbum
        // Format duration using the same helper as AudioAdapter
        val durationFormatted = AudioAdapter.formatDuration(item.durationMs)
        binding.tvDuration.text = durationFormatted


        binding.btnPlay.setOnClickListener {
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