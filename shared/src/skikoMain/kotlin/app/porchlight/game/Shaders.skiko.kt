package app.porchlight.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush

// Phase 2: no shaders yet on iOS and the web, so every effect takes its plainer fallback.

internal actual class FoilShader private constructor() {
    actual val brush: ShaderBrush get() = error("no shaders")
    actual fun update(width: Float, height: Float, tiltX: Float, tiltY: Float, time: Float, gold: Boolean, strength: Float) = Unit

    actual companion object {
        actual fun create(): FoilShader? = null
    }
}

internal actual class GlowShader private constructor() {
    actual val brush: ShaderBrush get() = error("no shaders")
    actual fun update(x: Float, y: Float, radius: Float, time: Float, level: Float, warmth: Float, hue: Color?) = Unit

    actual companion object {
        actual fun create(): GlowShader? = null
    }
}

internal actual class LayerEffects private constructor() {
    actual fun shimmer(width: Float, height: Float, time: Float, amount: Float, edge: Float): RenderEffect? = null
    actual fun aberration(x: Float, y: Float, strength: Float): RenderEffect? = null

    actual companion object {
        actual fun create(): LayerEffects = LayerEffects()
    }
}
