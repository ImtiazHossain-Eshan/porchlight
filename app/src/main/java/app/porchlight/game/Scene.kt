package app.porchlight.game

import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.geometry.Size
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animation time in seconds. Only draw code reads it, so a new frame redraws without recomposing.
 * [freeze] holds time still for a moment: the hit-stop on an Overdrive pair.
 */
internal class Clock {
    var seconds by mutableFloatStateOf(0f)
        private set
    var delta = 0f
        private set
    private var frozen = 0f

    fun freeze(forSeconds: Float) {
        frozen = forSeconds
    }

    fun advance(dt: Float) {
        if (frozen > 0f) {
            frozen -= dt
            delta = 0f
            return
        }
        delta = dt
        seconds += dt
    }
}

@Composable
internal fun rememberClock(): Clock {
    val clock = remember { Clock() }
    val view = LocalView.current
    LaunchedEffect(clock) {
        val refresh = view.display?.refreshRate?.takeIf { it > 1f } ?: 60f
        val watch = FrameWatch(1f / refresh)
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (last != 0L) {
                    val dt = (nanos - last) / 1e9f
                    watch.frame(dt)
                    clock.advance(dt.coerceAtMost(0.05f))
                }
                last = nanos
            }
        }
    }
    return clock
}

private const val MOTES = 50

/** How far each layer slides with the phone's tilt: the wall is far back, the moths close. */
internal const val DEPTH_WALL = 0.3f
internal const val DEPTH_GLOW = 0.6f
internal const val DEPTH_BOARD = 1f
internal const val DEPTH_MOTHS = 1.4f

/** Pixels a layer of depth 1 slides at full tilt. */
internal fun parallaxPx(density: Float) = 12f * density

/**
 * The night wall: plank siding, the light the bulb throws, dust drifting through it, and a vignette.
 * [light] runs from 0 (bulb off) to about 1.6 (full); [tighten] closes the vignette in.
 * The wall, the light and the dust each sit at their own depth, so tilting the phone parts them.
 */
