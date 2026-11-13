import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.carlight.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carlight.launcher"
        minSdk = 27          // Android 8.1 (Topway TS7)
        targetSdk = 27       // Sin restricciones nuevas de background; no se publica en Play
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "armeabi-v7a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Instalación por ADB: se firma con la clave debug.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        aidl = true
    }

    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        abortOnError = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Sin AndroidX ni AppCompat a propósito: el presupuesto de RAM es ≤ 40 MB.
dependencies {}
