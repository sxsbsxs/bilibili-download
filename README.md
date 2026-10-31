# Bilibili Download

An independent Kotlin Android Bilibili downloader. The project is being rebuilt using architectural patterns and technology choices from [KafuuNeko/BiliDownload](https://github.com/KafuuNeko/BiliDownload), while retaining its own package, implementation and product identity.

## Architecture direction

- Kotlin + Gradle Kotlin DSL + Version Catalog
- Modular Android project with feature and shared-core boundaries
- XML layouts with ViewBinding/DataBinding support alongside Jetpack Compose Material 3
- ViewModel and Kotlin Coroutines for UI state and asynchronous work
- Retrofit/Gson and OkHttp for network access
- Room for persistent local data
- WorkManager for deferrable background work
- Glide for image loading
- Media3 for media playback
- Kotpref for lightweight preferences

The dependency foundation is being added incrementally. A dependency being present does not mean its corresponding feature is already implemented.

## Current implemented functions

- BV ID, AV ID and Bilibili video URL parsing
- Video metadata and multi-part (P) listing
- Available quality list returned by Bilibili
- User-selectable quality lookup for progressive durl streams
- Android system download queue, status refresh and task removal
- GitHub Actions debug APK build and artifact upload

## Planned feature modules

- Home and search
- Video details and stream selection
- Bilibili account authorization
- Download queue and history
- Local resource management
- Danmaku and subtitle resources
- Audio extraction and media remuxing
- Favorites, watch-later and personal center
- Settings and download preferences

## Known limitations

- DASH-separated audio/video streams are not yet muxed.
- Available quality depends on the permissions granted by Bilibili to the current visitor/account.
- Bilibili app authorization handoff is not implemented yet.
- GIF, subtitles, danmaku, batch downloads and persistent download history are planned, not claimed as completed.

## Build

GitHub Actions currently uses JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21 and compileSdk 35.
