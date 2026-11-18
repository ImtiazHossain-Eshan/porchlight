import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val composeVersion = "1.12.2"

kotlin {
    android {
        namespace = "app.porchlight.game.shared"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
        // The fonts live in composeResources; Android reads them as assets.
        androidResources { enable = true }
    }

    // No iosX64: Compose Multiplatform 1.12 stopped publishing it (Intel-Mac simulators), and
    // GitHub's macos-latest runners are Apple silicon anyway.
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Compose's renderer (skiko) needs a browser, so the tests run in headless Chrome.
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
        // Bundling the tests with webpack is what loads skiko for them.
        binaries.executable()
        compilerOptions.optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // iOS and the web both draw with Skia (skiko), so they share the shader and image code.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val skikoMain = create("skikoMain") { dependsOn(commonMain.get()) }
        iosMain.get().dependsOn(skikoMain)
        wasmJsMain.get().dependsOn(skikoMain)

        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:atomicfu:0.32.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}

compose.resources {
    packageOfResClass = "app.porchlight.game.resources"
    publicResClass = false
}

// The Android checks stay one command: `./gradlew testDebugUnitTest` runs the shared tests too.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the shared tests on the JVM, as part of the Android unit tests."
    dependsOn("testAndroidHostTest")
}
