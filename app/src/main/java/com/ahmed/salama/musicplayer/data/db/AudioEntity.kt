package com.ahmed.salama.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ahmed.salama.musicplayer.model.AudioItem

/**
 * Entity representing an audio item persisted in the Room database.
 * The table name matches the underlying audio cache table. Each row
 * corresponds to a single audio file on the device. The companion
 * functions allow conversions between the in-memory [AudioItem] model
 * and the persisted [AudioEntity].
 */
@Entity(tableName = "audio_items")
data class AudioEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String
) {
    /**
     * Convert this persisted entity back into an in-memory [AudioItem].
     */
    fun toAudioItem(): AudioItem = AudioItem(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uriString = uriString
    )

    companion object {
        /**
         * Convert an [AudioItem] into its persisted [AudioEntity] form. This is used
         * when caching content provider results into the database.
         */
        fun fromAudioItem(item: AudioItem): AudioEntity = AudioEntity(
            id = item.id,
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            uriString = item.uriString
        )
    }
}