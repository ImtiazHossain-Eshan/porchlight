package app.porchlight.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// The five chapter scenes behind the level map. Each chapter is its own painted place, in its own
// colours, so the long scroll changes as you go: warm wood on the porch, green garden, misty woods,
// indigo rooftops, and a plum sky turning gold over the last porch.

/** A chapter scene's colours: its sky from top to bottom, two depths of silhouette, its line colour and its light. */
internal class SceneLook(
    val top: Color,
    val bottom: Color,
    val far: Color,
    val near: Color,
    /** Lines, small art and the chapter's labels. */
    val ink: Color,
    /** The scene's own light: lamp glow, fireflies, lit windows. */
    val light: Color,
)

private val LOOKS = listOf(
    // The porch: a warm wooden wall under the lamp.
    SceneLook(Color(0xFF2C1B10), Color(0xFF5E3A1C), Color(0xFF3E2716), Color(0xFF180E07), Color(0xFFF0D3A0), Color(0xFFFFC56A)),
    // The garden: green dusk, hedges and fireflies.
    SceneLook(Color(0xFF0B2219), Color(0xFF2B5A33), Color(0xFF1C4429), Color(0xFF0A1C11), Color(0xFFD3EDB5), Color(0xFFE2F77E)),
    // The woods: misty teal, pines, rust leaves.
    SceneLook(Color(0xFF071A20), Color(0xFF1D4A4A), Color(0xFF15383D), Color(0xFF051215), Color(0xFFBFE2DA), Color(0xFFE88A4A)),
    // The rooftops: an indigo night over the town.
    SceneLook(Color(0xFF0A0C2A), Color(0xFF2E3070), Color(0xFF1E2050), Color(0xFF090A20), Color(0xFFCDD0F6), Color(0xFFFFD27A)),
    // The last porch: plum night breaking into a gold dawn.
    SceneLook(Color(0xFF210B2A), Color(0xFF94502F), Color(0xFF431A40), Color(0xFF1A0715), Color(0xFFF6D6C2), Color(0xFFFFBE6A)),
)

internal fun sceneLook(chapter: Int): SceneLook = LOOKS[(chapter - 1).coerceIn(0, LOOKS.lastIndex)]

/**
 * Paints chapter [chapter]'s whole scene into a sheet with rounded [corner]s: sky, edge scenery,
 * and a feature in each open space beside the path. [top] is where the header ends; [stops] are
 * the coins' centres, so the art stays clear of them.
 */
internal fun DrawScope.sceneBackdrop(chapter: Int, top: Float, corner: Float, stops: List<Offset>) {
    val look = sceneLook(chapter)
    val w = size.width
    val h = size.height
    val line = 1.4f * density
    val random = Random(chapter * 7919)
    val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(corner))) }

    clipPath(outline) {
        drawRect(Brush.verticalGradient(listOf(look.top, look.bottom)))

        // The open space beside every coin that sits off to one side: that is where a feature goes.
        val spots = stops.mapNotNull { s ->
            when {
                s.x < w * 0.4f -> Offset(w * 0.74f, s.y)
                s.x > w * 0.6f -> Offset(w * 0.26f, s.y)
                else -> null
            }
        }
        val r = w * 0.15f

        when (chapter) {
            1 -> porch(look, w, h, top, line, spots, r, random)
            2 -> garden(look, w, h, top, line, spots, r, random)
            3 -> woods(look, w, h, top, line, spots, r, random)
            4 -> rooftops(look, w, h, top, line, spots, r, random)
            else -> lastPorch(look, w, h, top, line, spots, r, random)
        }

        // A soft vignette, so the edges fall away and the coins stand forward.
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.45f),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.75f,
            ),
        )
        // The header sits on a slightly darker band.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), 0f, top), size = Size(w, top))
    }
    val inset = 5f * density
    drawRoundRect(
        look.ink.copy(alpha = 0.28f),
        Offset(inset, inset),
        Size(w - inset * 2, h - inset * 2),
        CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(1f * density),
    )
    drawLine(look.ink.copy(alpha = 0.3f), Offset(w * 0.05f, top), Offset(w * 0.95f, top), line)
}

