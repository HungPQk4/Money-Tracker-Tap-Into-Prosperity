plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "vn.edu.usth.tip"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "vn.edu.usth.tip"
        minSdk = 24
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // needed for java.time on API < 26
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    
    // Jetpack Navigation
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")

    // Retrofit & Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.11.0")

    // MPAndroidChart — donut / pie / bar charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // CameraX
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    // ListenableFuture — dùng bởi ProcessCameraProvider.getInstance()
    implementation("androidx.concurrent:concurrent-futures:1.1.0")

    // ML Kit Text Recognition (Latin + Vietnamese)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // WorkManager — background sync with network constraint + exponential backoff
    implementation("androidx.work:work-runtime:2.9.0")

    // Encrypted storage for JWT token
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Java 8+ API desugaring for API < 26 (java.time.Instant, DateTimeFormatter)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}