package app.porchlight.game

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The woods the porch is built from. */
internal enum class Wood(val light: Color, val base: Color, val dark: Color) {
    /** Panels, sheets, plates. */
    Walnut(Color(0xFF4A301E), Color(0xFF332014), Color(0xFF1C110A)),

    /** Card backs and deep frames. */
    Ebony(Color(0xFF33231A), Color(0xFF241710), Color(0xFF120B07)),

    /** Signs you press: the lightest wood on screen. */
    Oak(Color(0xFFD7A566), Color(0xFFB07C42), Color(0xFF7A4F24)),

    /** The night-stained wall. */
    Night(Color(0xFF25323F), Color(0xFF18222C), Color(0xFF0C1218)),
}

/**
 * A plank of [wood]: a soft top-to-bottom shade, grain lines that wander a little (the same every
 * time for one [seed]), and a bevel that catches light on the top edge and falls dark at the bottom.
 */
internal fun Modifier.wood(wood: Wood, corner: Dp = 14.dp, seed: Int = 1, bevel: Boolean = true): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), radius)) }
    val fill = Brush.verticalGradient(listOf(wood.light, wood.base, wood.dark))
    val random = Random(seed)
    val gap = 7.dp.toPx()
    val grain = Path()
    val shine = Path()
    var y = gap * random.nextFloat()
    while (y < size.height) {
        val into = if (random.nextFloat() < 0.3f) shine else grain
        val start = -size.width * 0.1f * random.nextFloat()
        val wobble = gap * (0.3f + random.nextFloat() * 0.9f)
        into.moveTo(start, y)
        var x = start
        var up = random.nextBoolean()
        while (x < size.width) {
            val step = size.width * (0.18f + random.nextFloat() * 0.3f)
            into.quadraticTo(x + step / 2f, y + if (up) -wobble else wobble, x + step, y)
            x += step
            up = !up
        }
        y += gap * (0.6f + random.nextFloat() * 1.2f)
    }
    val grainLine = Stroke(1.dp.toPx())
    val edge = Stroke(1.5.dp.toPx())
    val lip = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.28f),
        0.12f to Color.White.copy(alpha = 0.04f),
        0.85f to Color.Black.copy(alpha = 0.1f),
        1f to Color.Black.copy(alpha = 0.55f),
    )
    onDrawBehind {
        clipPath(outline) {
            drawRect(fill)
            drawPath(grain, wood.dark, alpha = 0.5f, style = grainLine)
            drawPath(shine, wood.light, alpha = 0.35f, style = grainLine)
        }
        if (bevel) {
            val half = edge.width / 2f
            drawRoundRect(lip, Offset(half, half), Size(size.width - edge.width, size.height - edge.width), radius, style = edge)
        }
    }
}

/** A raised panel of wood: its shadow, its grain, and its content clipped to its corners. */
internal fun Modifier.plank(wood: Wood = Wood.Walnut, corner: Dp = 14.dp, elevation: Dp = 6.dp, seed: Int = 1): Modifier =
    this
        .shadow(elevation, RoundedCornerShape(corner), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
        .wood(wood, corner, seed)
        .clip(RoundedCornerShape(corner))

/**
 * Polished metal: bands of light and dark run corner to corner, with a dark rim.
 * [copper] turns the brass to copper; [dull] is unlit iron for anything locked or switched off.
 */
internal fun Modifier.metal(corner: Dp = 12.dp, copper: Boolean = false, dull: Boolean = false): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val light = if (dull) Color(0xFF5A6068) else if (copper) Craft.copperLight else Craft.brassLight
    val base = if (dull) Craft.iron else if (copper) Craft.copper else Craft.brass
    val dark = if (dull) Craft.ironDark else if (copper) Craft.copperDark else Craft.brassDark
    val fill = Brush.linearGradient(
        0f to dark,
        0.22f to light,
        0.45f to base,
        0.7f to light,
        1f to dark,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val rim = Stroke(1.5.dp.toPx())
    val half = rim.width / 2f
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = radius)
        drawRoundRect(dark, Offset(half, half), Size(size.width - rim.width, size.height - rim.width), radius, style = rim)
        drawLine(
            Color.White.copy(alpha = 0.5f),
            Offset(radius.x, rim.width * 1.4f),
            Offset(size.width - radius.x, rim.width * 1.4f),
            1.dp.toPx(),
        )
    }
}

/** Old paper: brighter in the middle, foxed at the edges, a few stains, an inked hairline inside. */
internal fun Modifier.parchment(corner: Dp = 10.dp, seed: Int = 3): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), radius)) }
    val fill = Brush.radialGradient(
        0f to Color(0xFFF4E6C2),
        0.7f to Craft.parchment,
        1f to Craft.parchmentShade,
        center = Offset(size.width / 2f, size.height * 0.42f),
        radius = maxOf(size.width, size.height) * 0.75f,
    )
    val random = Random(seed)
    val stains = FloatArray(15) { random.nextFloat() }
    val inset = 5.dp.toPx()
    val hairline = Stroke(1.dp.toPx())
    onDrawBehind {
        clipPath(outline) {
            drawRect(fill)
            for (i in 0 until 5) {
                drawCircle(
                    Color(0xFF8A5A2B),
                    radius = size.minDimension * (0.08f + stains[i * 3 + 2] * 0.16f),
                    center = Offset(size.width * stains[i * 3], size.height * stains[i * 3 + 1]),
                    alpha = 0.07f,
                )
            }
        }
        drawRoundRect(
            Craft.parchmentInkSoft,
            Offset(inset, inset),
            Size(size.width - inset * 2, size.height - inset * 2),
            CornerRadius((radius.x - inset).coerceAtLeast(0f)),
            alpha = 0.45f,
            style = hairline,
        )
    }
}