// ---------------------------------------------------------------------------------------------
// Shared pieces

private fun DrawScope.glow(c: Offset, radius: Float, color: Color, alpha: Float = 0.5f) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), c, radius), radius, c)
}

/** Small lights scattered down the sheet, kept off the path's middle. */
private fun DrawScope.motes(look: SceneLook, w: Float, h: Float, top: Float, random: Random, count: Int, size: Float, glowSize: Float) {
    repeat(count) {
        val edge = random.nextFloat()
        val x = if (edge < 0.5f) w * (0.03f + random.nextFloat() * 0.14f) else w * (0.83f + random.nextFloat() * 0.14f)
        val c = Offset(x, top + random.nextFloat() * (h - top))
        glow(c, glowSize * (0.6f + random.nextFloat() * 0.6f), look.light, 0.35f)
        drawCircle(look.light.copy(alpha = 0.85f), size, c)
    }
}

private fun DrawScope.moths(look: SceneLook, around: Offset, radius: Float, count: Int, random: Random) {
    repeat(count) { k ->
        val a = k * (2 * Math.PI / count) + random.nextDouble() * 0.6
        val d = radius * (0.7f + random.nextFloat() * 0.4f)
        glyph(Glyph.Moth, Offset(around.x + (d * cos(a)).toFloat(), around.y + (d * sin(a)).toFloat()), radius * 0.4f, look.ink, alpha = 0.75f)
    }
}

private fun DrawScope.lantern(look: SceneLook, c: Offset, s: Float) {
    glow(c, s * 2.4f, look.light, 0.45f)
    drawLine(look.ink.copy(alpha = 0.5f), Offset(c.x, c.y - s * 2.2f), Offset(c.x, c.y - s * 0.8f), 1.4f * density)
    glyph(Glyph.Lamp, c, s * 1.6f, look.light, alpha = 0.95f)
}

private fun DrawScope.pine(c: Offset, height: Float, color: Color) {
    val half = height * 0.3f
    val tree = Path().apply {
        moveTo(c.x, c.y - height)
        for (tier in 1..3) {
            val y = c.y - height + height * tier / 3.2f
            val wide = half * tier / 3f + half * 0.25f
            lineTo(c.x + wide, y)
            lineTo(c.x + wide * 0.45f, y)
        }
        lineTo(c.x + half * 0.12f, c.y - height * 0.04f)
        lineTo(c.x + half * 0.12f, c.y)
        lineTo(c.x - half * 0.12f, c.y)
        lineTo(c.x - half * 0.12f, c.y - height * 0.04f)
        for (tier in 3 downTo 1) {
            val y = c.y - height + height * tier / 3.2f
            val wide = half * tier / 3f + half * 0.25f
            lineTo(c.x - wide * 0.45f, y)
            lineTo(c.x - wide, y)
        }
        close()
    }
    drawPath(tree, color)
}

// ---------------------------------------------------------------------------------------------
// 1. The porch: a wall of boards, lanterns with moths, warm windows, a rail along the foot.

