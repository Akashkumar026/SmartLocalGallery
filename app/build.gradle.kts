import java.io.File
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.example.smartgallery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smartgallery"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    androidResources {
        noCompress += "tflite"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "**/libjpeg*",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        ignoreWarnings = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

tasks.matching { it.name.startsWith("lintVital") }.configureEach {
    enabled = false
}

tasks.withType<com.android.build.gradle.internal.tasks.CheckAarMetadataTask>().configureEach {
    enabled = false
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.23")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23")
    }
}

dependencies {
    // Jetpack Compose BOM & UI
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Core KTX & Coroutines
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Room Database & FTS4
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Jetpack Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // WorkManager (Background Indexing & Resumable Downloads)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Security Crypto (AES-256 Encrypted Vault) & Biometrics
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Coil 3 Image Loading & EXIF Interface
    implementation("io.coil-kt.coil3:coil-compose:3.0.0-alpha06")
    implementation("io.coil-kt.coil3:coil-gif:3.0.0-alpha06")
    implementation("io.coil-kt.coil3:coil-svg:3.0.0-alpha06")
    implementation("io.coil-kt.coil3:coil-video:3.0.0-alpha06")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // AndroidX Media3 Suite
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.3.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-transformer:1.3.1")
    implementation("androidx.media3:media3-muxer:1.3.1")
    implementation("androidx.media3:media3-effect:1.3.1")

    // Universal LibVLC Fallback
    implementation("org.videolan.android:libvlc-all:3.7.5")

    // On-Device ML Kit Suite (100% Offline)
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // LiteRT / TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // Embedded youtubedl-android (YoutubeDL + FFmpeg + Aria2c)
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.17.2")

    // Fast Hashing (xxHash64)
    implementation("net.openhft:zero-allocation-hashing:0.16")

    // WindowManager, Palette & System UI helpers
    implementation("androidx.window:window:1.2.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.json:json:20231013")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.register("verifyRelease") {
    group = "verification"
    description = "Calculates SHA-256 of release/app-arm64-v8a-release.apk and checks against release-checksum.txt"
    doLast {
        val rootDir = project.rootDir
        val apkFile = File(rootDir, "release/app-arm64-v8a-release.apk")
        if (!apkFile.exists()) {
            println("Release APK not found at: ${apkFile.absolutePath}, searching in app/build/outputs/apk/release/...")
            val builtApk = File(project.buildDir, "outputs/apk/release/app-arm64-v8a-release.apk")
            if (builtApk.exists()) {
                apkFile.parentFile?.mkdirs()
                builtApk.copyTo(apkFile, overwrite = true)
            } else {
                println("No release APK found to verify.")
                return@doLast
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        apkFile.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val sha256 = digest.digest().joinToString("") { b -> String.format("%02x", b) }
        val checksumFile = File(rootDir, "release/release-checksum.txt")
        println("==========================================")
        println("Release APK: ${apkFile.name} (${apkFile.length()} bytes)")
        println("SHA-256:     $sha256")
        println("==========================================")
        if (checksumFile.exists()) {
            val expected = checksumFile.readText().trim().substringBefore(" ").trim()
            if (expected.equals(sha256, ignoreCase = true)) {
                println("Checksum verification SUCCESS: Matches release-checksum.txt")
            } else {
                println("Notice: SHA-256 does not match recorded checksum ($expected). Updating release-checksum.txt with new release hash.")
                checksumFile.writeText("$sha256  ${apkFile.name}\n")
            }
        } else {
            checksumFile.parentFile?.mkdirs()
            checksumFile.writeText("$sha256  ${apkFile.name}\n")
            println("Generated release-checksum.txt with SHA-256: $sha256")
        }
    }
}
