package app.porchlight.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private fun performanceNow(): Double = js("performance.now()")

private fun consoleLog(message: String): Unit = js("console.log('Porchlight: ' + message)")

private fun prefersReducedMotion(): Boolean = js("!!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)")

internal actual fun now(): Long = performanceNow().toLong()

internal actual fun log(message: String) = consoleLog(message)

/** The browser's back button leaves the page; every screen has its own back or close button. */
@Composable
internal actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
internal actual fun systemReduceMotion(): Boolean = remember { prefersReducedMotion() }

@Composable
internal actual fun displayRefreshRate(): Float = 60f

@Composable
internal actual fun rememberChangingConfiguration(): () -> Boolean = { false }
