package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val CAPACITY = 200
private const val RINGS = 4
private val SPARKS = arrayOf(Color(0xFFFFD27A), Color(0xFFFFB938), Color(0xFFFFF4DA), Color(0xFFFF8A4A))

/**
 * One pool of sparks shared by every effect. Everything lives in flat arrays that are allocated
 * once, so a frame of particles allocates nothing.
 */
internal class Particles {
    private val x = FloatArray(CAPACITY)
    private val y = FloatArray(CAPACITY)
    private val vx = FloatArray(CAPACITY)
    private val vy = FloatArray(CAPACITY)
    private val life = FloatArray(CAPACITY)
    private val span = FloatArray(CAPACITY)
    private val radius = FloatArray(CAPACITY)
    private val tint = IntArray(CAPACITY)
    private var next = 0

    private val ringX = FloatArray(RINGS)
    private val ringY = FloatArray(RINGS)
    private val ringAge = FloatArray(RINGS) { -1f }
    private val ringReach = FloatArray(RINGS)
    private var nextRing = 0
    private val random = Random(5)

    /** Throws [count] sparks outward from [at]. [speed] and [size] are in pixels. */
    fun burst(at: Offset, count: Int, speed: Float, size: Float, seconds: Float = 0.4f) {
        repeat(count) {
            val i = next
            next = (next + 1) % CAPACITY
            val angle = random.nextFloat() * 6.283f
            val push = speed * (0.35f + random.nextFloat() * 0.65f)
            x[i] = at.x
            y[i] = at.y
            vx[i] = cos(angle) * push
            vy[i] = sin(angle) * push - speed * 0.25f
            span[i] = seconds * (0.7f + random.nextFloat() * 0.6f)
            life[i] = span[i]
            radius[i] = size * (0.5f + random.nextFloat())
            tint[i] = random.nextInt(SPARKS.size)
        }
    }

    /** A ring that expands from [at] out to [reach] pixels. */
    fun ring(at: Offset, reach: Float) {
        val i = nextRing
        nextRing = (nextRing + 1) % RINGS
        ringX[i] = at.x
        ringY[i] = at.y
        ringAge[i] = 0f
        ringReach[i] = reach
    }

    fun clear() {
        life.fill(0f)
        ringAge.fill(-1f)
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        for (i in 0 until CAPACITY) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            vy[i] += 900f * dt
            vx[i] *= 1f - 1.8f * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
        for (i in 0 until RINGS) {
            if (ringAge[i] < 0f) continue
            ringAge[i] += dt
            if (ringAge[i] > 0.5f) ringAge[i] = -1f
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (i in 0 until CAPACITY) {
            if (life[i] <= 0f) continue
            val left = life[i] / span[i]
            drawCircle(SPARKS[tint[i]], radius[i] * (0.4f + 0.6f * left), Offset(x[i], y[i]), alpha = left.coerceIn(0f, 1f))
        }
        for (i in 0 until RINGS) {
            if (ringAge[i] < 0f) continue
            val u = ringAge[i] / 0.5f
            val eased = 1f - (1f - u) * (1f - u)
            drawCircle(
                SPARKS[1],
                ringReach[i] * eased,
                Offset(ringX[i], ringY[i]),
                alpha = (1f - u).coerceIn(0f, 1f),
                style = Stroke(6.dp.toPx() * (1f - u) + 1f),
            )
        }
    }
}

/** A score that pops up off a matched pair. */
internal class Pop(val n: Int, val text: String, val at: Offset, val big: Boolean)

/** Sparks, shockwaves and the warm pulse at the screen edge, drawn above the board. */
@Composable
internal fun FxLayer(session: Session, modifier: Modifier = Modifier) {
    val edge = remember {
        Brush.radialGradient(
            0f to Color.Transparent,
            0.6f to Color.Transparent,
            1f to Dusk.amber.copy(alpha = 0.55f),
            center = Offset.Zero,
            radius = 100f,
        )
    }
    Canvas(modifier.fillMaxSize()) {
        session.clock.seconds // redraw every frame
        val pulse = session.edge.value
        if (pulse > 0.01f) {
            val corner = hypot(size.width, size.height) / 2f
            translate(size.width / 2f, size.height / 2f) {
                scale(size.width / 200f * 1.25f, corner / 100f, pivot = Offset.Zero) {
                    drawCircle(edge, 140f, Offset.Zero, alpha = pulse.coerceIn(0f, 1f))
                }
            }
        }
        session.particles.step(session.clock.delta)
        session.particles.draw(this)
    }
}

/** "+200" rises off the upper card of a matched pair, which is already an empty slot, and is gone in 700 ms. */
@Composable
internal fun Pops(session: Session) {
    val density = LocalDensity.current
    session.pops.forEach { pop ->
        key(pop.n) {
            val rise = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                rise.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                session.pops.remove(pop)
            }
            val w = 140.dp
            val h = 44.dp
            Box(
                Modifier
                    .offset {
                        with(density) { IntOffset((pop.at.x - w.toPx() / 2).toInt(), (pop.at.y - h.toPx() / 2).toInt()) }
                    }
                    .size(w, h)
                    .graphicsLayer {
                        val t = rise.value
                        translationY = -28.dp.toPx() * t
                        alpha = (1f - t * t * t * t).coerceIn(0f, 1f)
                        val s = 0.7f + 0.5f * (1f - (1f - t) * (1f - t)).coerceAtMost(0.6f)
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    pop.text,
                    style = Display.copy(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 3f), 10f)),
                    fontSize = if (pop.big) 30.sp else 24.sp,
                    color = if (pop.big) Dusk.glow else Dusk.amber,
                    maxLines = 1,
                )
            }
        }
    }
}

/** OVERDRIVE slides in from the side when the streak hits its cap. */
@Composable
internal fun OverdriveBanner(count: Int, calm: Boolean, modifier: Modifier = Modifier) {
    val t = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            t.snapTo(0f)
            t.animateTo(1f, tween(1_000))
        }
    }
    Text(
        "OVERDRIVE",
        style = Display.copy(shadow = Shadow(Dusk.ember, Offset.Zero, 28f)),
        fontSize = 34.sp,
        letterSpacing = 3.sp,
        color = Dusk.glow,
        maxLines = 1,
        modifier = modifier.graphicsLayer {
            val u = t.value
            val arrive = (u / 0.22f).coerceAtMost(1f)
            val eased = 1f - (1f - arrive) * (1f - arrive) * (1f - arrive)
            translationX = if (calm) 0f else (1f - eased) * size.width * 1.6f
            alpha = if (u >= 1f) 0f else if (u > 0.7f) (1f - u) / 0.3f else 1f
        },
    )
}
