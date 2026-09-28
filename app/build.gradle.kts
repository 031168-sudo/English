plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.english.pronunciation"
    compileSdk = 35

    defaultConfig {
        // The store identity: once published it can never change, or every
        // install becomes a different app and loses its data.
        applicationId = "ru.sayword.english"
        minSdk = 26
        targetSdk = 35
        // Every upload to a store needs a higher versionCode; CI numbers its
        // runs, so each build it makes is newer than the last.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"

        // The speech library ships a native blob per architecture; every phone
        // this app targets is 64-bit ARM, and shipping only that keeps the APK
        // from doubling in size.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // The release key never lives in the repository: CI decodes it from the
    // repository secrets into a temporary file and passes its path in here.
    val releaseKeystore = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Shrinking is off: the speech library reaches its native code
            // through JNA reflection, which R8 would strip without rules
            // that could only be verified on a device.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
