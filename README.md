# Music Player

A simple Android music player app built with Kotlin.

# Screenshots

<p align="center">
  <img src="https://github.com/a7medsalama/Music-Player/blob/86a66a809ed69f914e231c4e66e7684fffb62c06/Screenshot_20260615_182845.jpg" width="190"/>
  <img src="https://github.com/a7medsalama/Music-Player/blob/86a66a809ed69f914e231c4e66e7684fffb62c06/Screenshot_20260615_182846.jpg" width="190"/>
  <img src="https://github.com/a7medsalama/Music-Player/blob/86a66a809ed69f914e231c4e66e7684fffb62c06/Screenshot_20260615_182847.jpg" width="190"/>
  <img src="https://github.com/a7medsalama/Music-Player/blob/86a66a809ed69f914e231c4e66e7684fffb62c06/Screenshot_20260615_182848.jpg" width="190"/>
  <img src="https://github.com/a7medsalama/Music-Player/blob/86a66a809ed69f914e231c4e66e7684fffb62c06/Screenshot_20260615_182849.jpg" width="190"/>
</p>

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
