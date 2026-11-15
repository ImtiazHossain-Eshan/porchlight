package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.min
import kotlin.math.sin

private val WARM_WHITE = Color(0xFFFFF3C4)
private val AMBER = Color(0xFFFFB347)
private val DEEP_ORANGE = Color(0xFFE8642B)
private val COLD_GLASS = Color(0xFF26313B)
private val COLD_WIRE = Color(0xFF59626B)
private val GLASS_EDGE = Color(0xFF2A1C0E)

private const val GLASS = "M44 26 C44 44 14 56 14 88 A42 42 0 0 0 98 88 C98 56 68 44 68 26 Z"
private const val JAR = "M28 44 Q28 34 38 34 H74 Q84 34 84 44 V134 Q84 150 68 150 H44 Q28 150 28 134 Z"
private const val FILAMENT = "M40 80 L46 66 L53 80 L59 66 L66 80 L72 66"
private val GLASS_CENTRE = Offset(56f, 88f)

/** How far down the lamp's box the middle of the glass sits. */
internal const val BULB_GLASS_Y = 88f / 160f

private val glassLine = Stroke(2.5f, join = StrokeJoin.Round)
private val wire = Stroke(1.6f)
private val filamentLine = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val threadLine = Stroke(2.2f)
private val needle = Stroke(2.4f, cap = StrokeCap.Round)

/** Which lamp is hanging: the lobby's mason jar, or the round's bulb in its brass works. */
internal enum class Lamp { Jar, Works }

/** Warm white with time to spare, amber at 40%, deep orange under 15%, red in the last five seconds. */
internal fun glassColour(t: Float, last: Boolean = false): Color = when {
    last -> Craft.danger
    t >= 0.4f -> lerp(AMBER, WARM_WHITE, (t - 0.4f) / 0.6f)
    t >= 0.15f -> lerp(DEEP_ORANGE, AMBER, (t - 0.15f) / 0.25f)
    else -> DEEP_ORANGE
}

/**
 * The porch light, and the only clock in the game.
 *
 * As a [Lamp.Jar] it is a mason jar with a filament burning inside, hung by a wire: the lobby.
 * As [Lamp.Works] it is an Edison bulb in a brass socket, two gears turning behind it, and a
 * pressure gauge whose needle falls with the time left: the round.
 *
 * [timeLeft] is the fraction of the round remaining. [power] is how brightly it burns (0 off, 1 on,
 * more in a flare or the burn-out pop, less in a flicker). The light's colour follows [timeLeft]
 * (see [glassColour]); [last] turns it red for the final seconds, and [hue] can still override it.
 * [arc] draws the round's time arc around the glass. [smoke] makes it smoulder.
 */
@Composable
internal fun Bulb(
    clock: Clock,
    timeLeft: () -> Float,
    power: () -> Float,
    modifier: Modifier = Modifier,
    lamp: Lamp = Lamp.Works,
    hue: () -> Color? = { null },
    last: () -> Boolean = { false },
    arc: Boolean = false,
    smoke: () -> Boolean = { false },
    still: Boolean = false,
    /** Draw the lamp's own halo. On by default where the scene's glow shader can't; the share card always wants it. */
    ownHalo: Boolean = !Gpu.shaders,
) {
    val halo = remember {
        Brush.radialGradient(
            0f to Color.White,
            0.25f to Color.White.copy(alpha = 0.7f),
            0.6f to Color.White.copy(alpha = 0.22f),
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
                val time = if (still) 0f else clock.seconds
                val light = hue() ?: glassColour(t, last())

                // The time ring sits behind the brass works, so the gears and gauge stay on top of it.
                if (arc) timeArc(t)
                if (lamp == Lamp.Works) works(time, t, on)

                // Without the scene's glow shader, the lamp carries its own halo.
                if (raw > 0.02f && ownHalo) {
                    val radius = (0.6f + t) * 112f * (0.55f + 0.45f * raw)
                    translate(GLASS_CENTRE.x, GLASS_CENTRE.y) {
                        scale(radius / 100f, radius / 100f, pivot = Offset.Zero) {
                            drawCircle(halo, 100f, Offset.Zero, alpha = (0.5f * raw).coerceAtMost(1f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(light, androidx.compose.ui.graphics.BlendMode.Modulate))
                        }
                    }
                }

                if (lamp == Lamp.Jar) jar(light, on, core, time) else bulb(light, on, raw, t, last(), core, time)

                if (smoke() && !still) {
                    // The bulb smoulders: grey puffs rise from the top of the glass and thin out.
                    for (i in 0 until 6) {
                        val age = ((time * 0.55f + i / 6f) % 1f)
                        val x = 56f + sin(time * 1.3f + i * 2.1f) * (6f + 14f * age)
                        drawCircle(Color(0xFF9A948C), 5f + 13f * age, Offset(x, 40f - 66f * age), alpha = 0.34f * (1f - age))
                    }
                }
            }
        }
    }
}

