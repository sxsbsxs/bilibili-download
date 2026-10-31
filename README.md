# BiliDownload

An independent Android Bilibili video downloader. The app uses a WebView-based HTML/CSS/JavaScript interface and Kotlin for Android integration, Bilibili API access, and system download tasks.

## UI and architecture

- HTML structure: `app/src/main/assets/web/index.html`
- CSS design system and themes: `app/src/main/assets/web/css/app.css`
- JavaScript navigation and interaction: `app/src/main/assets/web/js/app.js`
- Kotlin WebView host and asynchronous bridge: `MainActivity.kt` and `bridge/WebBridge.kt`
- API parsing, Cookie session, and Android DownloadManager: `:common`

## Implemented

- Parse public BV IDs, AV IDs, and Bilibili video URLs.
- Read video metadata and multi-part (P) information.
- Query progressive-stream quality options per selected P when returned by the API.
- Request a selected progressive quality and enqueue it with Android DownloadManager.
- View, refresh, and remove app-created download tasks.
- Query and export available subtitles as JSON files.
- Export the selected video's danmaku as XML.
- Optional in-memory Cookie session for account-dependent API access.
- Responsive HTML interface with light/dark appearance settings.

## Not implemented yet

- DASH video/audio merging and high-resolution DASH downloads.
- Bilibili QR-code login and account profile/favorites/history synchronization.
- Local media indexing and in-app playback.

Quality availability is controlled by Bilibili, account permissions, region, and API responses. The app does not bypass VIP, DRM, or other access controls. Cookie credentials are memory-only and cleared when the app process exits.

## Build

GitHub Actions runs unit tests and creates a debug APK with Gradle and Java 17.
