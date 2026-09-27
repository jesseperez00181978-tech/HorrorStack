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
        versionCode = 4
        versionName = "1.0.3"
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
