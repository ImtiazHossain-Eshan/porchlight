plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes these in; local builds fall back to 1 / 0.6.6.
val appVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val appVersionName = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "0.6.6"

// Release signing is optional. Without these the release APK is built unsigned.
val keystorePath: String? = System.getenv("PORCHLIGHT_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.porchlight.game"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.porchlight.game"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PORCHLIGHT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PORCHLIGHT_KEY_ALIAS")
                keyPassword = System.getenv("PORCHLIGHT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")

    implementation(project(":shared"))

    testImplementation("junit:junit:4.13.2")
}