private fun DrawScope.porch(look: SceneLook, w: Float, h: Float, top: Float, line: Float, spots: List<Offset>, r: Float, random: Random) {
    // Vertical boards with a nail at each end of every board.
    val boards = 7
    for (k in 1 until boards) {
        val x = w * k / boards
        drawLine(look.near.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, h), line * 1.6f)
        drawLine(look.ink.copy(alpha = 0.06f), Offset(x + line * 1.6f, 0f), Offset(x + line * 1.6f, h), line)
    }
    for (k in 0 until boards) {
        var y = top + 30f * density
        while (y < h) {
            drawCircle(look.near.copy(alpha = 0.6f), 1.6f * density, Offset(w * (k + 0.5f) / boards, y))
            y += 160f * density
        }
    }
    // Grain: a few long, faint strokes per board.
    repeat(28) {
        val x = w * random.nextFloat()
        val y = h * random.nextFloat()
        drawLine(look.far.copy(alpha = 0.5f), Offset(x, y), Offset(x + w * 0.004f, y + h * 0.06f), line)
    }
    // The house's own lamp, warming the top of the sheet.
    glow(Offset(w * 0.15f, top), w * 0.6f, look.light, 0.28f)

    spots.forEachIndexed { i, c ->
        if (i % 2 == 0) {
            lantern(look, Offset(c.x, c.y - r * 0.2f), r * 0.42f)
            moths(look, Offset(c.x, c.y - r * 0.2f), r * 0.85f, 3, random)
        } else {
            // A window with the warm light of the house behind it.
            val ww = r * 1.2f
            val wh = r * 1.4f
            val tl = Offset(c.x - ww / 2, c.y - wh / 2)
            glow(c, r * 1.4f, look.light, 0.25f)
            drawRoundRect(look.near, Offset(tl.x - line * 3, tl.y - line * 3), Size(ww + line * 6, wh + line * 6), CornerRadius(line * 2))
            drawRect(Brush.verticalGradient(listOf(look.light.copy(alpha = 0.95f), Dusk.glowDeep.copy(alpha = 0.8f)), tl.y, tl.y + wh), tl, Size(ww, wh))
            drawLine(look.near, Offset(c.x, tl.y), Offset(c.x, tl.y + wh), line * 2.5f)
            drawLine(look.near, Offset(tl.x, c.y), Offset(tl.x + ww, c.y), line * 2.5f)
            // A sill under it.
            drawRect(look.far, Offset(tl.x - line * 6, tl.y + wh + line * 3), Size(ww + line * 12, line * 4))
        }
    }

    // The rail along the foot of the sheet.
    val railTop = h - w * 0.12f
    drawRect(look.near, Offset(0f, railTop), Size(w, line * 5))
    var x = w * 0.02f
    while (x < w) {
        drawRect(look.near, Offset(x, railTop), Size(line * 4, h - railTop))
        x += w * 0.06f
    }
    drawRect(look.near, Offset(0f, h - line * 8), Size(w, line * 8))
}

// ---------------------------------------------------------------------------------------------
// 2. The garden: hedges down both edges, flower bushes and fences, fireflies.

private fun DrawScope.garden(look: SceneLook, w: Float, h: Float, top: Float, line: Float, spots: List<Offset>, r: Float, random: Random) {
    // Hedges: overlapping round leaves of two greens down each edge.
    for (side in listOf(0f, 1f)) {
        var y = top
        while (y < h + r) {
            val x = if (side == 0f) w * 0.01f else w * 0.99f
            drawCircle(look.far, w * (0.07f + random.nextFloat() * 0.03f), Offset(x, y))
            drawCircle(look.near, w * (0.05f + random.nextFloat() * 0.02f), Offset(x + (if (side == 0f) -1 else 1) * w * 0.02f, y + w * 0.05f))
            y += w * 0.09f
        }
    }
    val petals = listOf(Color(0xFFF08AA6), Color(0xFFFFD36B), Color(0xFFB79CFF), Color(0xFFFFFFFF))
    fun bloom(c: Offset, s: Float, color: Color) {
        for (k in 0 until 5) {
            val a = k * 72.0 * Math.PI / 180
            drawCircle(color.copy(alpha = 0.9f), s, Offset(c.x + (s * 1.2f * cos(a)).toFloat(), c.y + (s * 1.2f * sin(a)).toFloat()))
        }
        drawCircle(Dusk.glowDeep, s * 0.7f, c)
    }
    spots.forEachIndexed { i, c ->
        if (i % 2 == 0) {
            // A flowering bush.
            for ((dx, dy, s) in listOf(Triple(-0.5f, 0.2f, 0.55f), Triple(0.45f, 0.25f, 0.5f), Triple(0f, -0.05f, 0.65f))) {
                drawCircle(look.far, r * s, Offset(c.x + r * dx, c.y + r * dy))
            }
            for ((dx, dy, s) in listOf(Triple(-0.3f, 0.35f, 0.4f), Triple(0.3f, 0.4f, 0.38f))) {
                drawCircle(look.near, r * s, Offset(c.x + r * dx, c.y + r * dy))
            }
            repeat(7) {
                val a = random.nextDouble() * Math.PI * 2
                val d = r * 0.55f * random.nextFloat()
                bloom(Offset(c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat() * 0.8f), r * 0.07f, petals[random.nextInt(petals.size)])
            }
        } else {
            // A picket fence with flowers at its foot.
            val left = c.x - r
            val base = c.y + r * 0.5f
            for (k in 0 until 6) {
                val x = left + k * r * 0.4f
                val picket = Path().apply {
                    moveTo(x, base); lineTo(x, base - r * 0.8f); lineTo(x + r * 0.11f, base - r * 0.95f)
                    lineTo(x + r * 0.22f, base - r * 0.8f); lineTo(x + r * 0.22f, base); close()
                }
                drawPath(picket, look.ink.copy(alpha = 0.55f))
            }
            drawRect(look.ink.copy(alpha = 0.45f), Offset(left - r * 0.1f, base - r * 0.6f), Size(r * 2.3f, r * 0.08f))
            drawRect(look.ink.copy(alpha = 0.45f), Offset(left - r * 0.1f, base - r * 0.25f), Size(r * 2.3f, r * 0.08f))
            for (k in 0 until 5) bloom(Offset(left + k * r * 0.48f + r * 0.1f, base - r * 0.02f), r * 0.07f, petals[(k + i) % petals.size])
        }
    }
    motes(look, w, h, top, random, 26, 1.8f * density, 10f * density)
    // Grass along the foot.
    var x = 0f
    while (x < w) {
        val blade = Path().apply { moveTo(x, h); lineTo(x + w * 0.012f, h - w * (0.05f + random.nextFloat() * 0.05f)); lineTo(x + w * 0.024f, h); close() }
        drawPath(blade, look.near)
        x += w * 0.018f
    }
}

