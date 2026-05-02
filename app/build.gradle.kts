plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yogaflow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yogaflow"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
}
