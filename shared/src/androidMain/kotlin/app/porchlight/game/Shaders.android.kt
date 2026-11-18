package app.porchlight.game

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect

// AGSL, Android 13 and up. The shader sources are shared with iOS and the web (see Shaders.kt).

/** Builds a shader, or returns null if this phone can't (old Android, or a driver that refuses it). */
@RequiresApi(33)
private fun compile(source: String): RuntimeShader? = runCatching { RuntimeShader(source) }.getOrNull()

/** [brush] is built with the shader, inside the API-33 path, so callers never touch RuntimeShader. */
internal actual class FoilShader @RequiresApi(33) private constructor(val shader: RuntimeShader, actual val brush: ShaderBrush) {
    actual fun update(width: Float, height: Float, tiltX: Float, tiltY: Float, time: Float, gold: Boolean, strength: Float) {
        if (Build.VERSION.SDK_INT < 33) return
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("tilt", tiltX, tiltY)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("gold", if (gold) 1f else 0f)
        shader.setFloatUniform("strength", strength)
    }

    actual companion object {
        /** A foil for one card, or null where shaders aren't available. */
        actual fun create(): FoilShader? = if (Gpu.shaders && Build.VERSION.SDK_INT >= 33) newFoil() else null

        @RequiresApi(33)
        private fun newFoil() = compile(FOIL)?.let { FoilShader(it, ShaderBrush(it)) }
    }
}

internal actual class GlowShader @RequiresApi(33) private constructor(val shader: RuntimeShader, actual val brush: ShaderBrush) {
    actual fun update(x: Float, y: Float, radius: Float, time: Float, level: Float, warmth: Float, hue: androidx.compose.ui.graphics.Color?) {
        if (Build.VERSION.SDK_INT < 33) return
        shader.setFloatUniform("hue", hue?.red ?: 0f, hue?.green ?: 0f, hue?.blue ?: 0f)
        shader.setFloatUniform("hueMix", if (hue != null) 0.85f else 0f)
        shader.setFloatUniform("center", x, y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("level", level)
        shader.setFloatUniform("warmth", warmth)
    }

    actual companion object {
        actual fun create(): GlowShader? = if (Gpu.shaders && Build.VERSION.SDK_INT >= 33) newGlow() else null

        @RequiresApi(33)
        private fun newGlow() = compile(GLOW)?.let { GlowShader(it, ShaderBrush(it)) }
    }
}

/** Effects applied to a whole layer: they read what the layer drew and bend it. */
internal actual class LayerEffects private constructor(private val shimmer: Any?, private val aberration: Any?) {
    /** Heat shimmer for a layer [width] x [height] pixels, or null where shaders aren't available. */
    actual fun shimmer(width: Float, height: Float, time: Float, amount: Float, edge: Float): androidx.compose.ui.graphics.RenderEffect? =
        if (Build.VERSION.SDK_INT >= 33 && shimmer != null) shimmer33(shimmer as RuntimeShader, width, height, time, amount, edge) else null

    actual fun aberration(x: Float, y: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect? =
        if (Build.VERSION.SDK_INT >= 33 && aberration != null) aberration33(aberration as RuntimeShader, x, y, strength) else null

    @RequiresApi(33)
    private fun shimmer33(s: RuntimeShader, width: Float, height: Float, time: Float, amount: Float, edge: Float): androidx.compose.ui.graphics.RenderEffect {
        s.setFloatUniform("size", width, height)
        s.setFloatUniform("edge", edge)
        s.setFloatUniform("time", time)
        s.setFloatUniform("amount", amount)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }

    @RequiresApi(33)
    private fun aberration33(s: RuntimeShader, x: Float, y: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect {
        s.setFloatUniform("center", x, y)
        s.setFloatUniform("strength", strength)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }

    actual companion object {
        actual fun create(): LayerEffects =
            if (Gpu.shaders && Build.VERSION.SDK_INT >= 33) LayerEffects(compile(SHIMMER), compile(ABERRATION))
            else LayerEffects(null, null)
    }
}

