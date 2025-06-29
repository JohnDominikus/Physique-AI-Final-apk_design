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
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Performance optimizations
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
        
        // Enable crash reporting
        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Performance optimizations
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            versionNameSuffix = "-debug"
            
            // Enable crash reporting in debug
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
    
<<<<<<< HEAD
    // Packaging options
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }
    
=======
>>>>>>> 5511c1da00ed0814e5051b86e9cead76022c1b63
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
        }
    }
}

dependencies {
    // Firebase (using BoM to manage versions)
<<<<<<< HEAD
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
=======
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
>>>>>>> 5511c1da00ed0814e5051b86e9cead76022c1b63
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
<<<<<<< HEAD
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Firebase Kotlin Extensions
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // AndroidX + Google
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.guava:guava:32.1.2-jre")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // CameraX
    val camerax_version = "1.3.1"
=======
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Play Services
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // AndroidX Core
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)

    // UI Components
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")

    // CameraX
    val camerax_version = "1.4.2"
>>>>>>> 5511c1da00ed0814e5051b86e9cead76022c1b63
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

<<<<<<< HEAD
    // Lifecycle & Fragment
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Media3
    implementation("androidx.media3:media3-common:1.2.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // Data Binding
    implementation("androidx.databinding:databinding-runtime:8.2.0")
    
    // Activity
    implementation("androidx.activity:activity-ktx:1.8.2")

    // ML Kit dependencies
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
    implementation("com.google.android.odml:image:1.0.0-beta1")
    implementation("com.google.mlkit:camera:16.0.0-beta3")

    // Navigation & Layout
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
=======
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    // ML Kit
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
    implementation("com.google.android.odml:image:1.0.0-beta1")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Utilities
    implementation("com.google.guava:guava:32.1.2-android")
>>>>>>> 5511c1da00ed0814e5051b86e9cead76022c1b63

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}