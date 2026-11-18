package app.porchlight.game

import androidx.compose.runtime.Composable
import platform.Foundation.NSLog
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIScreen
import kotlin.time.TimeSource

private val start = TimeSource.Monotonic.markNow()

internal actual fun now(): Long = start.elapsedNow().inWholeMilliseconds

internal actual fun log(message: String) {
    NSLog("Porchlight: %@", message)
}

/** iOS has no back key; every screen has its own back or close button. */
@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
internal actual fun systemReduceMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

@Composable
internal actual fun displayRefreshRate(): Float = UIScreen.mainScreen.maximumFramesPerSecond.toFloat().takeIf { it > 1f } ?: 60f

@Composable
internal actual fun rememberChangingConfiguration(): () -> Boolean = { false }
