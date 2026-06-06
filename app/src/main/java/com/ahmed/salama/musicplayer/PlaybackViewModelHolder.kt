package com.ahmed.salama.musicplayer

object PlaybackViewModelHolder {
    lateinit var viewModel: PlaybackViewModel

    fun init() {
        if (!::viewModel.isInitialized) {
            viewModel = PlaybackViewModel()
        }
    }
}