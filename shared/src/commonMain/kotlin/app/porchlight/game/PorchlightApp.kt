package app.porchlight.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * The whole game. Every platform's host (the Android activity, the iOS view controller, the web
 * page) shows this and nothing else; [platform] is how the game reaches the phone or browser.
 */
@Composable
fun PorchlightApp(platform: Platform, debug: DebugLaunch? = null) {
    val store = remember(platform) {
        Gpu.shaders = platform.shaders
        Gpu.blur = platform.blur
        if (debug != null) {
            Gpu.quality = debug.quality.coerceIn(0, 2)
            Gpu.fakeTilt = debug.fakeTilt
        }
        Store(platform.storage).also { store -> debug?.let { applyDebug(store, it) } }
    }
    val sfx = remember(platform) { Sfx() }
    DisposableEffect(sfx) {
        sfx.start(platform)
        onDispose { sfx.release() }
    }
    CompositionLocalProvider(LocalPlatform provides platform) {
        ProvideFaces {
            GameScreen(store, sfx, debug?.screen)
        }
    }
}

private fun applyDebug(store: Store, debug: DebugLaunch) {
    debug.setXp?.let { xp -> store.change { it.copy(xp = xp.coerceAtLeast(0)) } }
    debug.level?.let { level ->
        val n = level.coerceIn(1, LEVELS.size)
        store.change {
            it.copy(
                reached = maxOf(it.reached, n),
                current = n,
                chapters = maxOf(it.chapters, levelAt(n).chapter),
                levelStars = it.levelStars.mapIndexed { i, s -> if (i < n - 1) maxOf(s, 1) else s },
                tutorialDone = true,
            )
        }
    }
}
