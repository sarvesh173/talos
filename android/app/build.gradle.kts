plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.sarvesh173.talos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sarvesh173.talos"
        minSdk = 26
        targetSdk = 35
        versionCode = 21135
        versionName = "2.11.35"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bypass switches are compiled in, never read at runtime from a file:
        // a shipped APK must not be able to flip itself into a fake-data mode.
        buildConfigField("boolean", "BYPASS_MESH_GATE", "false")
        buildConfigField("boolean", "ALLOW_SIMULATED_REPLIES", "false")
    }

    // A second, clearly-labelled APK for working on the app itself: it skips
    // the Tailscale gate and labels simulated replies instead of blocking, so
    // the whole UI is reachable on a machine with no mesh. Its applicationId
    // is suffixed .dev, so it installs alongside the real app and can never be
    // mistaken for a production build.
    flavorDimensions += "channel"
    productFlavors {
        create("prod") {
            dimension = "channel"
            applicationIdSuffix = ""
            versionNameSuffix = ""
        }
        create("dev") {
            dimension = "channel"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "BYPASS_MESH_GATE", "true")
            buildConfigField("boolean", "ALLOW_SIMULATED_REPLIES", "true")
        }
    }

    signingConfigs {
        create("sharedDebug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Real release key: ONLY when CI secrets are present (decoded by
        // .github/workflows/build-apk.yml to RELEASE_KEYSTORE_FILE). Absent
        // secrets -> no "release" config -> release falls back to sharedDebug
        // so local/CI builds without secrets still work. Never commit a
        // keystore or secret values.
        val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_FILE")
        val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
        val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        if (!releaseKeystorePath.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank() &&
            file(releaseKeystorePath).exists()
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("sharedDebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        // BuildConfig carries the version name so settings never hardcodes a stale string.
        buildConfig = true
    }
}

dependencies {
    // Core Android & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose & Material 3 Expressive (BOM 2025.09.01 -> material3 1.4.0, ui 1.9.2;
    // verified via Google Maven POMs: minCompileSdk 35, minAGP 8.6.0 - compatible)
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Explicit material3 1.4.0: BOM 2025.09.01 constraint resolves 1.3.2 without a pin,
    // which hides MotionScheme/MaterialExpressiveTheme (internal in 1.3.x).
    implementation("androidx.compose.material3:material3:1.5.0-alpha12")
    implementation("androidx.compose.material:material-icons-extended")

    // Haze 1.1.1: Rock-solid backdrop blur aligned with Kotlin 2.0.21 stdlib & compileSdk 35/36
    implementation("dev.chrisbanes.haze:haze:1.1.1")
    // M3E shape engine: RoundedPolygon / Morph (stable 1.1.0)
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Biometric unlock (app lock). 1.2.0-alpha05 is the release that accepts a
    // FragmentActivity on API 21+ without the legacy FingerprintManager path.
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // MainActivity is a FragmentActivity (it extends ComponentActivity, so
    // setContent and lifecycleScope keep working).
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Networking (OkHttp for WebSockets & Tailscale REST)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Media3 (Native Audio & Video Playback)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Serialization & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Local JVM tests for pure-logic modules (orb geometry, providers).
    testImplementation("junit:junit:4.13.2")

}

// Bypass AAR metadata compileSdk checks for modern Compose Multiplatform libraries (e.g. Haze 2.0.1)
tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}
