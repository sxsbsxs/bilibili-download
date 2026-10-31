# Bilibili Download

An Android Bilibili downloader built with Kotlin and Jetpack Compose.

## Project direction

This repository is being rebuilt as a native Android application. Its package organization and Gradle/CI layout take inspiration from the modular structure of BVD, while application code is independently maintained.

## Planned architecture

- `common/model`: shared data models
- `common/network`: Bilibili API and HTTP clients
- `common/storage`: encrypted credential storage and file handling
- `common/download`: stream download and media muxing
- `feature/home`, `feature/login`, `feature/downloads`, `feature/profile`: UI features
- `service` and `notification`: background download execution and notifications

## Build

The project uses the Gradle wrapper and GitHub Actions to build a debug APK. See the workflow output for the actual build result.

## Scope and permissions

The application only requests media streams and quality levels available to the current visitor/account. It does not bypass paid content, regional restrictions, DRM, or account entitlements.
