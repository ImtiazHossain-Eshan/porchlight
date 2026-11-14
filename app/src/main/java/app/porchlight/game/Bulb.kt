package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.min
import kotlin.math.sin

private val WARM_WHITE = Color(0xFFFFF3C4)
private val AMBER = Color(0xFFFFB347)
private val DEEP_ORANGE = Color(0xFFE8642B)
private val COLD_GLASS = Color(0xFF22384A)
private val COLD_WIRE = Color(0xFF53606B)
private val BRASS = Color(0xFF8A6A3A)
private val BRASS_DARK = Color(0xFF4E3A1E)
private val GLASS_EDGE = Color(0xFF3A2A14)

private const val GLASS = "M44 26 C44 44 14 56 14 88 A42 42 0 0 0 98 88 C98 56 68 44 68 26 Z"
private val GLASS_CENTRE = Offset(56f, 88f)

/** How far down the bulb's box the middle of the glass sits. */
internal const val BULB_GLASS_Y = 88f / 160f

private val glassLine = Stroke(2.5f, join = StrokeJoin.Round)
private val wire = Stroke(1.6f)
private val filamentLine = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val threadLine = Stroke(2.2f)
private val arcLine = Stroke(3f, cap = StrokeCap.Round)

/** Warm white with time to spare, amber as it runs down, deep orange at the end. */
internal fun glassColour(t: Float): Color = when {
    t >= 0.4f -> lerp(AMBER, WARM_WHITE, (t - 0.4f) / 0.6f)
    t >= 0.15f -> lerp(DEEP_ORANGE, AMBER, (t - 0.15f) / 0.25f)
    else -> DEEP_ORANGE
}

/**
 * The porch bulb, and the only clock in the game. [timeLeft] is the fraction of the round remaining:
 * it sets the size of the glow and the colour of the glass. [power] is how brightly it burns right
 * now (0 off, 1 on, more during a flare, less in a flicker).
 */
@Composable
internal fun Bulb(
    clock: Clock,
    timeLeft: () -> Float,
    power: () -> Float,
    modifier: Modifier = Modifier,
    showArc: Boolean = false,
) {
    val halo = remember {
        Brush.radialGradient(
            0f to Color(0xFFFFF0C0),
            0.25f to Color(0xFFFFD27A).copy(alpha = 0.7f),
            0.6f to Color(0xFFFF9F43).copy(alpha = 0.22f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = 100f,
        )
    }
    val core = remember {
        Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center = Offset.Zero, radius = 100f)
    }
    Canvas(modifier) {
        val s = min(size.width / 112f, size.height / 160f)
        translate((size.width - 112f * s) / 2f, (size.height - 160f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                val t = timeLeft().coerceIn(0f, 1f)
                val raw = power().coerceIn(0f, 1.8f)
                val on = raw.coerceAtMost(1f)

                if (raw > 0.02f) {
                    // Glow radius = lerp(0.6, 1.6, t) x bulb width.
                    val radius = (0.6f + t) * 112f * (0.55f + 0.45f * raw)
                    translate(GLASS_CENTRE.x, GLASS_CENTRE.y) {
                        scale(radius / 100f, radius / 100f, pivot = Offset.Zero) {
                            drawCircle(halo, 100f, Offset.Zero, alpha = (0.8f * raw).coerceAtMost(1f))
                        }
                    }
                }

                if (showArc) {
                    val box = Offset(GLASS_CENTRE.x - 54f, GLASS_CENTRE.y - 54f)
                    drawArc(Dusk.panelEdge, 215f, -250f, false, box, Size(108f, 108f), style = arcLine)
                    if (t > 0.005f) {
                        drawArc(if (t < 0.17f) Dusk.ember else Dusk.amber, 215f, -250f * t, false, box, Size(108f, 108f), style = arcLine)
                    }
                }

                val glass = svg(GLASS)
                drawPath(glass, lerp(COLD_GLASS, glassColour(t), on))
                if (on > 0.02f) {
                    translate(GLASS_CENTRE.x, GLASS_CENTRE.y - 8f) {
                        scale(0.34f, 0.34f, pivot = Offset.Zero) {
                            drawCircle(core, 100f, Offset.Zero, alpha = (on * (0.35f + 0.65f * t)).coerceAtMost(1f))
                        }
                    }
                }
                drawOval(Color.White.copy(alpha = 0.10f + 0.18f * on), Offset(25f, 66f), Size(11f, 24f))
                drawPath(glass, GLASS_EDGE, style = glassLine)

                drawPath(svg("M50 27 V66 M62 27 V66"), lerp(COLD_WIRE, GLASS_EDGE, on), style = wire)
                // The filament burns brighter with time left; near the end it pulses.
                val pulse = if (t < 0.25f) 0.6f + 0.4f * sin(clock.seconds * 16f) else 1f
                val heat = (on * (0.45f + 0.55f * t) * pulse).coerceIn(0f, 1f)
                drawPath(
                    svg("M40 80 L46 66 L53 80 L59 66 L66 80 L72 66"),
                    lerp(COLD_WIRE, if (t < 0.25f) Dusk.ember else Color.White, heat),
                    style = filamentLine,
                )

                drawRect(BRASS, Offset(40f, 0f), Size(32f, 27f))
                drawPath(svg("M40 9 H72 M40 18 H72"), BRASS_DARK, style = threadLine)
                drawRect(BRASS_DARK, Offset(40f, 0f), Size(32f, 27f), style = glassLine)
            }
        }
    }
}
