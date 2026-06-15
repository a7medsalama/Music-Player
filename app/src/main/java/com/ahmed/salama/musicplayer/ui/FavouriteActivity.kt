package com.ahmed.salama.musicplayer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ahmed.salama.musicplayer.PlaybackViewModelHolder
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.data.AudioRepository
import com.ahmed.salama.musicplayer.databinding.ActivityFavouriteBinding
import com.ahmed.salama.musicplayer.playback.MusicPlayerService

class FavouriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavouriteBinding
    private val viewModel = PlaybackViewModelHolder.viewModel
    private lateinit var adapter: AudioAdapter

    private var currentPlayingAudioId: Long = -1L
    private var isServicePlaying: Boolean = false
    private var lastSelectedAudioId: Long = -1L

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MusicPlayerService.BROADCAST_PLAYBACK_STATE) return

            val title = intent.getStringExtra(MusicPlayerService.EXTRA_TITLE)
            val artist = intent.getStringExtra(MusicPlayerService.EXTRA_ARTIST)
            val state = intent.getStringExtra(MusicPlayerService.EXTRA_STATE)

            val audioId = intent.getLongExtra(
                MusicPlayerService.EXTRA_AUDIO_ID,
                -1L
            )

            currentPlayingAudioId = audioId
            isServicePlaying = state == "Playing"

            viewModel.updatePlaybackState(title, artist, state)

            if (audioId != -1L && audioId != lastSelectedAudioId) {
                val positionInFavouriteList = adapter.indexOfAudioId(audioId)

                if (positionInFavouriteList != RecyclerView.NO_POSITION) {
                    adapter.setSelectedPosition(positionInFavouriteList)
                    lastSelectedAudioId = audioId

                    // Optional. Remove this line if you do not want auto-scroll.
                    // binding.rvAudio.smoothScrollToPosition(positionInFavouriteList)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFavouriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener {
            finish()
        }

        setupObservers()
        setupRecycler()
        setupPlayerButtons()

        loadFavouriteSongs()
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter(MusicPlayerService.BROADCAST_PLAYBACK_STATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(playbackReceiver, filter)
        }

        // Refresh every time screen appears, because favourite state may change in details.
        loadFavouriteSongs()
    }

    override fun onStop() {
        unregisterReceiver(playbackReceiver)
        super.onStop()
    }

    private fun setupObservers() {
        viewModel.isPlaying.observe(this) { isPlaying ->
            val imageResource = if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play_3
            }

            binding.buttonToggle.setBackgroundResource(imageResource)
        }

        viewModel.nowPlayingTitle.observe(this) { title ->
            binding.textNowPlaying.text = title ?: "Nothing playing"
        }
    }

    private fun setupRecycler() {
        adapter = AudioAdapter { _, position ->
            showSongOptions(position)
        }

        binding.rvAudio.layoutManager = LinearLayoutManager(this)
        binding.rvAudio.adapter = adapter
    }

    private fun setupPlayerButtons() {
        binding.buttonToggle.setOnClickListener {
            sendPlaybackAction(MusicPlayerService.ACTION_TOGGLE)
        }

        binding.buttonNext.setOnClickListener {
            sendPlaybackAction(MusicPlayerService.ACTION_NEXT)
        }
    }

    private fun loadFavouriteSongs() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE

        Thread {
            val items = AudioRepository().loadFavourites(applicationContext)

            runOnUiThread {
                binding.progressLoading.visibility = View.GONE
                adapter.submitList(items)

                binding.textSubtitle.text = "${items.size} favourite song(s)"
                binding.textEmpty.visibility = if (items.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                val currentPosition = adapter.indexOfAudioId(currentPlayingAudioId)
                if (currentPosition != RecyclerView.NO_POSITION) {
                    adapter.setSelectedPosition(currentPosition)
                    lastSelectedAudioId = currentPlayingAudioId
                }
            }
        }.start()
    }

    private fun showSongOptions(position: Int) {
        val item = adapter.getItem(position)

        val sameSongAlreadyPlaying =
            currentPlayingAudioId == item.id && isServicePlaying

        if (!sameSongAlreadyPlaying) {
            AudioLibraryCache.setPlaylist(adapter.getItemsCopy())

            val intent = Intent(this, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PLAY_INDEX
                putExtra(MusicPlayerService.EXTRA_INDEX, position)
            }

            startPlaybackService(intent)

            currentPlayingAudioId = item.id
            isServicePlaying = true
        }

        val detailIntent = Intent(this, SongDetailActivity::class.java).apply {
            putExtra("audioItem", item)
            putExtra(MusicPlayerService.EXTRA_INDEX, position)
        }

        startActivity(detailIntent)
    }

    private fun sendPlaybackAction(action: String) {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            setAction(action)
        }

        startService(intent)
    }

    private fun startPlaybackService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}