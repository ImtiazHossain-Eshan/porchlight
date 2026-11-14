package app.porchlight.game

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
    LaunchedEffect(clock) {
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (last != 0L) clock.advance(((nanos - last) / 1e9f).coerceAtMost(0.05f))
                last = nanos
            }
        }
    }
    return clock
}

private const val MOTES = 26

/**
 * The night wall: plank siding, the pool of warm light the bulb throws, dust drifting in it, and a
 * vignette. [light] runs from 0 (bulb off) to about 1.6 (full); [tighten] closes the vignette in.
 */
@Composable
internal fun NightScene(
    clock: Clock,
    bulb: () -> Offset,
    light: () -> Float,
    reach: () -> Float,
    tighten: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val motes = remember {
        val random = Random(11)
        FloatArray(MOTES * 4) { random.nextFloat() }
    }
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val wall = Brush.verticalGradient(listOf(Dusk.wallTop, Dusk.wallBottom))
                val pool = Brush.radialGradient(
                    0f to Dusk.glow.copy(alpha = 0.55f),
                    0.45f to Dusk.glowDeep.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                    center = Offset.Zero,
                    radius = 100f,
                )
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
                val moteSize = 1.4.dp.toPx()

                onDrawBehind {
                    drawRect(wall)
                    var y = plankGap
                    while (y < size.height) {
                        drawLine(Dusk.plank, Offset(0f, y), Offset(size.width, y), plankWidth)
                        y += plankGap
                    }

                    val at = bulb()
                    val level = light().coerceIn(0f, 2f)
                    val radius = reach()
                    if (level > 0.01f && radius > 1f) {
                        translate(at.x, at.y) {
                            scale(radius / 100f, radius / 100f, pivot = Offset.Zero) {
                                drawCircle(pool, 100f, Offset.Zero, alpha = level.coerceAtMost(1f))
                            }
                        }
                        val t = clock.seconds
                        for (i in 0 until MOTES) {
                            val angle = motes[i * 4] * 6.283f + t * (0.05f + motes[i * 4 + 1] * 0.12f)
                            val far = 0.18f + motes[i * 4 + 2] * 0.72f
                            val bob = sin(t * 0.6f + motes[i * 4 + 3] * 6.283f) * 0.04f
                            drawCircle(
                                Dusk.glow,
                                moteSize * (0.6f + motes[i * 4 + 3]),
                                Offset(at.x + cos(angle) * far * radius, at.y + (sin(angle) * far * 0.8f + bob) * radius),
                                alpha = (level * (1f - far) * 0.55f).coerceIn(0f, 1f),
                            )
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
