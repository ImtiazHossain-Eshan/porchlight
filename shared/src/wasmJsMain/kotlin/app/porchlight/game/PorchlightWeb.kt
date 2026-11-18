package app.porchlight.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.porchlight.game.resources.Res
import app.porchlight.game.resources.alfa_slab_one
import app.porchlight.game.resources.fredoka
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

/**
 * The game in a web page. It waits for both fonts before the first frame (so no word ever shows in
 * the browser's own font), then runs [PorchlightApp] with a lifecycle that follows the page: a
 * hidden tab pauses, exactly as an Android app going to the background does.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun PorchlightWeb(platform: Platform) {
    val display by preloadFont(Res.font.alfa_slab_one)
    val body by preloadFont(Res.font.fredoka)
    val page = remember { PageLifecycle() }
    if (display == null || body == null) return
    LaunchedEffect(Unit) { pageReady() }
    CompositionLocalProvider(LocalLifecycleOwner provides page) {
        PorchlightApp(platform)
    }
}

/** RESUMED while the page is visible, CREATED while it is hidden or being left. */
private class PageLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = if (pageVisible()) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        onPageVisibility { visible ->
            registry.currentState = if (visible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        }
    }
}

private fun pageVisible(): Boolean = js("document.visibilityState === 'visible'")

private fun onPageVisibility(changed: (Boolean) -> Unit): Unit = js(
    """(function(){
        document.addEventListener('visibilitychange', function() { changed(document.visibilityState === 'visible'); });
        window.addEventListener('pagehide', function() { changed(false); });
        window.addEventListener('pageshow', function() { changed(document.visibilityState === 'visible'); });
    })()""",
)

/** Tells index.html the game has drawn, so it can take its loading screen down. */
private fun pageReady(): Unit = js("(function(){ if (window.porchlightReady) window.porchlightReady(); })()")
