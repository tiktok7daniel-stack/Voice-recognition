plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tiktok7daniel.voceitaliana"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tiktok7daniel.voceitaliana"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "2.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        ndk {
            // Real phones only; keeps the APK small.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            // CI smoke test runs on an x86_64 emulator.
            if (project.hasProperty("emulator")) abiFilters += "x86_64"
        }
    }

    signingConfigs {
        create("release") {
            // A fixed key committed with the project so every CI build can update
            // the previous install. Replace it before publishing to the Play Store.
            storeFile = file("voce-release.jks")
            storePassword = "voceitaliana"
            keyAlias = "voce"
            keyPassword = "voceitaliana"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        noCompress += "onnx"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // sherpa-onnx (k2-fsa): on-device ASR engine, fetched by CI from its GitHub release.
    implementation(files("libs/sherpa-onnx.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
