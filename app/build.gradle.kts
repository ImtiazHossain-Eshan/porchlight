plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes these in; local builds fall back to 1 / 0.6.6.
val appVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val appVersionName = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "0.6.6"

// Google's own sample AdMob ids, which only ever serve test ads. Debug builds always use these;
// a release build uses the owner's real ids from the environment, or these if none are given.
val testAdMobApp = "ca-app-pub-3940256099942544~3347511713"
val testRewardedUnit = "ca-app-pub-3940256099942544/5224354917"
val releaseAdMobApp = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() } ?: testAdMobApp
val releaseRewardedUnit = System.getenv("ADMOB_REWARDED_ID")?.takeIf { it.isNotBlank() } ?: testRewardedUnit

// The licence key from Play Console (Monetize > Monetization setup), to check purchase signatures on
// the phone. Optional: without it purchases are still granted, unchecked (see docs/ECONOMY.md).
val playLicenseKey = System.getenv("PLAY_LICENSE_KEY")?.trim().orEmpty()

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
        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
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
        debug {
            manifestPlaceholders["admobAppId"] = testAdMobApp
            buildConfigField("String", "REWARDED_AD_UNIT", "\"$testRewardedUnit\"")
        }
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = releaseAdMobApp
            buildConfigField("String", "REWARDED_AD_UNIT", "\"$releaseRewardedUnit\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    // Rewarded ads (opt-in only) and the consent form for 16+ players where the law asks for one.
    // Both are on Google Play's Families self-certified ads SDK list.
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    // Fireflies packs, the starter pack and Supporter, through Google Play.
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    testImplementation("junit:junit:4.13.2")
}
