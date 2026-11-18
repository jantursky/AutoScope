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
        versionCode = 2
        versionName = "1.1.0"
    }

    // Release signing comes from the environment, so a plain checkout (and F-Droid, which signs
    // with its own key) builds without any secrets.
    val keystorePath = providers.environmentVariable("AUTOSCOPE_KEYSTORE").orNull
    if (keystorePath != null) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = providers.environmentVariable("AUTOSCOPE_KEYSTORE_PASSWORD").get()
            keyAlias = "autoscope"
            keyPassword = providers.environmentVariable("AUTOSCOPE_KEYSTORE_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // Voice search is a Play requirement for media apps. This one never plays anything and is not on Play.
        disable += setOf("MissingIntentFilterForMediaSearch", "MissingOnPlayFromSearch")
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
    // MediaBrowserServiceCompat: the media entry point for hosts that refuse sideloaded Car App Library apps.
    implementation(libs.androidx.media)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