// ---------------------------------------------------------------------------------------------
// 3. The woods: ranks of pines at both edges in the mist, stands of pines, falling leaves.

private fun DrawScope.woods(look: SceneLook, w: Float, h: Float, top: Float, line: Float, spots: List<Offset>, r: Float, random: Random) {
    // Far trees, then mist, then near trees, down both edges.
    for ((color, size, step) in listOf(Triple(look.far, 0.26f, 0.11f), Triple(look.near, 0.2f, 0.13f))) {
        var y = top + w * 0.15f
        while (y < h + w * 0.2f) {
            pine(Offset(w * (0.03f + random.nextFloat() * 0.05f), y), w * size * (0.8f + random.nextFloat() * 0.4f), color)
            pine(Offset(w * (0.92f + random.nextFloat() * 0.05f), y + w * 0.05f), w * size * (0.8f + random.nextFloat() * 0.4f), color)
            y += w * step
        }
        if (color == look.far) {
            // Mist bands drifting across.
            var m = top + w * 0.1f
            while (m < h) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, look.ink.copy(alpha = 0.08f), Color.Transparent), m, m + w * 0.18f),
                    Offset(0f, m), Size(w, w * 0.18f),
                )
                m += w * 0.45f
            }
        }
    }
    spots.forEachIndexed { i, c ->
        if (i % 2 == 0) {
            pine(Offset(c.x - r * 0.45f, c.y + r * 0.6f), r * 1.5f, look.far)
            pine(Offset(c.x + r * 0.4f, c.y + r * 0.7f), r * 1.7f, look.far)
            pine(Offset(c.x, c.y + r * 0.85f), r * 1.3f, look.near)
        } else {
            // A fallen log with a toadstool on it.
            drawRoundRect(look.near, Offset(c.x - r, c.y + r * 0.1f), Size(r * 2f, r * 0.42f), CornerRadius(r * 0.2f))
            drawCircle(look.far, r * 0.17f, Offset(c.x + r * 0.85f, c.y + r * 0.31f))
            drawCircle(look.ink.copy(alpha = 0.3f), r * 0.17f, Offset(c.x + r * 0.85f, c.y + r * 0.31f), style = Stroke(line))
            drawRect(look.ink.copy(alpha = 0.8f), Offset(c.x - r * 0.35f, c.y - r * 0.15f), Size(r * 0.1f, r * 0.27f))
            val cap = Path().apply {
                moveTo(c.x - r * 0.52f, c.y - r * 0.12f)
                quadraticTo(c.x - r * 0.3f, c.y - r * 0.55f, c.x - r * 0.08f, c.y - r * 0.12f)
                close()
            }
            drawPath(cap, look.light)
            drawCircle(look.ink, r * 0.04f, Offset(c.x - r * 0.36f, c.y - r * 0.28f))
            drawCircle(look.ink, r * 0.03f, Offset(c.x - r * 0.22f, c.y - r * 0.22f))
        }
    }
    // Leaves on the way down.
    repeat(16) {
        val c = Offset(w * (0.05f + random.nextFloat() * 0.9f), top + random.nextFloat() * (h - top))
        if (stopsClear(c, spots, r)) glyph(Glyph.DeadLeaf, c, w * 0.045f, look.light, alpha = 0.75f)
    }
}

