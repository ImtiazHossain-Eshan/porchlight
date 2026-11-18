package app.porchlight.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
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

/** iOS Settings › Accessibility › Motion › Reduce Motion, followed live while the game is open. */
@Composable
internal actual fun systemReduceMotion(): Boolean {
    var reduce by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(UIAccessibilityReduceMotionStatusDidChangeNotification, null, NSOperationQueue.mainQueue) { _ ->
            reduce = UIAccessibilityIsReduceMotionEnabled()
        }
        onDispose { center.removeObserver(observer) }
    }
    return reduce
}

@Composable
internal actual fun displayRefreshRate(): Float = UIScreen.mainScreen.maximumFramesPerSecond.toFloat().takeIf { it > 1f } ?: 60f

@Composable
internal actual fun rememberChangingConfiguration(): () -> Boolean = { false }
