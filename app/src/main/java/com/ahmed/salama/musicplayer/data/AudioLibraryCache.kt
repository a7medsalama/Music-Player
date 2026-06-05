package com.ahmed.salama.musicplayer.data

import com.ahmed.salama.musicplayer.model.AudioItem

/**
 * Same-process cache used to avoid sending a large playlist through Intent extras.
 * The Activity loads MediaStore once and the playback Service reads from this cache.
 */
object AudioLibraryCache {
    private val playlist = ArrayList<AudioItem>()

    @Synchronized
    fun setPlaylist(items: List<AudioItem>?) {
        playlist.clear()
        if (items != null) {
            playlist.addAll(items)
        }
    }

    @Synchronized
    fun getPlaylistCopy(): ArrayList<AudioItem> = ArrayList(playlist)
}