private fun stopsClear(c: Offset, spots: List<Offset>, r: Float) = spots.none { (it - c).getDistance() < r * 1.1f }

// ---------------------------------------------------------------------------------------------
// 4. The rooftops: stars, a big moon, and blocks of roofs with lit windows.

private fun DrawScope.rooftops(look: SceneLook, w: Float, h: Float, top: Float, line: Float, spots: List<Offset>, r: Float, random: Random) {
    repeat(70) {
        val c = Offset(w * random.nextFloat(), top + random.nextFloat() * (h - top))
        val s = (0.6f + random.nextFloat() * 1.2f) * density
        drawCircle(look.ink.copy(alpha = 0.3f + random.nextFloat() * 0.5f), s, c)
    }
    fun twinkle(c: Offset, s: Float) {
        glow(c, s * 2.5f, look.ink, 0.25f)
        drawLine(look.ink, Offset(c.x - s, c.y), Offset(c.x + s, c.y), line, StrokeCap.Round)
        drawLine(look.ink, Offset(c.x, c.y - s), Offset(c.x, c.y + s), line, StrokeCap.Round)
    }
    repeat(8) { twinkle(Offset(w * (0.04f + random.nextFloat() * 0.92f), top + random.nextFloat() * (h - top)), 5f * density) }

    fun block(c: Offset) {
        // Three houses shoulder to shoulder, with chimneys and a few lit windows.
        val base = c.y + r * 0.9f
        for ((dx, wide, tall) in listOf(Triple(-0.95f, 0.7f, 0.9f), Triple(-0.25f, 0.6f, 1.3f), Triple(0.35f, 0.65f, 1.0f))) {
            val left = c.x + r * dx
            val right = left + r * wide
            val eaves = base - r * tall
            val house = Path().apply {
                moveTo(left, base); lineTo(left, eaves); lineTo((left + right) / 2, eaves - r * 0.35f); lineTo(right, eaves); lineTo(right, base); close()
            }
            drawRect(look.near, Offset(right - r * 0.2f, eaves - r * 0.38f), Size(r * 0.1f, r * 0.3f))
            drawPath(house, look.near)
            drawPath(house, look.ink.copy(alpha = 0.18f), style = Stroke(line))
            var y = eaves + r * 0.12f
            while (y < base - r * 0.2f) {
                var x = left + r * 0.1f
                while (x < right - r * 0.2f) {
                    if (random.nextFloat() < 0.5f) {
                        glow(Offset(x + r * 0.06f, y + r * 0.08f), r * 0.2f, look.light, 0.35f)
                        drawRect(look.light, Offset(x, y), Size(r * 0.12f, r * 0.16f))
                    }
                    x += r * 0.2f
                }
                y += r * 0.26f
            }
        }
    }
    spots.forEachIndexed { i, c ->
        if (i == 0) {
            glow(c, r * 1.8f, look.light, 0.25f)
            drawCircle(Color(0xFFFFF0C8), r * 0.6f, c)
            drawCircle(Color(0xFFE8D6A8), r * 0.12f, Offset(c.x - r * 0.2f, c.y - r * 0.1f))
            drawCircle(Color(0xFFE8D6A8), r * 0.08f, Offset(c.x + r * 0.22f, c.y + r * 0.2f))
        } else {
            block(c)
        }
    }
    // The town's skyline along the foot.
    var x = 0f
    val skyline = Path().apply { moveTo(0f, h) }
    while (x < w) {
        val roof = h - w * (0.08f + random.nextFloat() * 0.1f)
        val wide = w * (0.08f + random.nextFloat() * 0.07f)
        skyline.lineTo(x, roof); skyline.lineTo(x + wide / 2, roof - w * 0.04f); skyline.lineTo(x + wide, roof)
        x += wide
    }
    skyline.lineTo(w, h); skyline.close()
    drawPath(skyline, look.near)
}

