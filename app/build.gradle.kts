plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.visa"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.visa"
        minSdk = 26
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
    val cameraxVersion = "1.6.0"

    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    // To recognize Latin script
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // To recognize Chinese script
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // To recognize Japanese script
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
// To recognize Korean script
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}