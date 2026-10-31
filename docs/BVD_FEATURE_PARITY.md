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

- [~] Manual Cookie entry remains as a fallback.
- [~] QR-code generation uses ZXing locally; the QR payload is never sent to a third-party QR image service.
- [~] Official passport QR polling handles waiting, scanned/awaiting confirmation, expired and successful states.
- [~] Successful QR login extracts the Bilibili session Cookie and stores it with AES-GCM under an Android Keystore key; logout clears the encrypted credential.
- [~] Account page queries the authenticated navigation/profile endpoint and displays the account name / UID.
- [ ] Password login.
- [ ] Favorites, watch history, watch-later, recent likes, personal uploads.

## 4. Stream formats and downloads

- [x] Progressive durl stream lookup and app-owned single-stream download.
- [~] DASH video track and audio track parsing via `fnval=4048`.
- [~] DASH separate-track downloads with HTTP Range resume, Content-Range offset checks, and ETag / Last-Modified If-Range validation.
- [~] Android MediaExtractor / MediaMuxer audio-video MP4 muxing; codec/container compatibility is device-dependent.
- [~] Quality list and selected-quality requests; 4K/8K/HDR/Dolby/AV1 availability still depends on B站 API response, account rights, source encoding and device support.
- [~] App-owned task lifecycle: encrypted task metadata, progress, pause/resume/retry, process recovery, removal, bounded network retries, and one automatic signed-URL refresh attempt when video identity metadata is available.
- [~] Foreground data-sync service with an ongoing notification keeps the shared coordinator alive while the WebView activity is backgrounded; active tasks are restored by the service after process recreation.
- [~] Task list status filters and multi-select batch pause / resume / remove. Pause/resume is supported for app-owned tasks; legacy DownloadManager tasks can only be removed.
- [ ] Batch download and quality-mismatch policy.
- [~] Completion publishes MP4 to Download/BilibiliDownload on Android 10+; older versions use app-specific Downloads storage.
- [~] Subtitle and danmaku exports still use Android DownloadManager and do not yet share the app-owned pause/resume/retry task engine.

Important limitations: task URLs are encrypted with Android Keystore, but Cookie values are intentionally not persisted. A recovered task may need a fresh stream URL if its signed URL has expired or the source requires an authenticated Cookie. The current muxer relies on device MediaExtractor support and is not a substitute for BVD's broader FFmpeg pipeline.

## 5. Subtitles and danmaku

- [x] Subtitle track list retrieval.
- [~] Official subtitle JSON is converted to SRT and saved as a UTF-8 file.
- [~] Danmaku XML is parsed and converted to CSV with time, mode, font size, color, timestamp, pool, user hash, row ID and content fields.

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
- The account UI supports Bilibili APP QR scanning and confirmation, manual Cookie fallback, encrypted Cookie persistence with Android Keystore, account name / UID lookup and logout. QR login still needs real-account device verification.
- Video downloads use a process-wide app-owned coordinator shared by the WebView bridge and a foreground data-sync service. It persists task state, encrypts stream URLs with Android Keystore, supports pause/resume/retry, status filtering and batch operations, and attempts to recover active tasks after process restart. DASH streams are muxed with Android MediaMuxer.
- Subtitle JSON is converted to SRT and danmaku XML to CSV; the exported text files are saved locally rather than being faked as DownloadManager URL tasks.
- Local resources page is a placeholder that describes the Download directory.
- The current player, account content pages, search, media processing, and most settings are absent.

## Suggested implementation order

1. Improve the DASH pipeline: codec compatibility, optional FFmpeg fallback, and real-device validation.
2. Expand EP/SS coverage, improve batch quality policy, and test real season/episode responses.
3. Add account content and search.
4. Build the local resource library and video/audio players.
5. Add media conversion and remaining settings.

Do not mark an item complete until its Kotlin implementation, UI connection, and a meaningful test/build path exist.


## 9. Bangumi and batch episode downloads (2026-10-31)

- [~] EP and SS identifiers are parsed from plain IDs and standard bangumi play URLs.
- [~] Season metadata and episode lists are loaded from the PGC season API, including returned section episodes.
- [~] Episode checkboxes, select-all/select-none, and per-episode quality selection are wired to Kotlin.
- [~] Batch requests resolve each selected episode independently and enqueue successful streams into the app-owned task coordinator; failures are reported per episode.
- [ ] Automatic refresh of season/episode metadata after API schema changes.
- [ ] Real-account verification for restricted episodes, region-limited content, and season section pagination.

The batch UI defaults each episode to "automatic highest available" and lets the user request the episode's quality list individually. A selected quality is resolved at enqueue time; permissions and available quality remain controlled by the Bilibili API.


## 10. Download engine resilience (2026-10-31)

- [~] Resume requests use Range and If-Range when ETag or Last-Modified is available.
- [~] A 206 response is appended only when Content-Range begins at the exact local file length and the saved validator has not changed.
- [~] If a server ignores Range (200), returns an invalid range, or reports 416, the affected partial file is safely reset rather than concatenated blindly.
- [~] Transient I/O failures retry up to three additional times with increasing delays while retaining a valid partial file.
- [~] HTTP 401/403/404/410 signed-stream failures can trigger one Bilibili playurl refresh using the persisted BV ID, CID, and quality.
- [~] Signed URLs, stream identity, HTTP validators, and per-track expected lengths are stored in the encrypted task record. Cookie values remain memory-only.
- [ ] Verify Range/validator behavior against multiple Bilibili CDN hosts and devices; server behavior is not uniform.
- [ ] Persist a secure authenticated session so a post-restart refresh can recover streams that require login.

Safety rule: when a refreshed signed URL has no comparable ETag or Last-Modified validator, existing partial bytes for that track are discarded before downloading from byte zero. The app never assumes two different signed URLs point to identical byte streams. No user-owned backend server is required; requests go directly from the Android app to Bilibili's media CDN.
