package com.ahmed.salama.musicplayer

import android.app.Application

class MusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaybackViewModelHolder.init()
    }
}