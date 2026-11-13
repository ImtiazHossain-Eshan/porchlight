plugins {
    id("com.android.application") version "9.4.1" apply false
    // Pins the Kotlin compiler; AGP 9 compiles Kotlin itself, so this is never applied.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
