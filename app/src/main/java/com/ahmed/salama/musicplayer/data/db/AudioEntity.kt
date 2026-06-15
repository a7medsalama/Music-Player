package com.ahmed.salama.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ahmed.salama.musicplayer.model.AudioItem

@Entity(tableName = "audio_items")
data class AudioEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val artworkUriString: String?,
    val isFavourite: Boolean = false
) {
    fun toAudioItem(): AudioItem = AudioItem(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uriString = uriString,
        artworkUriString = artworkUriString,
        isFavourite = isFavourite
    )

    companion object {

        fun fromAudioItem(item: AudioItem): AudioEntity = AudioEntity(
            id = item.id,
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            uriString = item.uriString,
            artworkUriString = item.artworkUriString,
            isFavourite = item.isFavourite
        )
    }
}