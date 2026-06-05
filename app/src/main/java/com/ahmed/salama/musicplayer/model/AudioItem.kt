package com.ahmed.salama.musicplayer.model

import android.os.Parcel
import android.os.Parcelable

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String
) : Parcelable {

    val displayTitle: String
        get() = safe(title, "Unknown title")

    val displayArtist: String
        get() = safe(artist, "Unknown artist")

    val displayAlbum: String
        get() = safe(album, "Unknown album")

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty(),
        parcel.readLong(),
        parcel.readString().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeLong(durationMs)
        parcel.writeString(uriString)
    }

    override fun describeContents(): Int = 0

    private fun safe(value: String, fallback: String): String {
        if (value.isBlank()) return fallback

        return if (value.trim().equals("<unknown>", ignoreCase = true)) {
            fallback
        } else {
            value.trim()
        }
    }

    companion object CREATOR : Parcelable.Creator<AudioItem> {
        override fun createFromParcel(parcel: Parcel): AudioItem {
            return AudioItem(parcel)
        }

        override fun newArray(size: Int): Array<AudioItem?> {
            return arrayOfNulls(size)
        }
    }
}