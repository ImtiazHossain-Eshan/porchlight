package app.porchlight.game

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * What this phone can draw. AGSL shaders need Android 13 (API 33); blur needs Android 12 (API 31).
 * Below those, every effect has a plainer fallback. [pretend] lets a debug build act like an older
 * phone, so the fallbacks can be checked on a new one.
 */
internal object Gpu {
    /** Set from a debug launch extra: 0 means the real API level. */
    var pretend = 0

    /** Set from debug launch extras: a fixed tilt instead of the sensor, to check the parallax. */
    var fakeTilt: Pair<Float, Float>? = null

    private val api get() = if (pretend > 0) minOf(pretend, Build.VERSION.SDK_INT) else Build.VERSION.SDK_INT

    @get:ChecksSdkIntAtLeast(api = 33)
    val shaders get() = api >= 33
    val blur get() = api >= 31

    /**
     * Steps down when frames run long: 1 halves the bloom's resolution, 2 also thins the particles.
     * Set by [FrameWatch]; it only ever goes down within a session.
     */
    var quality = 0
}

/**
 * Card foil. Diagonal bands of colour slide as the card tilts, and a bright sheen line sits where
 * the light falls. [gold] swaps the rainbow for a gold ramp (mastered moths). Output is
 * premultiplied and already faded to [strength], so it can be drawn straight over the card.
 */
private const val FOIL = """
uniform float2 size;
uniform float2 tilt;
uniform float time;
uniform float gold;
uniform float strength;

half4 main(float2 p) {
    float2 uv = p / size;
    float band = uv.x * 0.8 + uv.y * 1.1 + tilt.x * 0.55 - tilt.y * 0.35 + time * 0.025;
    float3 rainbow = 0.62 + 0.38 * cos(6.2831 * (band * 1.4 + float3(0.0, 0.33, 0.67)));
    float wave = 0.5 + 0.5 * cos(6.2831 * band * 1.6);
    float3 metal = mix(float3(0.62, 0.40, 0.10), float3(1.0, 0.88, 0.52), wave);
    float3 colour = mix(rainbow, metal, gold);

    // The sheen: a soft line across the card, on the side the light comes from.
    float2 light = float2(0.5 - tilt.x * 0.55, 0.42 - tilt.y * 0.55);
    float across = abs(dot(uv - light, normalize(float2(1.0, -0.8))));
    float sheen = smoothstep(0.1, 0.0, across);

    float a = strength * (0.5 + 0.5 * sheen);
    float3 c = colour * a + sheen * strength * 0.45;
    return half4(half3(c), half(a));
}
"""

/**
 * The bulb's light on the wall: a soft falloff, faint god rays turning slowly, and film grain.
 * [warmth] is the time left (1 warm white, 0 deep orange); [level] is how brightly it burns.
 */
private const val GLOW = """
uniform float2 center;
uniform float radius;
uniform float time;
uniform float level;
uniform float warmth;
uniform float3 hue;
uniform float hueMix;

half4 main(float2 p) {
    float2 d = p - center;
    float r = length(d) / radius;
    float fall = exp(-r * r * 2.4) * 0.85 + exp(-r * 1.6) * 0.22;

    float angle = atan(d.y, d.x);
    float rays = (0.5 + 0.5 * sin(angle * 9.0 + time * 0.12)) * (0.5 + 0.5 * sin(angle * 5.0 - time * 0.08 + 1.3));
    rays = rays * rays * rays * smoothstep(1.5, 0.12, r) * 0.24;

    float3 hot = mix(mix(float3(1.0, 0.50, 0.20), float3(1.0, 0.84, 0.52), warmth), hue, hueMix);
    float grain = fract(sin(dot(floor(p) + fract(time) * 17.0, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
    float a = clamp((fall + rays) * level * 0.62 + grain * 0.03 * level, 0.0, 1.0);
    return half4(half3(hot * a), half(a));
}
"""

