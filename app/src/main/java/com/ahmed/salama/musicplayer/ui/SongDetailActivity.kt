package com.ahmed.salama.musicplayer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.ahmed.salama.musicplayer.PlaybackViewModelHolder
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.data.AudioRepository
import com.ahmed.salama.musicplayer.databinding.ActivitySongDetailBinding
import com.ahmed.salama.musicplayer.model.AudioItem
import com.ahmed.salama.musicplayer.playback.MusicPlayerService
import com.bumptech.glide.Glide

class SongDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongDetailBinding
    private val viewModel = PlaybackViewModelHolder.viewModel

    private lateinit var item: AudioItem

    private var playlistIndex: Int = RecyclerView.NO_POSITION
    private var userSeeking = false

    private var currentPlayingAudioId: Long = -1L
    private var isCurrentSongPlaying = false

    private var repeatMode = MusicPlayerService.REPEAT_OFF
    private var shuffleEnabled = false

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MusicPlayerService.BROADCAST_PLAYBACK_STATE) return

            val audioId = intent.getLongExtra(
                MusicPlayerService.EXTRA_AUDIO_ID,
                -1L
            )

            val index = intent.getIntExtra(
                MusicPlayerService.EXTRA_INDEX,
                RecyclerView.NO_POSITION
            )

            val positionMs = intent.getLongExtra(
                MusicPlayerService.EXTRA_POSITION_MS,
                0L
            )

            val durationMs = intent.getLongExtra(
                MusicPlayerService.EXTRA_DURATION_MS,
                item.durationMs
            )

            val isPlaying = intent.getBooleanExtra(
                MusicPlayerService.EXTRA_IS_PLAYING,
                false
            )

            repeatMode = intent.getIntExtra(
                MusicPlayerService.EXTRA_REPEAT_MODE,
                MusicPlayerService.REPEAT_OFF
            )

            shuffleEnabled = intent.getBooleanExtra(
                MusicPlayerService.EXTRA_SHUFFLE_ENABLED,
                false
            )

            currentPlayingAudioId = audioId
            isCurrentSongPlaying = isPlaying

            val playlist = AudioLibraryCache.getPlaylistCopy()

            val newCurrentItem = when {
                index != RecyclerView.NO_POSITION && index in playlist.indices -> {
                    playlist[index]
                }

                audioId != -1L -> {
                    playlist.firstOrNull { it.id == audioId }
                }

                else -> null
            }

            if (newCurrentItem != null && newCurrentItem.id != item.id) {
                playlistIndex = playlist.indexOfFirst { it.id == newCurrentItem.id }
                updateSongUiForNewItem(newCurrentItem)
            }

            updateProgress(positionMs, durationMs)
            updatePlayButton()
            updateRepeatButton()
            updateShuffleButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySongDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivSong.animation = AnimationUtils.loadAnimation(this, R.anim.rotate)

        val passedItem: AudioItem? = intent.getParcelableExtra("audioItem")
        if (passedItem == null) {
            finish()
            return
        }

        item = passedItem

        playlistIndex = intent.getIntExtra(
            MusicPlayerService.EXTRA_INDEX,
            RecyclerView.NO_POSITION
        )

        bindSongData()
        setupFavourite()
        setupSeekBar()
        setupPlaybackClicks()
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
    }

    override fun onStop() {
        unregisterReceiver(playbackReceiver)
        super.onStop()
    }

    private fun bindSongData() {
        binding.tvTitle.text = item.displayTitle
        binding.tvArtist.text = item.displayArtist

        binding.tvCurrentDuration.text = "0:00"
        binding.tvTotalDuration.text = AudioAdapter.formatDuration(item.durationMs)

        binding.seekBar.max = item.durationMs.toInt()
        binding.seekBar.progress = 0

        Glide.with(this)
            .load(item.artworkUriString)
            .placeholder(R.drawable.background)
            .error(R.drawable.background)
            .centerCrop()
            .into(binding.background)

        Glide.with(this)
            .load(item.artworkUriString)
            .placeholder(R.drawable.ic_music)
            .error(R.drawable.ic_music)
            .centerCrop()
            .into(binding.ivSong)

        binding.ivBack.setOnClickListener {
            finish()
        }

        updateFavouriteIcon(item.isFavourite)
        updatePlayButton()
        updateRepeatButton()
        updateShuffleButton()
    }

    private fun setupFavourite() {
        Thread {
            val dbFavourite = AudioRepository().isFavourite(
                applicationContext,
                item.id
            )

            runOnUiThread {
                item = item.copy(isFavourite = dbFavourite)
                updateFavouriteIcon(dbFavourite)
            }
        }.start()

        binding.ivFav.setOnClickListener {
            val newValue = !item.isFavourite

            item = item.copy(isFavourite = newValue)
            updateFavouriteIcon(newValue)

            Thread {
                AudioRepository().setFavourite(
                    applicationContext,
                    item.id,
                    newValue
                )
            }.start()
        }
    }

    private fun updateFavouriteIcon(isFavourite: Boolean) {
        binding.ivFav.setImageResource(
            if (isFavourite) {
                R.drawable.ic_fav
            } else {
                R.drawable.ic_non_fav
            }
        )
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        binding.tvCurrentDuration.text =
                            AudioAdapter.formatDuration(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = false

                    val positionMs = seekBar?.progress?.toLong() ?: 0L

                    val intent = Intent(
                        this@SongDetailActivity,
                        MusicPlayerService::class.java
                    ).apply {
                        action = MusicPlayerService.ACTION_SEEK_TO
                        putExtra(MusicPlayerService.EXTRA_POSITION_MS, positionMs)
                    }

                    startService(intent)
                }
            }
        )
    }

    private fun setupPlaybackClicks() {
        binding.btnPlay.setOnClickListener {
            if (currentPlayingAudioId == item.id) {
                sendPlaybackAction(MusicPlayerService.ACTION_TOGGLE)
            } else {
                playThisSong()
            }
        }

        binding.btnPlayNext.setOnClickListener {
            sendPlaybackAction(MusicPlayerService.ACTION_NEXT)
        }

        binding.btnPlayPrevious.setOnClickListener {
            sendPlaybackAction(MusicPlayerService.ACTION_PREVIOUS)
        }

        binding.btnRepeat.setOnClickListener {
            repeatMode = when (repeatMode) {
                MusicPlayerService.REPEAT_OFF -> MusicPlayerService.REPEAT_ONE
                MusicPlayerService.REPEAT_ONE -> MusicPlayerService.REPEAT_ALL
                else -> MusicPlayerService.REPEAT_OFF
            }

            val intent = Intent(this, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_SET_REPEAT_MODE
                putExtra(MusicPlayerService.EXTRA_REPEAT_MODE, repeatMode)
            }

            startService(intent)
            updateRepeatButton()
        }

        binding.btnShuffle.setOnClickListener {
            sendPlaybackAction(MusicPlayerService.ACTION_TOGGLE_SHUFFLE)
            shuffleEnabled = !shuffleEnabled
            updateShuffleButton()
        }
    }

    private fun playThisSong() {
        val playlist = AudioLibraryCache.getPlaylistCopy()

        if (playlist.isEmpty()) {
            AudioLibraryCache.setPlaylist(listOf(item))
            playlistIndex = 0
        } else {
            val foundIndex = playlist.indexOfFirst { it.id == item.id }

            if (foundIndex != -1) {
                playlistIndex = foundIndex
            } else {
                AudioLibraryCache.setPlaylist(listOf(item))
                playlistIndex = 0
            }
        }

        val playIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PLAY_INDEX
            putExtra(MusicPlayerService.EXTRA_INDEX, playlistIndex)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(playIntent)
        } else {
            startService(playIntent)
        }
    }

    private fun updateSongUiForNewItem(newItem: AudioItem) {
        item = newItem

        binding.tvTitle.text = item.displayTitle
        binding.tvArtist.text = item.displayArtist

        Glide.with(this)
            .load(item.artworkUriString)
            .placeholder(R.drawable.background)
            .error(R.drawable.background)
            .centerCrop()
            .into(binding.background)

        Glide.with(this)
            .load(item.artworkUriString)
            .placeholder(R.drawable.ic_music)
            .error(R.drawable.ic_music)
            .centerCrop()
            .into(binding.ivSong)

        Thread {
            val dbFavourite = AudioRepository().isFavourite(applicationContext, item.id)
            runOnUiThread {
                item = item.copy(isFavourite = dbFavourite)
                updateFavouriteIcon(dbFavourite)
            }
        }.start()
    }

    private fun sendPlaybackAction(actionValue: String) {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = actionValue
        }

        startService(intent)
    }

    private fun updateProgress(positionMs: Long, durationMs: Long) {
        if (userSeeking) return

        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration)

        binding.seekBar.max = safeDuration.toInt()
        binding.seekBar.progress = safePosition.toInt()

        binding.tvCurrentDuration.text = AudioAdapter.formatDuration(safePosition)
        binding.tvTotalDuration.text = AudioAdapter.formatDuration(safeDuration)
    }

    private fun updatePlayButton() {
        binding.btnPlay.setImageResource(
            if (isCurrentSongPlaying) {
                R.drawable.ic_circle_pause
            } else {
                R.drawable.ic_play_2
            }
        )
    }

    private fun updateRepeatButton() {
        when (repeatMode) {
            MusicPlayerService.REPEAT_ONE -> {
                binding.btnRepeat.alpha = 1f
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
            }

            MusicPlayerService.REPEAT_ALL -> {
                binding.btnRepeat.alpha = 1f
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
            }

            else -> {
                binding.btnRepeat.alpha = 0.45f
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
            }
        }
    }

    private fun updateShuffleButton() {
        binding.btnShuffle.alpha = if (shuffleEnabled) 1f else 0.45f
    }
}