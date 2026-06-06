package com.ahmed.salama.musicplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ahmed.salama.musicplayer.model.AudioItem

class MainViewModel: ViewModel() {

    private val _audioItems = MutableLiveData<List<AudioItem>>()
    val audioItems: LiveData<List<AudioItem>> get() = _audioItems

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> get() = _isPlaying

    private val _currentAudioItem = MutableLiveData<AudioItem>()
    val currentAudioItem: LiveData<AudioItem> get() = _currentAudioItem


}