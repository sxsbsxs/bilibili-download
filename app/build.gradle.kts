plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.sxsbsxs.bilibilidownload"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sxsbsxs.bilibilidownload"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":common"))
    implementation(project(":service:download"))
    implementation(project(":notification"))
    implementation(libs.okhttp)
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
}
