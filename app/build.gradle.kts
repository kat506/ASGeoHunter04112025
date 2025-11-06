plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.geohunter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.geohunter"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    implementation("com.google.android.gms:play-services-maps:18.1.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // ML Kit Image Labeling (on-device)
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // ML Kit Face Detection (on-device)
    implementation("com.google.mlkit:face-detection:16.1.7")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}