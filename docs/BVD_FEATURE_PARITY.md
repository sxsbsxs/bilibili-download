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
- [ ] EP / SS bangumi and media parsing.
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
- [ ] Bangumi episode / season details and episode lists.
- [ ] Batch select and enqueue multiple parts.

## 3. Login and account

- [~] Manual Cookie entry into an in-memory session.
- [ ] QR-code login generation and polling.
- [ ] Blue "或者点击此处" link opening the QR login confirmation URL.
- [ ] Persisted authenticated session / secure Cookie storage.
- [ ] Account profile and logout UI.
- [ ] Password login.
- [ ] Favorites, watch history, watch-later, recent likes, personal uploads.

## 4. Stream formats and downloads

- [~] Progressive durl stream lookup and single-URL download.
- [ ] DASH video track and audio track parsing.
- [ ] DASH separate-track downloads.
- [ ] Audio/video muxing and post-processing.
- [ ] Full quality/codec capability negotiation for DASH (4K/8K/HDR/Dolby/AV1 when actually returned and authorized).
- [~] Android DownloadManager persistence and basic status query.
- [ ] App-owned task states and lifecycle (pause/resume, retries, recovery, batch actions).
- [ ] Batch download and quality-mismatch policy.
- [ ] Download completion processing and media publication workflow.

Important limitation: current repository requests `fnval=0`, expects `durl`, and explicitly fails when the API returns DASH. The current implementation therefore does not deliver DASH/high-resolution DASH downloads.

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
- The current download engine delegates a single URL to Android DownloadManager. The app does not own a full resumable DASH/muxing pipeline.
- Subtitle export currently keeps BCC JSON; danmaku export currently keeps XML. Neither matches BVD's SRT/CSV export.
- Local resources page is a placeholder that describes the Download directory.
- The current player, account content pages, search, media processing, and most settings are absent.

## Suggested implementation order

1. Core playback stream pipeline: DASH parsing, independent video/audio download, merge, quality/codec negotiation.
2. Real task engine: persistent task model, progress, pause/resume, retry, process recovery, batch actions.
3. Bangumi EP/SS resolution and batch episode download.
4. QR login with QR image, clickable confirmation URL, polling, and secure session handling.
5. Correct SRT and CSV export.
6. Account content and search.
7. Local resource library, video/audio players, media conversion, and remaining settings.

Do not mark an item complete until its Kotlin implementation, UI connection, and a meaningful test/build path exist.
