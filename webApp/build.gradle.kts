import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The web build: the shared game in a page, for Safari on iPhone (18.2+) and any current browser.
//   ./gradlew :webApp:wasmJsBrowserDevelopmentRun     (a local server with reload)
//   ./gradlew :webApp:wasmJsBrowserDistribution       (the site, in webApp/build/dist/wasmJs/productionExecutable)
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("porchlight")
        browser {
            commonWebpackConfig { outputFileName = "porchlight.js" }
        }
        binaries.executable()
        compilerOptions.optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.ui:ui:1.12.2")
        }
    }
}