/** Plum velvet, lit from the middle, as in a showcase. */
internal fun Modifier.velvet(corner: Dp = 14.dp): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val fill = Brush.radialGradient(
        0f to Craft.velvet,
        1f to Craft.velvetDeep,
        center = Offset(size.width / 2f, size.height * 0.4f),
        radius = maxOf(size.width, size.height) * 0.7f,
    )
    val sheen = Brush.linearGradient(
        0f to Color.Transparent,
        0.5f to Color.White.copy(alpha = 0.06f),
        1f to Color.Transparent,
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f),
    )
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = radius)
        drawRoundRect(sheen, cornerRadius = radius)
    }
}

private const val FLOURISH =
    "M2 40 C2 18 18 2 40 2 M9 40 C9 23 23 9 40 9 M2 52 C12 50 18 42 15 33 C13 27 5 27 5 33 C5 37 9 38 11 35 " +
        "M52 2 C50 12 42 18 33 15 C27 13 27 5 33 5 C37 5 38 9 35 11"

/**
 * A gilded reward panel: deep navy, a double gold rule, and a curled flourish in each corner.
 * This is the only place on the porch that is allowed to be this grand.
 */
internal fun Modifier.gilded(corner: Dp = 18.dp): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val fill = Brush.verticalGradient(listOf(Craft.navy, Craft.navyDeep))
    val gold = Brush.linearGradient(listOf(Craft.goldDeep, Craft.gold, Color(0xFFFFF0B8), Craft.gold, Craft.goldDeep))
    val outer = Stroke(2.5.dp.toPx())
    val inner = Stroke(1.dp.toPx())
    val gap = 7.dp.toPx()
    val unit = 1.1.dp.toPx()
    val curl = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val flourish = svg(FLOURISH)
    onDrawBehind {
        drawRoundRect(fill, cornerRadius = radius)
        val half = outer.width / 2f
        drawRoundRect(gold, Offset(half, half), Size(size.width - outer.width, size.height - outer.width), radius, style = outer)
        drawRoundRect(
            gold,
            Offset(gap, gap),
            Size(size.width - gap * 2, size.height - gap * 2),
            CornerRadius((radius.x - gap).coerceAtLeast(2f)),
            alpha = 0.7f,
            style = inner,
        )
        for (corner in 0..3) {
            val flipX = corner % 2 == 1
            val flipY = corner >= 2
            translate(if (flipX) size.width - gap * 1.6f else gap * 1.6f, if (flipY) size.height - gap * 1.6f else gap * 1.6f) {
                scale(if (flipX) -unit else unit, if (flipY) -unit else unit, pivot = Offset.Zero) {
                    drawPath(flourish, gold, style = curl)
                }
            }
        }
    }
}

private val gears = HashMap<Int, Path>()

/** A gear outline with [teeth] teeth, in a unit circle of radius 100. */
private fun gearPath(teeth: Int): Path = gears.getOrPut(teeth) {
    Path().apply {
        val step = 2 * PI / teeth
        for (i in 0 until teeth) {
            val a = i * step
            val points = arrayOf(a - step * 0.22 to 82.0, a - step * 0.14 to 100.0, a + step * 0.14 to 100.0, a + step * 0.22 to 82.0)
            for ((j, p) in points.withIndex()) {
                val x = (cos(p.first) * p.second).toFloat()
                val y = (sin(p.first) * p.second).toFloat()
                if (i == 0 && j == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        close()
    }
}

/** A brass or copper gear, turned by [degrees], with spokes and a hub. */
internal fun DrawScope.gear(centre: Offset, radius: Float, teeth: Int, degrees: Float, copper: Boolean = false, alpha: Float = 1f) {
    val light = if (copper) Craft.copperLight else Craft.brassLight
    val base = if (copper) Craft.copper else Craft.brass
    val dark = if (copper) Craft.copperDark else Craft.brassDark
    translate(centre.x, centre.y) {
        rotate(degrees, pivot = Offset.Zero) {
            scale(radius / 100f, radius / 100f, pivot = Offset.Zero) {
                drawPath(gearPath(teeth), base, alpha = alpha)
                drawPath(gearPath(teeth), dark, alpha = alpha, style = Stroke(5f, join = StrokeJoin.Round))
                drawCircle(dark, 62f, Offset.Zero, alpha = alpha)
                for (i in 0 until 4) {
                    rotate(i * 45f, pivot = Offset.Zero) {
                        drawLine(base, Offset(-62f, 0f), Offset(62f, 0f), 13f, alpha = alpha)
                    }
                }
                drawCircle(light, 20f, Offset.Zero, alpha = alpha)
                drawCircle(dark, 8f, Offset.Zero, alpha = alpha)
            }
        }
    }
}

/** A domed brass rivet. */
internal fun DrawScope.rivet(centre: Offset, radius: Float, alpha: Float = 1f) {
    drawCircle(Craft.brassDark, radius, centre, alpha = alpha)
    drawCircle(Craft.brass, radius * 0.78f, centre, alpha = alpha)
    drawCircle(Craft.brassLight, radius * 0.3f, Offset(centre.x - radius * 0.25f, centre.y - radius * 0.28f), alpha = alpha)
}

/** A copper pipe along [path]: a dark body, the copper, and a line of light down its length. */
internal fun DrawScope.pipe(path: Path, width: Float, alpha: Float = 1f) {
    drawPath(path, Craft.copperDark, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Craft.copper, alpha = alpha, style = Stroke(width * 0.72f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, Craft.copperLight, alpha = alpha * 0.8f, style = Stroke(width * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
