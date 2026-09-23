plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.english.pronunciation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.english.pronunciation"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // The speech library ships a native blob per architecture; every phone
        // this app targets is 64-bit ARM, and shipping only that keeps the APK
        // from doubling in size.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Offline speech recognition, model and all, bundled into the APK.
    implementation("com.alphacephei:vosk-android:0.3.47")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