// ---------------------------------------------------------------------------------------------
// 5. The last porch: the night going, the dawn coming, moths in swarms, and the house at the end.

private fun DrawScope.lastPorch(look: SceneLook, w: Float, h: Float, top: Float, line: Float, spots: List<Offset>, r: Float, random: Random) {
    // Fading stars at the top of the sheet.
    repeat(40) {
        val y = top + random.nextFloat() * (h - top) * 0.5f
        val fade = 1f - (y - top) / ((h - top) * 0.5f)
        drawCircle(look.ink.copy(alpha = 0.7f * fade), (0.6f + random.nextFloat()) * density, Offset(w * random.nextFloat(), y))
    }
    // Dawn rising from the foot.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, look.light.copy(alpha = 0.35f)), h * 0.55f, h), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
    // Hills against the dawn.
    for ((color, y0, amp) in listOf(Triple(look.far, 0.86f, 0.05f), Triple(look.near, 0.92f, 0.04f))) {
        val hill = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * y0)
            var x = 0f
            while (x < w) {
                quadraticTo(x + w * 0.12f, h * (y0 - amp) - random.nextFloat() * h * amp, x + w * 0.25f, h * y0)
                x += w * 0.25f
            }
            lineTo(w, h)
            close()
        }
        drawPath(hill, color)
    }
    spots.forEachIndexed { i, c ->
        when {
            i == spots.lastIndex -> {
                // The house at the end of the path, its lamp blazing.
                val base = c.y + r * 0.9f
                val house = Path().apply {
                    moveTo(c.x - r * 0.8f, base); lineTo(c.x - r * 0.8f, base - r * 0.9f); lineTo(c.x, base - r * 1.5f)
                    lineTo(c.x + r * 0.8f, base - r * 0.9f); lineTo(c.x + r * 0.8f, base); close()
                }
                val lamp = Offset(c.x, base - r * 0.55f)
                for (k in 0 until 9) {
                    val a = Math.toRadians(-180.0 + k * 22.5)
                    drawLine(look.light.copy(alpha = 0.25f), lamp, Offset(lamp.x + (r * 2.2f * cos(a)).toFloat(), lamp.y + (r * 2.2f * sin(a)).toFloat()), line * 2)
                }
                drawPath(house, look.near)
                drawRect(look.light, Offset(c.x - r * 0.18f, base - r * 0.45f), Size(r * 0.36f, r * 0.45f))
                glow(lamp, r * 1.3f, look.light, 0.7f)
                drawCircle(Color(0xFFFFF4D6), r * 0.08f, lamp)
                moths(look, lamp, r * 0.9f, 5, random)
            }
            i % 2 == 0 -> {
                lantern(look, c, r * 0.42f)
                moths(look, c, r * 0.8f, 4, random)
            }
            else -> {
                // A swarm on its way to the light.
                glow(c, r, look.light, 0.18f)
                repeat(6) {
                    val a = random.nextDouble() * Math.PI * 2
                    val d = r * random.nextFloat()
                    glyph(Glyph.Moth, Offset(c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat()), r * (0.22f + random.nextFloat() * 0.14f), look.ink, alpha = 0.65f)
                }
            }
        }
    }
}
