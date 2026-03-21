plugins {
    alias(libs.plugins.android.application)
    id("com.chaquo.python")
}

android {
    namespace = "soft.shadlv.twp_rewritekts"
    compileSdk = 35

    defaultConfig {
        applicationId = "soft.shadlv.twp_rewritekts"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "x86", "armeabi-v7a")
        }
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
    splits {
        abi {
            // Enables the ABI splits mechanism
            isEnable = true

            // Clears the default list of ABIs
            reset()

            // Specifies which ABIs to include in separate APKs
            // You can include "armeabi-v7a", "arm64-v8a", "x86", "x86_64"
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

            // Optional: If set to false (default), a universal APK containing all ABIs is not generated.
            // If set to true, a universal APK will also be built alongside the specific ones.
            isUniversalApk = true
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("cryptography")
        }
        staticProxy("main")
    }
}