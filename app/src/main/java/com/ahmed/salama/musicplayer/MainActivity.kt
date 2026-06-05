package com.ahmed.salama.musicplayer

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ahmed.salama.musicplayer.data.AudioRepository
import com.ahmed.salama.musicplayer.data.AudioLibraryCache
import com.ahmed.salama.musicplayer.model.AudioItem
import com.ahmed.salama.musicplayer.playback.MusicPlayerService
import com.ahmed.salama.musicplayer.ui.AudioAdapter
import com.ahmed.salama.musicplayer.ui.SongDetailActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : Activity() {

    private lateinit var adapter: AudioAdapter
    private lateinit var textSubtitle: TextView
    private lateinit var textEmpty: TextView
    private lateinit var textNowPlaying: TextView
    private lateinit var textPlaybackState: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var buttonGrantPermission: Button
    private lateinit var buttonToggle: Button

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != MusicPlayerService.BROADCAST_PLAYBACK_STATE) return

            val title = intent.getStringExtra(MusicPlayerService.EXTRA_TITLE)
            val artist = intent.getStringExtra(MusicPlayerService.EXTRA_ARTIST)
            val state = intent.getStringExtra(MusicPlayerService.EXTRA_STATE)

            textNowPlaying.text = if (title.isNullOrBlank()) "Nothing playing" else title

            val safeArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"
            val safeState = state ?: "Stopped"
            textPlaybackState.text = "$safeState • $safeArtist"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textSubtitle = findViewById(R.id.textSubtitle)
        textEmpty = findViewById(R.id.textEmpty)
        textNowPlaying = findViewById(R.id.textNowPlaying)
        textPlaybackState = findViewById(R.id.textPlaybackState)
        progressLoading = findViewById(R.id.progressLoading)
        buttonGrantPermission = findViewById(R.id.buttonGrantPermission)
        buttonToggle = findViewById(R.id.buttonToggle)
        val buttonPrevious = findViewById<Button>(R.id.buttonPrevious)
        val buttonNext = findViewById<Button>(R.id.buttonNext)
        val buttonStop = findViewById<Button>(R.id.buttonStop)


        // Setup button listeners
        buttonGrantPermission.setOnClickListener { requestNeededPermissions() }

        val listAudio = findViewById<RecyclerView>(R.id.rvAudio)

        adapter = AudioAdapter { _, position ->
            showSongOptions(position)
        }

        listAudio.layoutManager = LinearLayoutManager(this)
        listAudio.adapter = adapter

        buttonToggle.setOnClickListener { sendPlaybackAction(MusicPlayerService.ACTION_TOGGLE) }
        buttonPrevious.setOnClickListener { sendPlaybackAction(MusicPlayerService.ACTION_PREVIOUS) }
        buttonNext.setOnClickListener { sendPlaybackAction(MusicPlayerService.ACTION_NEXT) }
        buttonStop.setOnClickListener { sendStopAction() }

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
        progressLoading.visibility = View.GONE
        textEmpty.visibility = View.VISIBLE
        buttonGrantPermission.visibility = View.VISIBLE
        textSubtitle.text = "Permission required to scan audio files"
    }

    private fun loadAudioLibrary() {
        buttonGrantPermission.visibility = View.GONE
        textEmpty.visibility = View.GONE
        progressLoading.visibility = View.VISIBLE
        textSubtitle.text = "Scanning audio files from MediaStore..."

        Thread {
            val items = AudioRepository().loadAudio(applicationContext)
            runOnUiThread {
                progressLoading.visibility = View.GONE
                adapter.submitList(items)
                AudioLibraryCache.setPlaylist(items)
                textSubtitle.text = "${items.size} audio file(s) found on this device"
                textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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

    private fun sendStopAction() {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_STOP
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
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_playback_options, null)
        val optionPlay = sheetView.findViewById<TextView>(R.id.optionPlay)
        val optionDetails = sheetView.findViewById<TextView>(R.id.optionDetails)

        // Play selected song when Play option is clicked
        optionPlay.setOnClickListener {
            dialog.dismiss()
            // Set the entire playlist and start playing from the selected position
            AudioLibraryCache.setPlaylist(adapter.getItemsCopy())
            val intent = Intent(this, MusicPlayerService::class.java).apply {
                action = MusicPlayerService.ACTION_PLAY_INDEX
                putExtra(MusicPlayerService.EXTRA_INDEX, position)
            }
            startPlaybackService(intent)
        }

        // Open details screen when Details option is clicked
        optionDetails.setOnClickListener {
            dialog.dismiss()
            val item = adapter.getItem(position)
            val detailIntent = Intent(this, SongDetailActivity::class.java).apply {
                putExtra("audioItem", item)
            }
            startActivity(detailIntent)
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }
}
