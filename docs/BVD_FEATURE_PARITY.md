# BVD Feature Parity Audit

Reference: https://github.com/KafuuNeko/BiliDownload (master)
Audited branch: `bvd-style-rebuild`
Audit date: 2026-10-31

This document distinguishes working code paths from UI placeholders and model stubs. A button or label alone does not count as an implemented feature.

Legend:
- [x] Implemented in this repository's current branch (code path exists; not a claim of full device-level QA)
- [~] Partial implementation / limited format or lifecycle
- [ ] Missing

## 1. Parsing and search

- [~] BV / AV / video URL parsing: parser and video detail API exist.
- [~] EP / SS bangumi parsing: season/episode API lookup and episode list are connected; share-link variants still need real-world coverage.
- [ ] Bilibili share-link short URL resolution.
- [ ] Global video search.
- [ ] Bangumi / film search.
- [ ] UP主 manuscript search.
- [ ] Search history.
- [ ] Android share-sheet intent intake.

## 2. Video detail and parts

- [x] Video metadata: title, description, cover, owner, duration, aid, bvid.
- [x] Multi-P metadata and selected P.
- [~] Re-query progressive quality list when selected P changes.
- [~] Bangumi season metadata and episode lists; section episodes are included when returned by the API.
- [~] Batch-select bangumi episodes and enqueue each selected episode as an independent app-owned task; per-episode quality override is available.

## 3. Login and account

- [~] Manual Cookie entry into an in-memory session.
- [ ] QR-code login generation and polling.
- [ ] Blue "或者点击此处" link opening the QR login confirmation URL.
- [ ] Persisted authenticated session / secure Cookie storage.
- [ ] Account profile and logout UI.
- [ ] Password login.
- [ ] Favorites, watch history, watch-later, recent likes, personal uploads.

## 4. Stream formats and downloads

- [x] Progressive durl stream lookup and app-owned single-stream download.
- [~] DASH video track and audio track parsing via `fnval=4048`.
- [~] DASH separate-track downloads with HTTP Range resume where the server supports it.
- [~] Android MediaExtractor / MediaMuxer audio-video MP4 muxing; codec/container compatibility is device-dependent.
- [~] Quality list and selected-quality requests; 4K/8K/HDR/Dolby/AV1 availability still depends on B站 API response, account rights, source encoding and device support.
- [~] App-owned task lifecycle for video downloads: persisted task metadata, progress, pause, resume, retry, process recovery, and removal.
- [ ] Batch download and quality-mismatch policy.
- [~] Completion publishes MP4 to Download/BilibiliDownload on Android 10+; older versions use app-specific Downloads storage.
- [~] Subtitle and danmaku exports still use Android DownloadManager and do not yet share the app-owned pause/resume/retry task engine.

Important limitations: task URLs are encrypted with Android Keystore, but Cookie values are intentionally not persisted. A recovered task may need a fresh stream URL if its signed URL has expired or the source requires an authenticated Cookie. The current muxer relies on device MediaExtractor support and is not a substitute for BVD's broader FFmpeg pipeline.

## 5. Subtitles and danmaku

- [~] Subtitle track list retrieval.
- [~] Subtitle URL is currently downloaded as JSON; SRT conversion/export is missing.
- [~] Danmaku URL is currently downloaded as raw XML; CSV parsing/export is missing.

## 6. Local resources and players

- [ ] Local media indexing, metadata, categories, details, export and deletion UI.
- [ ] In-app video player, playback controls, speed and picture-in-picture.
- [ ] In-app audio player and background playback.
- [ ] Audio spectrum / spectrogram visualization.
- [ ] Open media with external player.

## 7. Media processing

- [ ] AAC audio extraction.
- [ ] FFmpeg-based muxing.
- [ ] FLV / MP4 / MKV / WMV remux or conversion.
- [ ] Media metadata inspection.
- [ ] Configurable automatic post-download processing.

## 8. Settings

- [~] Light/dark theme selection.
- [ ] Download directory selection.
- [ ] Download source and custom host.
- [ ] Batch quality mismatch policy.
- [ ] Delete source tracks after merge.
- [ ] Auto audio remux.
- [ ] Picture-in-picture preference.
- [ ] Resource and export filename templates.

## Current implementation facts

- The active UI is local HTML/CSS/JS in WebView; Kotlin provides the native bridge.
- The current account UI accepts manually pasted Cookie only. It is not QR login.
- Video downloads now use an app-owned task coordinator for progressive and DASH streams. It persists task state, encrypts saved stream URLs with Android Keystore, supports pause/resume/retry, and attempts to recover active tasks after process restart. DASH streams are muxed with Android MediaMuxer. Subtitle and danmaku exports still use Android DownloadManager.
- Subtitle export currently keeps BCC JSON; danmaku export currently keeps XML. Neither matches BVD's SRT/CSV export.
- Local resources page is a placeholder that describes the Download directory.
- The current player, account content pages, search, media processing, and most settings are absent.

## Suggested implementation order

1. Improve the current DASH pipeline: codec compatibility, FFmpeg fallback, stream URL refresh, and real-device validation.
2. Finish task engine parity: batch actions, foreground/background service integration, notifications, queue controls, and robust recovery.
3. Expand EP/SS coverage, improve batch quality policy, and test real season/episode responses.
4. QR login with QR image, clickable confirmation URL, polling, and secure session handling.
5. Correct SRT and CSV export.
6. Account content and search.
7. Local resource library, video/audio players, media conversion, and remaining settings.

Do not mark an item complete until its Kotlin implementation, UI connection, and a meaningful test/build path exist.


## 9. Bangumi and batch episode downloads (2026-10-31)

- [~] EP and SS identifiers are parsed from plain IDs and standard bangumi play URLs.
- [~] Season metadata and episode lists are loaded from the PGC season API, including returned section episodes.
- [~] Episode checkboxes, select-all/select-none, and per-episode quality selection are wired to Kotlin.
- [~] Batch requests resolve each selected episode independently and enqueue successful streams into the app-owned task coordinator; failures are reported per episode.
- [ ] Automatic refresh of season/episode metadata after API schema changes.
- [ ] Real-account verification for restricted episodes, region-limited content, and season section pagination.

The batch UI defaults each episode to "automatic highest available" and lets the user request the episode's quality list individually. A selected quality is resolved at enqueue time; permissions and available quality remain controlled by the Bilibili API.