/** The Edison bulb: brass cap, coloured glass, a hot core and a filament. */
private fun DrawScope.bulb(light: Color, on: Float, raw: Float, t: Float, last: Boolean, core: Brush, time: Float) {
    val glass = svg(GLASS)
    // Dark, the glass is still there: only the light goes.
    drawPath(glass, lerp(COLD_GLASS, light, on * (0.55f + 0.45f * t).coerceAtMost(1f)))
    if (on > 0.02f) {
        // A flare (Sorbet's top-up, a big streak) or the burn-out pop swells the hot core.
        val swell = 1f + 0.5f * (raw - on).coerceAtLeast(0f)
        translate(GLASS_CENTRE.x, GLASS_CENTRE.y - 8f) {
            scale(0.34f * swell, 0.34f * swell, pivot = Offset.Zero) {
                drawCircle(core, 100f, Offset.Zero, alpha = (raw * (0.35f + 0.65f * t)).coerceAtMost(1f))
            }
        }
    }
    drawOval(Color.White.copy(alpha = 0.10f + 0.18f * on), Offset(25f, 66f), Size(11f, 24f))
    drawPath(glass, GLASS_EDGE, style = glassLine)

    drawPath(svg("M50 27 V66 M62 27 V66"), lerp(COLD_WIRE, GLASS_EDGE, on), style = wire)
    // The filament burns brighter with time left; near the end it pulses.
    val pulse = if (t < 0.25f) 0.6f + 0.4f * sin(time * 16f) else 1f
    val heat = (on * (0.45f + 0.55f * t) * pulse).coerceIn(0f, 1f)
    drawPath(svg(FILAMENT), lerp(COLD_WIRE, if (last || t < 0.25f) Dusk.ember else Color.White, heat), style = filamentLine)

    drawRect(Craft.brass, Offset(40f, 0f), Size(32f, 27f))
    drawRect(Craft.brassLight, Offset(44f, 0f), Size(5f, 27f), alpha = 0.6f)
    drawPath(svg("M40 9 H72 M40 18 H72"), Craft.brassDark, style = threadLine)
    drawRect(Craft.brassDark, Offset(40f, 0f), Size(32f, 27f), style = glassLine)
}

