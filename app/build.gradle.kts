plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.thatmarcel.apps.railochrone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thatmarcel.apps.railochrone"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "0.0.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                abiFilters += setOf("x86_64", "arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.mapbox)
    implementation(libs.asynclayoutinflater)
    implementation(libs.okhttp)
    implementation(libs.gson)
}