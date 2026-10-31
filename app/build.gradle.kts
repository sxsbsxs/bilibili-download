plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sxsbsxs.bilibilidownload"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sxsbsxs.bilibilidownload"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // BVD-style mixed UI foundation: XML/ViewBinding/DataBinding + Compose.
    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res/views/activity",
                "src/main/res/views/fragment",
                "src/main/res/views/include",
                "src/main/res/views/dialog",
                "src/main/res/views/item"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":feature:home"))
    implementation(project(":service:download"))
    implementation(project(":notification"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.tooling)
    // BVD-inspired data, networking, media and preference stack.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.google.gson)
    implementation(libs.glide)
    implementation(libs.jsoup)
    implementation(libs.kotpref)
    implementation(libs.eventbus)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coroutines.android)
}
