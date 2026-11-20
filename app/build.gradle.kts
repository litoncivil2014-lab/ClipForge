plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.clipforge"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.clipforge"
        minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "1.0"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    val m = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$m")
    implementation("androidx.media3:media3-ui:$m")
    implementation("androidx.media3:media3-transformer:$m")
    implementation("androidx.media3:media3-effect:$m")
    implementation("androidx.media3:media3-common:$m")
}
