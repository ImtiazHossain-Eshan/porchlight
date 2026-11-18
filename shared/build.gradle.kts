import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "app.porchlight.game.shared"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        compilerOptions.optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}

// The Android checks stay one command: `./gradlew testDebugUnitTest` runs the shared tests too.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the shared tests on the JVM, as part of the Android unit tests."
    dependsOn("testAndroidHostTest")
}
