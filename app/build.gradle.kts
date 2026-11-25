plugins {
    alias(libs.plugins.android.application)
    jacoco
}

android {
    namespace = "com.jantursky.autoscope"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jantursky.autoscope"
        minSdk = 28
        targetSdk = 37
        versionCode = 6
        versionName = "1.2.0"
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
        debug {
            enableUnitTestCoverage = true
        }
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

// Line coverage of the unit-testable code. Views, car screens, services and the Activity need a device,
// so they are left out; see the exclude list.
val coverageClasses = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
    exclude(
        "**/R.class", "**/R$*.class", "**/BuildConfig.class",
        "**/phone/*View*.class", "**/phone/MainActivity*.class", "**/phone/OverflowMenu.class",
        "**/phone/ConfirmDialog*.class", "**/phone/HelpPage*.class", "**/phone/Ui*.class",
        "**/car/*Screen*.class", "**/car/AutoScopeCarService*.class", "**/car/Android*.class",
        "**/media/**", "**/AutoScopeApp*.class",
    )
}
val coverageExec = layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(coverageExec)
    reports { xml.required = true; html.required = true }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(coverageExec)
    violationRules {
        rule { limit { counter = "LINE"; minimum = "0.66".toBigDecimal() } }
    }
}
