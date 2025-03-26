plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jamie.nodica"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jamie.nodica"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
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
        compose = true
    }
}

dependencies {

    // Google Fonts support for Compose (e.g., Inter, Poppins)
    implementation(libs.androidx.ui.text.google.fonts)

    // Compose essentials
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Google Play Services - required for font provider
    implementation(libs.play.services.base)

    // (Optional) Splash screen & dynamic theming for Android 12+
    implementation(libs.androidx.core.splashscreen)

    // Koin (DI for Android + Compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

// Supabase Kotlin SDK (Auth + PostgREST + Realtime + Storage)
    implementation(libs.postgrest.kt)
    implementation(libs.auth.kt)
    implementation(libs.realtime.kt)
    implementation(libs.storage.kt)
    implementation(libs.gotrue.kt)

// Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)

// Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

// kotlinx-datetime (timestamp parsing, etc.)
    implementation(libs.kotlinx.datetime)

// Coil for image loading in Compose
    implementation(libs.coil.compose)

// Timber for logging
    implementation(libs.timber)


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}