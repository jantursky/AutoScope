plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jantursky.autoscope"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jantursky.autoscope"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.car.app)
    // Dispatchers.Main for AppContainer and the Activity; core only brings coroutines-core.
    implementation(libs.kotlinx.coroutines.android)
    // CarHardwareManager on Android Auto lives in app-projected (androidx.car.app docs, "Car Hardware APIs").
    implementation(libs.androidx.car.app.projected)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
