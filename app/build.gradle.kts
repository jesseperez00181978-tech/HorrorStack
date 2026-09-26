plugins {
    id("com.android.application")
}

android {
    namespace = "com.horrorstack.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.horrorstack.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
}
