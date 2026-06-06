package com.ahmed.salama.musicplayer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.RequiresApi
import com.ahmed.salama.musicplayer.data.db.AudioDatabase
import com.ahmed.salama.musicplayer.model.AudioItem

class AudioRepository {

    /**
     * Load audio items either from the Room cache or the MediaStore. If the cache
     * contains entries then those are returned; otherwise the MediaStore is scanned
     * and the results are persisted to the database for next time. Callers must
     * ensure this method runs off of the main thread.
     */
    fun loadAudio(context: Context): List<AudioItem> {
        val database = AudioDatabase.getInstance(context)
        val dao = database.audioDao()

        // Check cached audio items first
        val cached = dao.getAll()
        if (cached.isNotEmpty()) {
            return cached.map { it.toAudioItem() }
        }

        // Otherwise scan the MediaStore and cache the results
        val audioItems = mutableListOf<AudioItem>()
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        resolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)

                // Skip zero-length tracks
                if (duration <= 0) continue

                var artworkUriString: String? = null

                if (albumId > 0) {
                    // ✅ On ALL API levels, produce a URI string — let Glide handle loading
                    artworkUriString = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                }

                val contentUri = ContentUris.withAppendedId(collection, id)
                audioItems.add(
                    AudioItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uriString = contentUri.toString(),
                        artworkUriString = artworkUriString,
                        isFavorite = false
                    )
                )
            }
        }

        // Persist the scanned results to the cache. Clear any stale entries first.
        if (audioItems.isNotEmpty()) {
            dao.clear()
            val entities = audioItems.map {
                com.ahmed.salama.musicplayer.data.db.AudioEntity.fromAudioItem(it)
            }
            dao.insertAll(entities)
        }

        return audioItems
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getAlbumArtwork(resolver: ContentResolver, albumId: Long): Bitmap? {
        val contentUri = ContentUris.withAppendedId(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            albumId
        )
        return try {
            resolver.loadThumbnail(contentUri, Size(640, 480), null)
        } catch (e: Exception) {
            null
        }
    }
}