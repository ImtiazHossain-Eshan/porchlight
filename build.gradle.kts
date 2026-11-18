plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
    // Pins the Kotlin compiler; AGP 9 compiles Kotlin itself, so this is never applied.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// The web build's tools (Node.js, Yarn, Binaryen) resolve through the repositories declared in
// settings.gradle.kts; this stops the Kotlin plugin from adding its own, which settings forbids.
allprojects {
    plugins.whenPluginAdded {
        extensions.extensionsSchema.forEach { schema ->
            val spec = extensions.findByName(schema.name)
            if (spec is org.jetbrains.kotlin.gradle.targets.js.EnvSpec<*>) spec.downloadBaseUrl.set(null as String?)
        }
    }
}
