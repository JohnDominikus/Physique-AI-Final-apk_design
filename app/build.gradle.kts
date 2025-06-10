plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.physiqueaiapkfinal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.physiqueaiapkfinal"
        minSdk = 28
        targetSdk = 33
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src\\main\\assets", "src\\main\\assets")
            }
        }
    }
}

dependencies {
    // Firebase (using BoM to manage versions)
    implementation(platform("com.google.firebase:firebase-bom:31.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    // Firebase Kotlin Extensions
    implementation("com.google.firebase:firebase-auth-ktx:22.1.2")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.2")

    // AndroidX + Google
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.10.0")
    implementation("com.google.guava:guava:30.1-android")
    implementation("com.google.guava:guava:32.1.2-jre")

    implementation("com.github.bumptech.glide:glide:4.15.1")

    //google fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.8")
    // CameraX
    implementation("androidx.camera:camera-camera2:1.0.0")
    implementation("androidx.camera:camera-lifecycle:1.0.0")
    implementation("androidx.camera:camera-view:1.0.0")
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Lifecycle & Fragment
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    // Media3
    implementation(libs.androidx.media3.common.ktx)

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.animation.core.android)
    implementation(libs.androidx.databinding.runtime)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")


    // ML Kit dependencies
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
    implementation("com.google.android.odml:image:1.0.0-beta1")
    implementation("com.google.mlkit:camera:16.0.0-beta3")
    // Navigation & Layout
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime.ktx)
    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("com.google.android.material:material:1.6.0")

    // Material & AppCompat (aliases)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation (libs.androidx.core)

    // CameraX dependencies
    val camerax_version = "1.3.0-beta01"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
  //  implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}