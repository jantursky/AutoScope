plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jantursky.autoscope.spike.media"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jantursky.autoscope.spike.media"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.media)
}