@Composable
internal fun NightScene(
    clock: Clock,
    tilt: Tilt,
    calm: Boolean,
    bulb: () -> Offset,
    light: () -> Float,
    reach: () -> Float,
    warmth: () -> Float,
    hue: () -> Color? = { null },
    danger: () -> Float = { 0f },
    tighten: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    // Each mote: angle, speed, distance from the bulb, phase, depth.
    val motes = remember {
        val random = Random(11)
        FloatArray(MOTES * 5) { random.nextFloat() }
    }
    val glowShader = remember { GlowShader.create() }
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val wall = Brush.verticalGradient(listOf(Dusk.wallTop, Dusk.wallBottom))
                // Wide planks: a seam between each, grain along them, a nail at either end.
                val plankHeight = 58.dp.toPx()
                val woodRandom = Random(29)
                val grain = Path()
                val grainLight = Path()
                var gy = 0f
                while (gy < size.height + plankHeight) {
                    val lines = 5 + woodRandom.nextInt(4)
                    for (i in 0 until lines) {
                        val into = if (woodRandom.nextFloat() < 0.3f) grainLight else grain
                        val y0 = gy + plankHeight * (0.1f + 0.8f * woodRandom.nextFloat())
                        var x = -40f - woodRandom.nextFloat() * 120f
                        into.moveTo(x, y0)
                        var up = woodRandom.nextBoolean()
                        while (x < size.width + 40f) {
                            val step = size.width * (0.2f + woodRandom.nextFloat() * 0.35f)
                            val bend = plankHeight * (0.03f + woodRandom.nextFloat() * 0.09f)
                            into.quadraticTo(x + step / 2f, y0 + if (up) -bend else bend, x + step, y0)
                            x += step
                            up = !up
                        }
                    }
                    gy += plankHeight
                }
                val grainLine = Stroke(1.dp.toPx())
                val warning = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    1f to Craft.danger.copy(alpha = 0.5f),
                    center = Offset.Zero,
                    radius = 100f,
                )
                val pool = Brush.radialGradient(
                    0f to Dusk.glow.copy(alpha = 0.55f),
                    0.45f to Dusk.glowDeep.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    center = Offset.Zero,
                    radius = 100f,
                )
                // A soft dot, for the near motes that are out of focus.
                val soft = Brush.radialGradient(
                    0f to Dusk.glow,
                    0.4f to Dusk.glow.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    center = Offset.Zero,
                    radius = 100f,
                )
                val glowBrush = glowShader?.let { ShaderBrush(it.shader) }
                val corner = hypot(size.width, size.height) / 2f
                val vignette = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                    center = Offset.Zero,
                    radius = 100f,
                )
                val plankGap = 28.dp.toPx()
                val plankWidth = 1.dp.toPx()
                val moteSize = 1.3.dp.toPx()
                val slide = parallaxPx(density)

                onDrawBehind {
                    val tx = tilt.x * slide
                    val ty = tilt.y * slide
                    // The wall, drawn a little oversized so its edges never show as it slides.
                    translate(tx * DEPTH_WALL, ty * DEPTH_WALL) {
                        drawRect(wall, Offset(-slide, -slide), Size(size.width + slide * 2, size.height + slide * 2))
                        drawPath(grain, Wood.Night.dark, alpha = 0.55f, style = grainLine)
                        drawPath(grainLight, Wood.Night.light, alpha = 0.3f, style = grainLine)
                        var y = plankHeight
                        while (y < size.height + slide) {
                            drawLine(Dusk.plank, Offset(-slide, y), Offset(size.width + slide, y), plankWidth * 2.5f)
                            drawLine(Wood.Night.light, Offset(-slide, y + plankWidth * 2f), Offset(size.width + slide, y + plankWidth * 2f), plankWidth, alpha = 0.35f)
                            drawCircle(Dusk.plank, plankWidth * 2.2f, Offset(14.dp.toPx(), y - plankHeight / 2f))
                            drawCircle(Dusk.plank, plankWidth * 2.2f, Offset(size.width - 14.dp.toPx(), y - plankHeight / 2f))
                            y += plankHeight
                        }
                    }

                    val at = bulb()
                    val level = light().coerceIn(0f, 2f)
                    val radius = reach()
                    if (level > 0.01f && radius > 1f) {
                        val gx = at.x + tx * DEPTH_GLOW
                        val gy = at.y + ty * DEPTH_GLOW
                        if (glowBrush != null && Build.VERSION.SDK_INT >= 33) {
                            glowShader.update(gx, gy, radius * 0.62f, clock.seconds, level, warmth(), hue())
                            drawRect(glowBrush)
                        } else {
                            translate(gx, gy) {
                                scale(radius / 100f, radius / 100f, pivot = Offset.Zero) {
                                    drawCircle(pool, 100f, Offset.Zero, alpha = level.coerceAtMost(1f))
                                }
                            }
                        }

                        // Dust: far motes are small and sharp, near ones large and soft and slide
                        // further with the tilt. Motes inside the cone of light below the bulb glow.
                        val t = if (calm) 0f else clock.seconds
                        val count = if (Gpu.quality >= 2) MOTES / 2 else MOTES
                        for (i in 0 until count) {
                            val k = i * 5
                            val depth = motes[k + 4]
                            val angle = motes[k] * 6.283f + t * (0.03f + motes[k + 1] * 0.08f)
                            val far = 0.12f + motes[k + 2] * 0.85f
                            val bob = sin(t * 0.5f + motes[k + 3] * 6.283f) * 0.05f
                            val shift = 0.4f + depth * 1.2f
                            val x = at.x + cos(angle) * far * radius * 1.1f + tx * shift
                            val y = at.y + (sin(angle) * far * 0.9f + bob) * radius + ty * shift
                            val dx = x - at.x
                            val dy = y - at.y
                            val below = dy / (hypot(dx, dy) + 1f)
                            val cone = ((below - 0.8f) / 0.2f).coerceIn(0f, 1f)
                            val bright = (level * (1f - far) * (0.35f + 0.9f * cone)).coerceIn(0f, 1f)
                            if (bright < 0.02f) continue
                            if (depth > 0.7f) {
                                val r = moteSize * (2.5f + depth * 4f)
                                translate(x, y) {
                                    scale(r / 100f, r / 100f, pivot = Offset.Zero) {
                                        drawCircle(soft, 100f, Offset.Zero, alpha = bright * 0.55f)
                                    }
                                }
                            } else {
                                drawCircle(Dusk.glow, moteSize * (0.5f + depth * 1.2f), Offset(x, y), alpha = bright)
                            }
                        }
                    }

                    // The last seconds: red closes in from the edges, pulsing.
                    val alarm = danger().coerceIn(0f, 1f)
                    if (alarm > 0.01f) {
                        val beat = if (calm) 0.8f else 0.65f + 0.35f * sin(clock.seconds * 7f)
                        translate(size.width / 2f, size.height / 2f) {
                            scale(size.width / 200f * 1.3f, corner / 100f, pivot = Offset.Zero) {
                                drawCircle(warning, 140f, Offset.Zero, alpha = alarm * beat)
                            }
                        }
                    }

                    val close = 1f - 0.28f * tighten().coerceIn(0f, 1f)
                    translate(size.width / 2f, size.height / 2f) {
                        scale(corner * close / 100f, corner * close / 100f, pivot = Offset.Zero) {
                            drawCircle(vignette, 100f / close * 1.5f, Offset.Zero)
                        }
                    }
                }
            },
    )
}
