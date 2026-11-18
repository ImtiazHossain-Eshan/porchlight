package app.porchlight.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// SkSL through skiko, on iOS and the web. AGSL is Android's dialect of SkSL, so the shader sources
// in Shaders.kt compile here unchanged. A Skia shader is a fixed snapshot of its uniforms, so each
// update makes a new one; the draw code reads [brush] after calling update.

/** Compiles [source], or returns null if this GPU or browser refuses it. */
private fun builder(source: String): RuntimeShaderBuilder? =
    runCatching { RuntimeShaderBuilder(RuntimeEffect.makeForShader(source)) }
        .onFailure { log("Shader didn't compile: ${it.message}") }
        .getOrNull()

internal actual class FoilShader private constructor(private val shader: RuntimeShaderBuilder) {
    private var current = ShaderBrush(shader.makeShader().asComposeShader())
    actual val brush: ShaderBrush get() = current

    actual fun update(width: Float, height: Float, tiltX: Float, tiltY: Float, time: Float, gold: Boolean, strength: Float) {
        shader.uniform("size", width, height)
        shader.uniform("tilt", tiltX, tiltY)
        shader.uniform("time", time)
        shader.uniform("gold", if (gold) 1f else 0f)
        shader.uniform("strength", strength)
        current = ShaderBrush(shader.makeShader().asComposeShader())
    }

    actual companion object {
        actual fun create(): FoilShader? = if (Gpu.shaders) builder(FOIL)?.let { FoilShader(it) } else null
    }
}

internal actual class GlowShader private constructor(private val shader: RuntimeShaderBuilder) {
    private var current = ShaderBrush(shader.makeShader().asComposeShader())
    actual val brush: ShaderBrush get() = current

    actual fun update(x: Float, y: Float, radius: Float, time: Float, level: Float, warmth: Float, hue: Color?) {
        shader.uniform("hue", hue?.red ?: 0f, hue?.green ?: 0f, hue?.blue ?: 0f)
        shader.uniform("hueMix", if (hue != null) 0.85f else 0f)
        shader.uniform("center", x, y)
        shader.uniform("radius", radius)
        shader.uniform("time", time)
        shader.uniform("level", level)
        shader.uniform("warmth", warmth)
        current = ShaderBrush(shader.makeShader().asComposeShader())
    }

    actual companion object {
        actual fun create(): GlowShader? = if (Gpu.shaders) builder(GLOW)?.let { GlowShader(it) } else null
    }
}

internal actual class LayerEffects private constructor(
    private val shimmer: RuntimeShaderBuilder?,
    private val aberration: RuntimeShaderBuilder?,
) {
    actual fun shimmer(width: Float, height: Float, time: Float, amount: Float, edge: Float): RenderEffect? {
        val s = shimmer ?: return null
        s.uniform("size", width, height)
        s.uniform("edge", edge)
        s.uniform("time", time)
        s.uniform("amount", amount)
        return ImageFilter.makeRuntimeShader(s, "content", null).asComposeRenderEffect()
    }

    actual fun aberration(x: Float, y: Float, strength: Float): RenderEffect? {
        val s = aberration ?: return null
        s.uniform("center", x, y)
        s.uniform("strength", strength)
        return ImageFilter.makeRuntimeShader(s, "content", null).asComposeRenderEffect()
    }

    actual companion object {
        actual fun create(): LayerEffects =
            if (Gpu.shaders) LayerEffects(builder(SHIMMER), builder(ABERRATION)) else LayerEffects(null, null)
    }
}
