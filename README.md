# BiliDownload

An independent Android Bilibili downloader. The Android host and download engine are Kotlin; the application UI is local HTML/CSS/JavaScript rendered in WebView.

## UI and architecture

- HTML: `app/src/main/assets/web/index.html`
- CSS: `app/src/main/assets/web/css/app.css`
- JavaScript: `app/src/main/assets/web/js/app.js`
- Native bridge: `app/src/main/java/com/sxsbsxs/bilibilidownload/bridge/WebBridge.kt`
- Bilibili API and models: `:common`
- Download coordinator: `DownloadTaskCoordinator.kt`
- Background execution: `DownloadForegroundService.kt`

## Implemented

- Parse BV / AV / video URL and EP / SS bangumi identifiers.
- Read video details, cover, uploader, multi-P parts, stream qualities and DASH tracks when Bilibili returns them.
- Select individual bangumi episodes and enqueue them as separate tasks with per-episode quality selection.
- Download progressive or DASH streams through the app-owned task coordinator; DASH tracks are muxed with Android MediaExtractor / MediaMuxer.
- Persist task state, pause / resume / retry / remove tasks, recover interrupted tasks, validate HTTP Range responses and attempt one expired-stream refresh.
- Run app-owned downloads through a foreground data-sync service with an ongoing notification; task UI supports status filters and batch pause / resume / remove.
- Sign in by generating a Bilibili QR code locally and polling the official passport QR-login endpoint. Scan with the Bilibili APP and confirm in the APP.
- Encrypt the login Cookie with AES-GCM using an Android Keystore key. Logout clears the stored credential; task URLs and task metadata use separate encrypted storage.
- Search Bilibili videos, bangumi/media and UP accounts with pagination; local search history is stored on-device. UP search results can load public submission lists.
- Account center: created favorite folders and their resources, watch history, watch-later, recent likes and own submissions (login required; favorite folders/resources, likes and uploads support paging; API coverage still needs real-account validation).
- Local resource library: scan the app's `Download/BilibiliDownload` folder, filter videos/audio/subtitles/data, open with an external app, share/export through Android's chooser, and delete files.
- Convert Bilibili subtitle JSON to SRT and danmaku XML to CSV, then save the files to `Download/BilibiliDownload` on Android 10+.
- Light / dark WebView UI.

## Known limitations

- Bilibili controls available quality, codecs, region access and account permissions. The app does not bypass VIP, DRM or other access controls.
- HTTP Range resume depends on the Bilibili CDN. If a CDN ignores Range or returns a different resource validator, the affected partial stream is restarted safely.
- DASH muxing uses Android MediaMuxer, not FFmpeg; codec/container support varies by device.
- Batch pause / resume applies to app-owned tasks. Legacy Android DownloadManager tasks can be removed but cannot be paused or resumed through this app UI.
- QR login, Cookie persistence, background service and media exports are implemented in code but still require real-device and real-account validation.
- Local media indexing, built-in video/audio players, account favorites/history pages, search, FFmpeg conversions and advanced download settings are not implemented yet.

## Build

GitHub Actions builds the debug APK with Gradle and Java 17. A successful CI build verifies compilation, not real-device playback, account login or CDN behavior.
