plugins { alias(libs.plugins.android.app); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
 namespace="com.sxsbsxs.bilibilidownload"; compileSdk=35
 defaultConfig { applicationId="com.sxsbsxs.bilibilidownload"; minSdk=23; targetSdk=35; versionCode=2; versionName="0.2.0" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }; buildFeatures { compose=true }
}
dependencies {
 implementation(project(":common"))
 implementation(project(":feature:home"))
 implementation(project(":service:download"))
 implementation(project(":notification"))
 implementation(libs.activity.compose)
 implementation(platform(libs.compose.bom))
 implementation(libs.compose.material3)
 debugImplementation(libs.compose.tooling)
}
