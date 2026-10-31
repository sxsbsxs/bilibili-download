# Bilibili Download

Native Android Bilibili downloader written in Kotlin and Jetpack Compose.

## Architecture

The source tree uses a layered, feature-oriented layout inspired by the *organization* of BVD. Its application implementation is independently maintained.

- `common/model`: shared video, stream and account models
- `common/network`: Bilibili API and authorization
- `common/download`: stream download and media muxing
- `common/storage`: encrypted credential storage
- `feature/compose`: Compose UI
- `service` / `notification`: planned background execution and task notifications

## Build

GitHub Actions will build a debug APK with JDK 17 and Gradle 8.9. The workflow uses the Gradle installation provided by setup-gradle because the source repository did not include a wrapper JAR.

The app only exposes media and quality levels available to the current visitor/account. It does not bypass paywalls, DRM, regional restrictions or account entitlements.
