package app.porchlight.game

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.LocalLifecycleOwner
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

/** The largest text size the game follows: Android's top font scale, so iOS Dynamic Type matches it. */
private const val MAX_FONT_SCALE = 2f

/**
 * The iOS host's view controller: the whole game. [onImmersive] hears when a round starts and ends,
 * so the SwiftUI side can hide the status bar and home indicator and defer edge swipes.
 * [arguments] are the process's launch arguments; debug builds read `--level N`, `--setXp N`,
 * `--quality N` and `--screen Daily` from them, as the Android debug build reads intent extras.
 */
@OptIn(ExperimentalNativeApi::class)
@Suppress("unused", "FunctionName") // Called from Swift.
fun MainViewController(arguments: List<String>, onImmersive: (Boolean) -> Unit): UIViewController {
    val platform = IosPlatform(onImmersive)
    val debug = if (kotlin.native.Platform.isDebugBinary) debugLaunch(arguments) else null
    val lifecycle = AppLifecycle()
    return ComposeUIViewController {
        val density = LocalDensity.current
        val capped = remember(density) {
            Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
        }
        CompositionLocalProvider(LocalDensity provides capped, LocalLifecycleOwner provides lifecycle) {
            PorchlightApp(platform, debug)
        }
    }
}

private fun debugLaunch(arguments: List<String>): DebugLaunch? {
    fun value(name: String) = arguments.indexOf("--$name").takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }
    if (arguments.none { it.startsWith("--") }) return null
    return DebugLaunch(
        quality = value("quality")?.toIntOrNull() ?: 0,
        setXp = value("setXp")?.toIntOrNull(),
        level = value("level")?.toIntOrNull(),
        screen = value("screen"),
    )
}