/** Heat shimmer: ripples the bulb sideways, more strongly towards the top, as if over a flame. */
private const val SHIMMER = """
uniform shader content;
uniform float2 size;
uniform float time;
uniform float amount;

half4 main(float2 p) {
    float wave = sin(p.y * 0.07 + time * 13.0) * 0.6 + sin(p.y * 0.19 - time * 8.0) * 0.4;
    float rise = 0.35 + 0.65 * smoothstep(size.y, 0.0, p.y);
    return content.eval(p + float2(wave * amount * rise, 0.0));
}
"""

/** A chromatic-aberration pulse: red and blue pull apart, outward from [center]. */
private const val ABERRATION = """
uniform shader content;
uniform float2 center;
uniform float strength;

half4 main(float2 p) {
    float2 dir = p - center;
    float len = length(dir) + 0.001;
    float2 n = dir / len;
    float k = strength * smoothstep(0.0, 240.0, len);
    half4 c = content.eval(p);
    half r = content.eval(p + n * k).r;
    half b = content.eval(p - n * k).b;
    return half4(r, c.g, b, c.a);
}
"""

/** Builds a shader, or returns null if this phone can't (old Android, or a driver that refuses it). */
@RequiresApi(33)
private fun compile(source: String): RuntimeShader? = runCatching { RuntimeShader(source) }.getOrNull()

/** [brush] is built with the shader, inside the API-33 path, so callers never touch RuntimeShader. */
internal class FoilShader @RequiresApi(33) private constructor(val shader: RuntimeShader, val brush: ShaderBrush) {
    @RequiresApi(33)
    fun update(width: Float, height: Float, tiltX: Float, tiltY: Float, time: Float, gold: Boolean, strength: Float) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("tilt", tiltX, tiltY)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("gold", if (gold) 1f else 0f)
        shader.setFloatUniform("strength", strength)
    }

    companion object {
        /** A foil for one card, or null where shaders aren't available. */
        fun create(): FoilShader? = if (Gpu.shaders) newFoil() else null

        @RequiresApi(33)
        private fun newFoil() = compile(FOIL)?.let { FoilShader(it, ShaderBrush(it)) }
    }
}

internal class GlowShader @RequiresApi(33) private constructor(val shader: RuntimeShader, val brush: ShaderBrush) {
    @RequiresApi(33)
    fun update(x: Float, y: Float, radius: Float, time: Float, level: Float, warmth: Float, hue: androidx.compose.ui.graphics.Color?) {
        shader.setFloatUniform("hue", hue?.red ?: 0f, hue?.green ?: 0f, hue?.blue ?: 0f)
        shader.setFloatUniform("hueMix", if (hue != null) 0.85f else 0f)
        shader.setFloatUniform("center", x, y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("level", level)
        shader.setFloatUniform("warmth", warmth)
    }

    companion object {
        fun create(): GlowShader? = if (Gpu.shaders) newGlow() else null

        @RequiresApi(33)
        private fun newGlow() = compile(GLOW)?.let { GlowShader(it, ShaderBrush(it)) }
    }
}

/** Effects applied to a whole layer: they read what the layer drew and bend it. */
internal class LayerEffects private constructor(private val shimmer: Any?, private val aberration: Any?) {
    /** Heat shimmer for a layer [width] x [height] pixels, or null where shaders aren't available. */
    fun shimmer(width: Float, height: Float, time: Float, amount: Float): androidx.compose.ui.graphics.RenderEffect? =
        if (Build.VERSION.SDK_INT >= 33 && shimmer != null) shimmer33(shimmer as RuntimeShader, width, height, time, amount) else null

    fun aberration(x: Float, y: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect? =
        if (Build.VERSION.SDK_INT >= 33 && aberration != null) aberration33(aberration as RuntimeShader, x, y, strength) else null

    @RequiresApi(33)
    private fun shimmer33(s: RuntimeShader, width: Float, height: Float, time: Float, amount: Float): androidx.compose.ui.graphics.RenderEffect {
        s.setFloatUniform("size", width, height)
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

    companion object {
        fun create(): LayerEffects =
            if (Gpu.shaders) LayerEffects(compile(SHIMMER), compile(ABERRATION))
            else LayerEffects(null, null)
    }
}

/** A gaussian blur of [radius] pixels, or null below Android 12. */
internal fun blurOf(radius: Float): androidx.compose.ui.graphics.RenderEffect? =
    if (Gpu.blur && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
