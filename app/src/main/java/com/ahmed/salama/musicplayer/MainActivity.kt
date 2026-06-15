package com.ahmed.salama.musicplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ahmed.salama.musicplayer.data.AudioRepository
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.databinding.ActivityMainBinding
import com.ahmed.salama.musicplayer.model.AudioItem
import com.ahmed.salama.musicplayer.playback.MusicPlayerService
import com.ahmed.salama.musicplayer.ui.AudioAdapter
import com.ahmed.salama.musicplayer.ui.FavouriteActivity
import com.ahmed.salama.musicplayer.ui.SongDetailActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
//    private val viewModel = MainViewModel()

    private val viewModel = PlaybackViewModelHolder.viewModel
    private lateinit var adapter: AudioAdapter

    private var currentPlayingAudioId: Long = -1L
    private var isServicePlaying: Boolean = false
    private var lastSelectedIndex = RecyclerView.NO_POSITION

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MusicPlayerService.BROADCAST_PLAYBACK_STATE) return

            val title = intent.getStringExtra(MusicPlayerService.EXTRA_TITLE)
            val artist = intent.getStringExtra(MusicPlayerService.EXTRA_ARTIST)
            val state = intent.getStringExtra(MusicPlayerService.EXTRA_STATE)
            val index = intent.getIntExtra(
                MusicPlayerService.EXTRA_INDEX,
                RecyclerView.NO_POSITION
            )
            val audioId = intent.getLongExtra(
                MusicPlayerService.EXTRA_AUDIO_ID,
                -1L
            )

            currentPlayingAudioId = audioId
            isServicePlaying = state == "Playing"

            viewModel.updatePlaybackState(title, artist, state)

            if (index != RecyclerView.NO_POSITION) {
                viewModel.updateCurrentIndex(index)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.favLayout.setOnClickListener {
            val favIntent = Intent(this, FavouriteActivity::class.java)
            startActivity(favIntent)
        }

        // viewModel observer :
        viewModel.isPlaying.observe(this) { isPlaying ->
            val imageResource = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_3
            binding.buttonToggle.setBackgroundResource(imageResource)
        }

        viewModel.nowPlayingTitle.observe(this) { title ->
            binding.textNowPlaying.text = title
        }

        viewModel.nowPlayingArtist.observe(this) { artist ->
            binding.textPlaybackState.text = artist
        }

        viewModel.currentIndex.observe(this) { index ->
            if (index == RecyclerView.NO_POSITION) return@observe

            if (index != lastSelectedIndex) {
                adapter.setSelectedPosition(index)
                lastSelectedIndex = index

                // Optional: scroll only when song actually changed
                // Remove this line completely if you never want auto-scroll.
                binding.rvAudio.smoothScrollToPosition(index)
            }
        }

        // Setup button listeners
        binding.buttonGrantPermission.setOnClickListener { requestNeededPermissions() }

        val listAudio = findViewById<RecyclerView>(R.id.rvAudio)

        adapter = AudioAdapter { _, position ->
            showSongOptions(position)
        }

        listAudio.layoutManager = LinearLayoutManager(this)
        listAudio.adapter = adapter

        binding.buttonToggle.setOnClickListener { sendPlaybackAction(MusicPlayerService.ACTION_TOGGLE) }
//        binding.buttonPrevious.setOnClickListener { sendPlaybackAction(MusicPlayerService.ACTION_PREVIOUS) }
        binding.buttonNext.setOnClickListener { sendPlaybackAction(MusicPlayerService.ACTION_NEXT) }
//        binding.buttonStop.setOnClickListener { sendStopAction() }

        // Load audio library
        if (hasNeededPermissions()) {
            loadAudioLibrary()
        } else {
            showPermissionState()
            requestNeededPermissions()
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA_PERMISSION) {
            if (hasNeededPermissions()) {
                loadAudioLibrary()
            } else {
                showPermissionState()
                Toast.makeText(this, "Audio permission is required to list your music.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPermissionState() {
        binding.progressLoading.visibility = View.GONE
        binding.emptyLayout.visibility = View.VISIBLE
        binding.buttonGrantPermission.visibility = View.VISIBLE
        binding.textSubtitle.text = "Permission required to scan audio files"
    }

    private fun loadAudioLibrary() {
        binding.buttonGrantPermission.visibility = View.GONE
        binding.emptyLayout.visibility = View.GONE
        binding.progressLoading.visibility = View.VISIBLE
        binding.textSubtitle.text = "Scanning audio files from MediaStore..."

        Thread {
            val items = AudioRepository().loadAudio(applicationContext)
            runOnUiThread {
                binding.progressLoading.visibility = View.GONE
                adapter.submitList(items)
                AudioLibraryCache.setPlaylist(items)
                binding.textSubtitle.text = "${items.size} songs found"
                binding.emptyLayout.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun hasNeededPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        for (permission in requiredAudioPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requiredAudioPermissions(): ArrayList<String> {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            // Add this line:
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions
    }

    private fun permissionsToRequest(): ArrayList<String> {
        val permissions = requiredAudioPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = permissionsToRequest()
        requestPermissions(permissions.toTypedArray(), REQUEST_MEDIA_PERMISSION)
    }

    private fun sendPlaybackAction(action: String) {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            setAction(action)
        }
        startService(intent)
    }

//    private fun sendStopAction() {
//        val intent = Intent(this, MusicPlayerService::class.java).apply {
//            action = MusicPlayerService.ACTION_STOP
//        }
//        startService(intent)
//    }

    private fun startPlaybackService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        private const val REQUEST_MEDIA_PERMISSION = 9001
    }

    /**
     * Display a bottom sheet with actions for the selected audio item. Allows
     * the user to play the song or view more details. When play is selected
     * the service is started similarly to the previous behaviour. When details
     * are selected a new [SongDetailActivity] is launched.
     */
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
        }

        startActivity(detailIntent)
    }
}
