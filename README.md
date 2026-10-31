# Bilibili Download

Kotlin + Jetpack Compose Android downloader. This is a clean-room rebuild. BVD is an architectural reference only; no BVD business implementation or old BiliDownload-KT business source is copied.

## BVD-inspired project layout

```
app/                         Android entry point
common/
  core/
    model/                   Video, page, stream and task models
    parser/                  BV / AV / URL identifier parsing
    network/                 OkHttp transport and request headers
    repository/              Video metadata, pages and stream lookup
    download/                Android DownloadManager adapter
    security/                In-memory cookie session boundary
feature/
  home/                      Compose parsing, page/quality selection and detail UI
service/
  download/                  Download orchestration boundary
notification/                Download notification channel
```

## Current capabilities
- Parse BV IDs, AV IDs and Bilibili video URLs.
- Fetch video metadata and multi-part (P) lists.
- Read the quality options returned by Bilibili and request a selected quality.
- Queue supported progressive streams through Android DownloadManager.
- Refresh and remove current-session download tasks.
- Keep cookie handling behind an in-memory session boundary; raw cookies are not persisted.

## Limitations / next work
- DASH audio/video streams are detected but not muxed yet; a proper FFmpeg/Media3 mux pipeline is next.
- Download task persistence/history and background orchestration need expansion.
- Official Bilibili App authorization handoff and callback are not connected yet.
- Collections, bangumi, subtitles, danmaku, GIF and batch download remain future features.

Quality availability depends on the current visitor/account. No DRM, paywall, region or account-entitlement bypass is implemented.

## Build
GitHub Actions uses JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21 and compileSdk 35.
