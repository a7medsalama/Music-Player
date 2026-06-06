package com.ahmed.salama.musicplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView

class PlaybackViewModel : ViewModel() {

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentIndex = MutableLiveData(RecyclerView.NO_POSITION)
    val currentIndex: LiveData<Int> = _currentIndex

    private val _nowPlayingTitle = MutableLiveData("Nothing playing")
    val nowPlayingTitle: LiveData<String> = _nowPlayingTitle

    private val _nowPlayingArtist = MutableLiveData("")
    val nowPlayingArtist: LiveData<String> = _nowPlayingArtist

    val isFavourite = MutableLiveData(false)

    fun updatePlaybackState(title: String?, artist: String?, state: String?) {
        _isPlaying.value = state == "Playing"
        _nowPlayingTitle.value = title?.takeIf { it.isNotBlank() } ?: "Nothing playing"
        _nowPlayingArtist.value = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"
    }

    fun updateCurrentIndex(index: Int) {
        _currentIndex.value = index
    }
}