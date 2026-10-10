plugins {
    id("com.android.application")
}

android {
    namespace = "com.horrorstack.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.horrorstack.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5"
        manifestPlaceholders["appLabel"] = "HorrorStack TV"
    }

    buildTypes {
        create("sandbox") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["appLabel"] = "HorrorStack Test"
        }

        // Separate Android phone preview: it does not overwrite the owner's
        // existing HorrorStack TV or older debug/test installs.
        create("phonePreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".phonepreview"
            versionNameSuffix = "-phone-preview"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["appLabel"] = "HorrorStack Preview"
            // Moto G (2026) is arm64. Avoid the much larger universal APK
            // whose unused x86 / 32-bit VLC libraries waste phone storage.
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }
}

dependencies {
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
}
