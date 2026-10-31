# Bilibili Download

A clean-room Android Bilibili downloader using Kotlin and Jetpack Compose.

The new codebase is independent of the previous BiliDownload-KT business implementation. It uses a small Android multi-module foundation: app for UI, core for models/API/download integration. The project organization is informed by BVD's structure only; no BVD business code is copied.

Initial functions: BV/AV parsing, visitor-accessible video metadata, visitor-accessible progressive stream lookup, and Android DownloadManager handoff. Quality availability remains controlled by Bilibili. No DRM, paywall, regional, or account entitlement bypass is included.

GitHub Actions builds a debug APK using JDK 17, Gradle 8.9, and AGP 8.7.3.
