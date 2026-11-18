plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jantursky.autoscope.spike.car"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jantursky.autoscope.spike.car"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    // Spike A tries each car app category separately, so both can be installed side by side.
    flavorDimensions += "category"
    productFlavors {
        create("iot") {
            dimension = "category"
            applicationIdSuffix = ".iot"
            manifestPlaceholders["carCategory"] = "androidx.car.app.category.IOT"
        }
        create("poi") {
            dimension = "category"
            applicationIdSuffix = ".poi"
            manifestPlaceholders["carCategory"] = "androidx.car.app.category.POI"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.car.app)
}
