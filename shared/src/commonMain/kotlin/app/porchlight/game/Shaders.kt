package app.porchlight.game

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode

/**
 * What this phone or browser can draw. The host sets [shaders] and [blur] from [Platform] at launch
 * (Android: AGSL shaders need Android 13, blur Android 12). Without them every effect has a
 * plainer fallback; a debug launch can switch them off to check those fallbacks on a new phone.
 */
internal object Gpu {
    /** Set from debug launch extras: a fixed tilt instead of the sensor, to check the parallax. */
    var fakeTilt: Pair<Float, Float>? = null

    var shaders = false
    var blur = false

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
internal const val FOIL = """
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
internal const val GLOW = """
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

/**
 * Heat shimmer: a gentle sideways ripple, stronger towards the top, as if over a flame. Long, slow
 * waves keep the jar's outline whole, and the ripple fades to nothing over [edge] pixels at the
 * layer's sides, so the layer's bounds never show as a hard cut.
 */
internal const val SHIMMER = """
uniform shader content;
uniform float2 size;
uniform float time;
uniform float amount;
uniform float edge;

half4 main(float2 p) {
    float wave = sin(p.y * 0.035 + time * 9.0) * 0.7 + sin(p.y * 0.08 - time * 6.0) * 0.3;
    float rise = 0.35 + 0.65 * smoothstep(size.y, 0.0, p.y);
    float fade = smoothstep(0.0, edge, p.x) * smoothstep(size.x, size.x - edge, p.x)
        * smoothstep(0.0, edge, p.y) * smoothstep(size.y, size.y - edge, p.y);
    return content.eval(p + float2(wave * amount * rise * fade, 0.0));
}
"""

/** A chromatic-aberration pulse: red and blue pull apart, outward from [center]. */
internal const val ABERRATION = """
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

/** Card foil, drawn as a brush. Null where shaders aren't available. */
internal expect class FoilShader {
    val brush: ShaderBrush
    fun update(width: Float, height: Float, tiltX: Float, tiltY: Float, time: Float, gold: Boolean, strength: Float)

    companion object {
        /** A foil for one card, or null where shaders aren't available. */
        fun create(): FoilShader?
    }
}

/** The bulb's light on the wall, drawn as a brush. Null where shaders aren't available. */
internal expect class GlowShader {
    val brush: ShaderBrush
    fun update(x: Float, y: Float, radius: Float, time: Float, level: Float, warmth: Float, hue: Color?)

    companion object {
        fun create(): GlowShader?
    }
}

/** Effects applied to a whole layer: they read what the layer drew and bend it. */
internal expect class LayerEffects {
    /** Heat shimmer for a layer [width] x [height] pixels, or null where shaders aren't available. */
    fun shimmer(width: Float, height: Float, time: Float, amount: Float, edge: Float): RenderEffect?

    fun aberration(x: Float, y: Float, strength: Float): RenderEffect?

    companion object {
        fun create(): LayerEffects
    }
}

/** A gaussian blur of [radius] pixels, or null where blur isn't available. */
internal fun blurOf(radius: Float): RenderEffect? =
    if (Gpu.blur && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
