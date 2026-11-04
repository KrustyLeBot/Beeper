plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.vigie"
    // GeckoView (the Firefox engine) is built against Android 17 (API 37.2).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.vigie"
        minSdk = 26
        targetSdk = 35
        // Grows with every CI run so each new APK installs over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    signingConfigs {
        // Fixed release key, injected by CI from the `release` environment's secrets. Never committed.
        val keystore = System.getenv("VIGIE_KEYSTORE")
        if (keystore != null && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("VIGIE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VIGIE_KEY_ALIAS") ?: "vigie"
                keyPassword = System.getenv("VIGIE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // GeckoView alone is ~90 MB: R8 keeps the rest small.
            isMinifyEnabled = true
            isShrinkResources = true
            // A local build without the key is signed with the SDK's debug key, so it still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // Compressed native libraries: about half the APK size, unpacked once at install.
        jniLibs { useLegacyPackaging = true }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.16.0")
    // Firefox's engine, so that real extensions (uBlock Origin, Consent-O-Matic) run in every tab.
    // Pinned on purpose: bump it with each Firefox release for its security fixes.
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261102144711")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    testImplementation("junit:junit:4.13.2")
    // org.json is only a stub on the JVM; the real one runs the file format tests.
    testImplementation("org.json:json:20240303")
}
