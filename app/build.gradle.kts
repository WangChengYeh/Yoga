plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yogaflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yogaflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.8.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    androidResources {
        // Godot 4 exports remapped scene and import artifacts under assets/.godot.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Pinned to a verified CameraX line with ImageAnalysis RGBA_8888 support.
    val cameraXVersion = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // Pinned: avoids relying on older overload availability for ImageProcessingOptions.
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    
    // Keep Android runtime aligned with the Godot 4.6 exported project.pck.
    implementation("org.godotengine:godot:4.6.2.stable")

    // WebSocket bridge from Android/Kotlin to the embedded Godot avatar.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