/** The mason jar: a wire handle, a zinc lid with its thread, thick glass, and a filament inside. */
private fun DrawScope.jar(light: Color, on: Float, core: Brush, time: Float) {
    // The wire it hangs from.
    drawPath(svg("M40 14 C34 -10 78 -10 72 14"), Craft.brassDark, style = Stroke(3f, cap = StrokeCap.Round))
    drawPath(svg("M40 14 C34 -10 78 -10 72 14"), Craft.brassLight, alpha = 0.6f, style = Stroke(1f, cap = StrokeCap.Round))

    val glass = svg(JAR)
    // The glow fills the jar; the glass itself is a pale blue-white skin over it.
    drawPath(glass, lerp(COLD_GLASS, lerp(light, AMBER, 0.4f), on * 0.72f))
    if (on > 0.02f) {
        val breathe = 0.92f + 0.08f * sin(time * 1.7f)
        translate(56f, 100f) {
            scale(0.36f * breathe, 0.46f * breathe, pivot = Offset.Zero) {
                drawCircle(core, 100f, Offset.Zero, alpha = (on * 0.85f).coerceAtMost(1f))
            }
        }
    }
    // The filament and its two posts.
    drawPath(svg("M50 34 V84 M62 34 V84"), lerp(COLD_WIRE, GLASS_EDGE, on), style = wire)
    translate(0f, 18f) {
        drawPath(svg(FILAMENT), lerp(COLD_WIRE, Color.White, on), style = filamentLine)
    }
    // Reflections: a long one down the left, a short one on the right shoulder.
    drawPath(svg("M36 50 V124"), Color.White, alpha = 0.22f + 0.2f * on, style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(svg("M76 48 V64"), Color.White, alpha = 0.14f + 0.14f * on, style = Stroke(3f, cap = StrokeCap.Round))
    drawPath(glass, Color(0xFFCFE6F2), alpha = 0.55f, style = Stroke(3f, join = StrokeJoin.Round))
    drawPath(glass, GLASS_EDGE, alpha = 0.7f, style = Stroke(1f, join = StrokeJoin.Round))

    // The lid and its screw thread.
    drawRoundRect(Craft.brass, Offset(32f, 12f), Size(48f, 24f), androidx.compose.ui.geometry.CornerRadius(4f))
    drawRect(Craft.brassLight, Offset(38f, 13f), Size(7f, 22f), alpha = 0.55f)
    drawPath(svg("M32 20 H80 M32 28 H80"), Craft.brassDark, style = threadLine)
    drawRoundRect(Craft.brassDark, Offset(32f, 12f), Size(48f, 24f), androidx.compose.ui.geometry.CornerRadius(4f), style = glassLine)
}

/**
 * The brass works behind the round's bulb: two gears that turn while it burns, and a pressure gauge
 * whose needle sweeps from full to empty with the time left. The last sixth of the dial is red.
 */
private fun DrawScope.works(time: Float, t: Float, on: Float) {
    val turn = time * 14f * on
    gear(Offset(-12f, 40f), 28f, 12, turn, copper = true)
    gear(Offset(124f, 26f), 21f, 10, -turn * 1.36f - 8f)
    gear(Offset(122f, 132f), 15f, 8, turn * 1.9f, copper = true, alpha = 0.9f)

    // The gauge, lower left: a brass rim, a cream face, tick marks, a red zone and the needle.
    val c = Offset(-10f, 118f)
    drawCircle(Craft.brassDark, 25f, c)
    drawCircle(Craft.brass, 23f, c)
    drawCircle(Craft.parchment, 19f, c)
    val dial = Offset(c.x - 15f, c.y - 15f)
    drawArc(Craft.danger, 150f, 40f, false, dial, Size(30f, 30f), style = Stroke(4f))
    drawArc(Craft.parchmentInkSoft, 190f, 200f, false, dial, Size(30f, 30f), style = Stroke(1.2f))
    for (i in 0..6) {
        rotate(150f + i * 40f, pivot = c) {
            drawLine(Craft.parchmentInk, Offset(c.x + 12f, c.y), Offset(c.x + 16f, c.y), 1.2f)
        }
    }
    // 150° is empty (lower left), 390° is full (lower right), sweeping over the top.
    rotate(150f + 240f * t, pivot = c) {
        drawLine(if (t < 1f / 6f) Craft.danger else Craft.parchmentInk, c, Offset(c.x + 15f, c.y), needle.width, StrokeCap.Round)
    }
    drawCircle(Craft.brassDark, 3f, c)
}

/**
 * The round's clock: a bold band that follows the glass's own outline, a little outside it, open at
 * the neck under the cap. It drains clockwise from the right of the neck, so what's left is the gold
 * run from the left of the neck down and round; what's spent is a faint track.
 */
private const val TIME_BAND = "M38 36 C38 50 8 60 8 88 A48 48 0 0 0 104 88 C104 60 74 50 74 36"
private val bandMeasure = PathMeasure()
private val bandLeft = Path()

private fun DrawScope.timeArc(t: Float) {
    val band = svg(TIME_BAND)
    drawPath(band, Color.White, alpha = 0.15f, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (t <= 0f) return
    bandMeasure.setPath(band, false)
    bandLeft.reset()
    bandMeasure.getSegment(0f, bandMeasure.length * t, bandLeft, true)
    // A soft glow under the band, then the band itself, then a bright line along its middle.
    drawPath(bandLeft, Craft.gold, alpha = 0.3f, style = Stroke(15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(bandLeft, Craft.gold, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(bandLeft, Craft.brassLight, alpha = 0.8f, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
