# Bilibili Download

Kotlin + Jetpack Compose Android downloader, rebuilt clean-room. BVD is an architectural reference only; no BVD business implementation is copied.

## Current modules

- `app/feature/home`: Compose home screen and user interactions.
- `core/model`: video, page, stream and task models.
- `core/parser`: BV / AV / URL identifier parsing.
- `core/network`: OkHttp transport and Bilibili API headers.
- `core/repository`: video metadata, pages, quality options and stream lookup.
- `core/download`: Android DownloadManager integration and task status query.
- `core/security`: memory-only cookie session boundary; raw cookies are not written to disk.
- `.github/workflows/android.yml`: GitHub Actions debug APK build.

## Implemented in this milestone

- BV ID, AV ID and video URL parsing.
- Video metadata and multi-page (P) listing.
- Available quality list returned by Bilibili API.
- User-selectable quality lookup for progressive `durl` streams.
- Android system download queue, status refresh and task removal.
- CI build and APK artifact upload.

## Known limitations

- Bilibili may return DASH-separated audio/video streams. This version reports that case instead of producing a corrupt MP4; DASH muxing is a later milestone.
- High quality depends on the rights and session Bilibili grants to the current visitor/account. No entitlement, region, DRM or paywall bypass.
- Login authorization and Bilibili-app handoff are not implemented yet. CookieSession is only a memory-only integration boundary, not a login UI.
- GIF, subtitles, danmaku, batch downloads, favorites and download history persistence are not implemented yet.

## Build

GitHub Actions uses JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21 and compileSdk 35.
