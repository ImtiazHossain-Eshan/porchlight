package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Shapes for the card-back inlays, in a 120-unit square. */
private val INLAY_MOTH = "M60 40 C68 24 100 18 106 36 C110 52 86 60 66 60 C80 66 92 80 84 94 C76 104 64 92 60 80 C56 92 44 104 36 94 C28 80 40 66 54 60 C34 60 10 52 14 36 C20 18 52 24 60 40 Z"
private val INLAY_LEAF = "M60 14 C90 38 90 82 60 106 C30 82 30 38 60 14 Z"
private val starPath: Path by lazy {
    Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 46f else 19f
            val a = -PI / 2 + i * PI / 5
            val x = 60f + (cos(a) * r).toFloat()
            val y = 62f + (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
private val moonPath: Path by lazy {
    val disc = Path().apply { addOval(Rect(Offset(60f, 60f), 40f)) }
    val bite = Path().apply { addOval(Rect(Offset(78f, 48f), 36f)) }
    Path.combine(PathOperation.Difference, disc, bite)
}

/**
 * A motif set into the middle of a card back, [width] pixels across, in the back's metal: a dark
 * cut, the metal, and a thin catch of light, so it reads as inlaid rather than printed.
 */
internal fun DrawScope.inlay(inlay: Inlay, trim: Trim, centre: Offset, width: Float, alpha: Float = 1f) {
    if (inlay == Inlay.None) return
    val (light, base, dark) = trim.tones
    val s = width / 120f
    translate(centre.x - width / 2f, centre.y - width / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            val shape: Path? = when (inlay) {
                Inlay.Moth -> svg(INLAY_MOTH)
                Inlay.Leaf -> svg(INLAY_LEAF)
                Inlay.Star -> starPath
                Inlay.Moon -> moonPath
                else -> null
            }
            if (shape != null) {
                translate(2.5f, 3f) { drawPath(shape, Color.Black, alpha = 0.45f * alpha) }
                drawPath(shape, base, alpha = 0.85f * alpha)
                drawPath(shape, dark, alpha = alpha, style = Stroke(3f))
                translate(-1.2f, -1.2f) { drawPath(shape, light, alpha = 0.45f * alpha, style = Stroke(1.6f)) }
                if (inlay == Inlay.Leaf) drawLine(dark, Offset(60f, 24f), Offset(60f, 98f), 3f, StrokeCap.Round, alpha = alpha)
                if (inlay == Inlay.Moth) {
                    drawLine(dark, Offset(60f, 38f), Offset(60f, 84f), 6f, StrokeCap.Round, alpha = alpha)
                    drawLine(dark, Offset(58f, 40f), Offset(50f, 26f), 2.5f, StrokeCap.Round, alpha = alpha)
                    drawLine(dark, Offset(62f, 40f), Offset(70f, 26f), 2.5f, StrokeCap.Round, alpha = alpha)
                }
                return@scale
            }
            when (inlay) {
                Inlay.Snow -> for (k in 0 until 6) {
                    val a = k * PI / 3
                    val tip = Offset(60f + (cos(a) * 44).toFloat(), 60f + (sin(a) * 44).toFloat())
                    val mid = Offset(60f + (cos(a) * 26).toFloat(), 60f + (sin(a) * 26).toFloat())
                    for ((colour, w, dx) in listOf(Triple(Color.Black, 9f, 2.5f), Triple(base, 6f, 0f), Triple(light, 2f, -1f))) {
                        val o = Offset(dx, dx)
                        val av = if (colour == Color.Black) 0.4f * alpha else if (colour == light) 0.5f * alpha else alpha
                        drawLine(colour, Offset(60f, 60f) + o, tip + o, w, StrokeCap.Round, alpha = av)
                        for (side in listOf(-1, 1)) {
                            val b = a + side * PI / 4
                            drawLine(colour, mid + o, mid + o + Offset((cos(b) * 14).toFloat(), (sin(b) * 14).toFloat()), w * 0.8f, StrokeCap.Round, alpha = av)
                        }
                    }
                }
                Inlay.Firefly -> {
                    // A firefly in lacquer: a gilt body and wings, its tail lit.
                    drawCircle(Brush.radialGradient(listOf(Craft.gold.copy(alpha = 0.55f * alpha), Color.Transparent), Offset(60f, 84f), 40f), 40f, Offset(60f, 84f))
                    drawOval(base, Offset(52f, 34f), Size(16f, 44f), alpha = alpha)
                    drawOval(dark, Offset(52f, 34f), Size(16f, 44f), alpha = alpha, style = Stroke(2.5f))
                    drawOval(light, Offset(30f, 40f), Size(26f, 14f), alpha = 0.6f * alpha)
                    drawOval(light, Offset(64f, 40f), Size(26f, 14f), alpha = 0.6f * alpha)
                    drawCircle(Color(0xFFFFF2B0), 9f, Offset(60f, 84f), alpha = alpha)
                }
                else -> Unit
            }
        }
    }
}

/** A face-down card in [style], for the shop: the same wood, metal, rivets and inlay as on the board. */
@androidx.compose.runtime.Composable
internal fun CardBackPreview(style: BackStyle, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .shadow(6.dp, RoundedCornerShape(12.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        val (light, _, dark) = style.trim.tones
        drawRect(Brush.verticalGradient(listOf(style.light, style.base, style.dark)))
        val random = Random(7)
        var gy = size.height * 0.06f
        while (gy < size.height) {
            val bend = size.height * (0.01f + random.nextFloat() * 0.03f)
            val grain = Path().apply {
                moveTo(0f, gy)
                quadraticTo(size.width * 0.5f, gy + if (random.nextBoolean()) bend else -bend, size.width, gy)
            }
            drawPath(grain, style.dark, alpha = 0.7f, style = Stroke(1.dp.toPx()))
            gy += size.height * (0.07f + random.nextFloat() * 0.1f)
        }
        val frame = Stroke(2.dp.toPx())
        val half = frame.width / 2f
        drawRoundRect(style.trim.rule, Offset(half, half), Size(size.width - frame.width, size.height - frame.width), CornerRadius(12.dp.toPx()), style = frame)
        val inset = 4.dp.toPx()
        drawRoundRect(dark, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawRoundRect(light, Offset(inset + 1.dp.toPx(), inset + 1.dp.toPx()), Size(size.width - inset * 2 - 2.dp.toPx(), size.height - inset * 2 - 2.dp.toPx()), CornerRadius(8.dp.toPx()), alpha = 0.35f, style = Stroke(1.dp.toPx()))
        val stud = 1.8.dp.toPx()
        val at = inset + stud * 2.2f
        for (p in listOf(Offset(at, at), Offset(size.width - at, at), Offset(at, size.height - at), Offset(size.width - at, size.height - at))) rivet(p, stud, trim = style.trim)
        if (style.inlay == Inlay.None) {
            // The plain back's moth at rest, as on the board.
            val (_, metal, metalDark) = style.trim.tones
            val pad = 10.dp.toPx()
            translate(pad, pad) {
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                val s = minOf(w, h) / 120f
                translate((w - 120f * s) / 2f, (h - 120f * s) / 2f) { scale(s, s, pivot = Offset.Zero) { restingMoth(metal, metalDark) } }
            }
        } else {
            inlay(style.inlay, style.trim, Offset(size.width / 2f, size.height / 2f), size.width * 0.6f)
        }
        // A soft spot of light, as on the board.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(size.width * 0.35f, size.height * 0.3f), size.width * 0.7f), size.width * 0.7f, Offset(size.width * 0.35f, size.height * 0.3f))
    }
}

/** A corner of a porch in [style]: the wall's planks, the railing and a pool of its light. */
@androidx.compose.runtime.Composable
internal fun PorchPreview(style: PorchStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp))) {
        drawRect(Brush.verticalGradient(listOf(style.wallTop, style.wallBottom)))
        val plank = size.height / 4.5f
        var y = plank
        while (y < size.height) {
            drawLine(style.plank, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            drawLine(Color.White, Offset(0f, y + 2.dp.toPx()), Offset(size.width, y + 2.dp.toPx()), 1.dp.toPx(), alpha = 0.06f)
            y += plank
        }
        val lamp = Offset(size.width / 2f, size.height * 0.24f)
        drawCircle(
            Brush.radialGradient(0f to style.glow.copy(alpha = 0.75f), 0.45f to style.glowDeep.copy(alpha = 0.25f), 1f to Color.Transparent, center = lamp, radius = size.width * 0.62f),
            size.width * 0.62f, lamp,
        )
        drawCircle(style.glow, size.minDimension * 0.07f, lamp)
        drawCircle(Color.White, size.minDimension * 0.035f, lamp, alpha = 0.9f)
        val (light, base, dark) = style.rail
        val rail = size.height * 0.78f
        var x = 4.dp.toPx()
        while (x < size.width) {
            drawRect(dark, Offset(x, rail), Size(5.dp.toPx(), size.height - rail))
            drawRect(base, Offset(x + 1.dp.toPx(), rail), Size(2.dp.toPx(), size.height - rail), alpha = 0.6f)
            x += 16.dp.toPx()
        }
        drawRect(base, Offset(0f, rail - 6.dp.toPx()), Size(size.width, 7.dp.toPx()))
        drawRect(light, Offset(0f, rail - 6.dp.toPx()), Size(size.width, 1.5.dp.toPx()), alpha = 0.7f)
        drawRect(dark, Offset(0f, rail), Size(size.width, 1.5.dp.toPx()))
    }
}
