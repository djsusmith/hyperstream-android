import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localAapt2 = File("/data/user/0/com.agy/files/usr/bin/aapt2")
if (localAapt2.exists()) {
    project.extensions.extraProperties.set("android.aapt2FromMavenOverride", localAapt2.absolutePath)
}

val upstreamVersionFile = rootProject.file("../upstream/UPSTREAM_VERSION.json")
val upstreamCommitSha: String = if (upstreamVersionFile.exists()) {
    Regex("\"commit\"\\s*:\\s*\"([^\"]+)\"")
        .find(upstreamVersionFile.readText())
        ?.groupValues
        ?.getOrNull(1)
        ?.take(12)
        ?: "local"
} else {
    "local"
}

android {
    namespace = "com.hyperstream.downloadmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hyperstream.downloadmanager"
        minSdk = 28
        targetSdk = 35
        versionCode = 64
        versionName = "0.6.4"
        buildConfigField("String", "UPSTREAM_COMMIT", "\"$upstreamCommitSha\"")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    val keystoreFile = file("hyperstream-release.keystore")
    val hasReleaseKeystore = keystoreFile.exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = "hyperstream2026"
                keyAlias = "hyperstream"
                keyPassword = "hyperstream2026"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    debugImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
