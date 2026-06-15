# Music Player

A simple Android music player app built with Kotlin.

## Features

* Scan and display local audio files
* Play, pause, next, and previous controls
* Background playback with foreground service
* Media notification controls
* Song details screen with rotated artwork and seek bar
* Favourite songs handled by Room Database
* Separate favourites screen

## Tech Stack

* Kotlin
* RecyclerView
* ViewBinding
* Room
* MediaPlayer
* MediaSession
* Foreground Service(Notifications)
* ContentProvider
* Glide

## Main Flow

1. The app scans audio files from `MediaStore`.
2. Songs are cached locally using Room.
3. The user selects a song from the home screen.
4. `MusicPlayerService` handles playback in the background.
5. `SongDetailActivity` shows song details, progress, and controls.
6. Favourite songs are saved and shown in `FavouriteActivity`.

## Notes

* Some songs may not have album artwork.
* Media permissions are required to read local audio files.
* Clear app data after Room schema changes during development.
