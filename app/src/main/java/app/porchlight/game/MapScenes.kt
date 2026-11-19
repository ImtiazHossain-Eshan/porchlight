package app.porchlight.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// The five chapter scenes behind the level map. Each chapter is its own living place, in its own
// colours: warm boards and swinging lanterns on the porch, a garden of fireflies, misty woods with
// leaves tumbling down, an indigo night over the rooftops, and a plum sky turning gold over the
// last porch.
//
// A scene is drawn in layers so it has depth: a still sky, far scenery, near scenery, and the
// moving life on top. The far and near layers slide by different amounts as the map scrolls and
// the phone tilts (see ChapterScene), and the life layer is redrawn every frame from the clock.

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

/** How far each layer lags the coins as the map scrolls: the further back, the more it lags. */
internal const val FAR_SCROLL = 0.10f
internal const val NEAR_SCROLL = 0.04f
internal const val LIFE_SCROLL = 0.06f

/**
 * The open space beside every coin that sits off to one side of the path: where a scene puts its
 * features, clear of the coins and the pipe.
 */
private fun DrawScope.spots(stops: List<Offset>): List<Offset> = stops.mapNotNull { s ->
    when {
        s.x < size.width * 0.4f -> Offset(size.width * 0.74f, s.y)
        s.x > size.width * 0.6f -> Offset(size.width * 0.26f, s.y)
        else -> null
    }
}

private val DrawScope.featureSize get() = size.width * 0.15f
private val DrawScope.thin get() = 1.4f * density

/** How far past the sheet a sliding layer paints, so its edge never shows. */
private val DrawScope.overscan get() = size.height * 0.15f

// ---------------------------------------------------------------------------------------------
// The layers

/** The still sky behind everything. */
internal fun DrawScope.sceneSky(chapter: Int) {
    val look = sceneLook(chapter)
    drawRect(Brush.verticalGradient(listOf(look.top, look.bottom)))
    when (chapter) {
        1 -> glow(Offset(size.width * 0.15f, 0f), size.width * 0.7f, look.light, 0.25f)
        5 -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, look.light.copy(alpha = 0.38f)), size.height * 0.5f, size.height))
    }
}

/**
 * Each scene's light, behind the scenery and in front of the sky, at [t] seconds: lamplight on the
 * porch, moonbeams in the garden, sunbeams through the woods, an aurora over the rooftops, and the
 * sun rising behind the last porch's hills. Soft, tapered shafts that breathe, never hard lines.
 */
internal fun DrawScope.sceneLight(chapter: Int, t: Float, top: Float) {
    val look = sceneLook(chapter)
    val w = size.width
    val h = size.height
    when (chapter) {
        1 -> godRays(Offset(w * 0.12f, top * 0.6f), 62f, 64f, 8, h * 0.62f, look.light, 0.16f, t, seed = 1)
        2 -> godRays(Offset(w * 0.9f, -h * 0.02f), 112f, 38f, 6, h * 0.8f, Color(0xFFE6F5D0), 0.09f, t, seed = 2)
        3 -> {
            godRays(Offset(w * 1.08f, top - h * 0.04f), 116f, 26f, 7, h * 1.1f, Color(0xFFE4F6EC), 0.18f, t, seed = 3)
            godRays(Offset(w * 0.2f, top - h * 0.06f), 78f, 14f, 3, h * 0.9f, Color(0xFFE4F6EC), 0.07f, t, seed = 33)
        }
        4 -> aurora(top, t)
        else -> {
            // The sun, just breaking the far hills, and its rays fanning up into the dawn.
            val sun = Offset(w * 0.6f, h * 0.86f)
            val r = w * 0.11f
            glow(sun, w * 0.95f, look.light, 0.30f)
            godRays(sun, -90f, 170f, 13, h * 0.7f, Color(0xFFFFD58A), 0.20f, t, seed = 5)
            glow(sun, r * 2.2f, Color(0xFFFFE2A6), 0.55f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFBEA), Color(0xFFFFD27A)), sun, r), r, sun)
        }
    }
}

private val rayPath = Path()

/**
 * Light falling from [origin]: [count] tapered shafts spread over [spread] degrees around [direction]
 * (0 is to the right, 90 straight down). Each has its own width, reach and slow breath, sways a
 * degree or two, fades out along its length, and adds onto what is under it. With [motes], dust
 * drifts in the light.
 */
private fun DrawScope.godRays(
    origin: Offset,
    direction: Float,
    spread: Float,
    count: Int,
    length: Float,
    color: Color,
    alpha: Float,
    t: Float,
    seed: Int,
    motes: Boolean = true,
) {
    val random = Random(seed * 977)
    for (k in 0 until count) {
        val jitter = random.nextFloat() - 0.5f
        val widen = 0.35f + 0.65f * random.nextFloat()
        val reach = length * (0.6f + 0.4f * random.nextFloat())
        val rate = 0.25f + 0.35f * random.nextFloat()
        val phase = random.nextFloat() * 6.28f
        val step = spread / count
        val a = direction - spread / 2f + step * (k + 0.5f + 0.6f * jitter) + 1.8f * sin(t * 0.11f + phase)
        val half = step * widen * 0.5f
        val breath = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * rate + phase))
        val a0 = Math.toRadians((a - half).toDouble())
        val a1 = Math.toRadians((a + half).toDouble())
        rayPath.reset()
        rayPath.moveTo(origin.x, origin.y)
        rayPath.lineTo(origin.x + (reach * cos(a0)).toFloat(), origin.y + (reach * sin(a0)).toFloat())
        rayPath.lineTo(origin.x + (reach * cos(a1)).toFloat(), origin.y + (reach * sin(a1)).toFloat())
        rayPath.close()
        val strength = alpha * breath
        drawPath(
            rayPath,
            Brush.radialGradient(
                0f to color.copy(alpha = strength.coerceIn(0f, 1f)),
                0.35f to color.copy(alpha = (strength * 0.55f).coerceIn(0f, 1f)),
                1f to Color.Transparent,
                center = origin,
                radius = reach,
            ),
            blendMode = BlendMode.Plus,
        )
        if (motes) {
            // A few motes turning slowly in this shaft.
            for (m in 0 until 3) {
                val along = ((random.nextFloat() + t * 0.012f * (1 + m)) % 1f) * reach * 0.85f + reach * 0.08f
                val side = (random.nextFloat() - 0.5f) * half * 1.4f
                val ang = Math.toRadians((a + side).toDouble())
                val at = Offset(origin.x + (along * cos(ang)).toFloat(), origin.y + (along * sin(ang)).toFloat())
                val twinkle = 0.5f + 0.5f * sin(t * (1f + m) + phase + m)
                drawCircle(color.copy(alpha = (0.5f * twinkle * breath * (1f - along / reach)).coerceIn(0f, 1f)), (0.9f + m * 0.4f) * density, at)
            }
        }
    }
}

private val hemPath = Path()

/**
 * The northern lights over the rooftops, drawn the way they hang: tall curtains with a bright, wavy
 * hem along the bottom, fading up into the sky, with soft folds of light drifting along them. Three
 * curtains (green in front, teal, violet high behind) ripple at their own pace.
 */
private fun DrawScope.aurora(top: Float, t: Float) {
    val w = size.width
    val h = size.height
    // A wide wash of green under it all, so the whole sky above the town glows.
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to Color(0xFF3FE0A8).copy(alpha = 0.10f + 0.03f * sin(t * 0.4f)),
            1f to Color.Transparent,
            startY = top - h * 0.02f,
            endY = top + h * 0.36f,
        ),
        blendMode = BlendMode.Plus,
    )
    // Back to front: violet high and far, teal, then the bright green curtain.
    val curtains = listOf(
        Triple(Color(0xFFB07CFF), 0.10f, 0.13f),
        Triple(Color(0xFF3FE6B0), 0.17f, 0.16f),
        Triple(Color(0xFF8CFF6E), 0.25f, 0.21f),
    )
    curtains.forEachIndexed { band, (colour, hemAt, tall) ->
        val base = top + h * hemAt
        val rise = h * tall
        val speed = 1f + band * 0.35f
        fun hem(u: Float) = base +
            h * 0.03f * sin(u * 4.6f + t * 0.20f * speed + band * 2.1f) +
            h * 0.012f * sin(u * 11f - t * 0.33f * speed + band)
        fun height(u: Float) = rise * (0.65f + 0.35f * sin(u * 6.3f + t * 0.27f + band * 1.7f))
        val steps = 56
        rayPath.reset()
        hemPath.reset()
        for (i in 0..steps) {
            val u = -0.05f + 1.1f * i / steps
            val y = hem(u)
            if (i == 0) { rayPath.moveTo(u * w, y); hemPath.moveTo(u * w, y) } else { rayPath.lineTo(u * w, y); hemPath.lineTo(u * w, y) }
        }
        for (i in steps downTo 0) {
            val u = -0.05f + 1.1f * i / steps
            rayPath.lineTo(u * w, hem(u) - height(u))
        }
        rayPath.close()
        val glow = (0.34f + 0.08f * band) * (0.85f + 0.15f * sin(t * 0.5f + band * 1.3f))
        // The curtain: brightest at its hem, fading up into the dark.
        drawPath(
            rayPath,
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to colour.copy(alpha = glow * 0.35f),
                0.9f to colour.copy(alpha = glow * 0.8f),
                1f to colour.copy(alpha = glow),
                startY = base - rise - h * 0.03f,
                endY = base + h * 0.04f,
            ),
            blendMode = BlendMode.Plus,
        )
        // Folds of light drifting along the curtain: a few wide, soft bands, never thin stripes.
        clipPath(rayPath) {
            for (k in 0 until 10) {
                val x = w * (((k * 0.113f + t * 0.010f * speed * (if (k % 2 == 0) 1 else -1)) % 1f + 1f) % 1f)
                val half = w * (0.022f + 0.016f * (k % 3))
                val shine = 0.45f + 0.3f * sin(t * 0.6f + k * 1.9f + band)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, colour.copy(alpha = (shine * glow * 1.4f).coerceAtMost(1f)), Color.Transparent),
                        startX = x - half,
                        endX = x + half,
                    ),
                    Offset(x - half, base - rise - h * 0.03f),
                    androidx.compose.ui.geometry.Size(half * 2f, rise + h * 0.08f),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        // The hem itself: a soft halo and a bright thread of light.
        drawPath(hemPath, colour.copy(alpha = glow * 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(9f * density), blendMode = BlendMode.Plus)
        drawPath(hemPath, colour.copy(alpha = glow * 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f * density), blendMode = BlendMode.Plus)
    }
}

/** Scenery far back: it slides the most against the coins. */
internal fun DrawScope.sceneFar(chapter: Int, top: Float, stops: List<Offset>) {
    val look = sceneLook(chapter)
    val random = Random(chapter * 7919)
    val w = size.width
    val h = size.height
    val from = -overscan
    val to = h + overscan
    when (chapter) {
        1 -> {
            // A wall of vertical boards, with nails and grain.
            val boards = 7
            for (k in 1 until boards) {
                val x = w * k / boards
                drawLine(look.near.copy(alpha = 0.55f), Offset(x, from), Offset(x, to), thin * 1.6f)
                drawLine(look.ink.copy(alpha = 0.06f), Offset(x + thin * 1.6f, from), Offset(x + thin * 1.6f, to), thin)
            }
            for (k in 0 until boards) {
                var y = from + 30f * density
                while (y < to) {
                    drawCircle(look.near.copy(alpha = 0.6f), 1.6f * density, Offset(w * (k + 0.5f) / boards, y))
                    y += 160f * density
                }
            }
            repeat(32) {
                val x = w * random.nextFloat()
                val y = from + (to - from) * random.nextFloat()
                drawLine(look.far.copy(alpha = 0.5f), Offset(x, y), Offset(x + w * 0.004f, y + h * 0.06f), thin)
            }
        }
        2 -> {
            // Hedges down both edges.
            for (side in listOf(-1f, 1f)) {
                var y = from
                while (y < to) {
                    val x = if (side < 0) w * 0.01f else w * 0.99f
                    drawCircle(look.far, w * (0.07f + random.nextFloat() * 0.03f), Offset(x, y))
                    y += w * 0.09f
                }
            }
        }
        3 -> {
            // The far rank of pines down both edges.
            var y = from + w * 0.15f
            while (y < to + w * 0.2f) {
                pine(Offset(w * (0.03f + random.nextFloat() * 0.05f), y), w * 0.26f * (0.8f + random.nextFloat() * 0.4f), look.far)
                pine(Offset(w * (0.92f + random.nextFloat() * 0.05f), y + w * 0.05f), w * 0.26f * (0.8f + random.nextFloat() * 0.4f), look.far)
                y += w * 0.11f
            }
        }
        4 -> {
            // Stars, and the moon over the first open space.
            repeat(80) {
                val c = Offset(w * random.nextFloat(), from + random.nextFloat() * (to - from))
                drawCircle(look.ink.copy(alpha = 0.25f + random.nextFloat() * 0.45f), (0.6f + random.nextFloat() * 1.1f) * density, c)
            }
            spots(stops).firstOrNull()?.let { c ->
                val r = featureSize
                glow(c, r * 1.9f, look.light, 0.25f)
                drawCircle(Color(0xFFFFF0C8), r * 0.6f, c)
                drawCircle(Color(0xFFE8D6A8), r * 0.12f, Offset(c.x - r * 0.2f, c.y - r * 0.1f))
                drawCircle(Color(0xFFE8D6A8), r * 0.08f, Offset(c.x + r * 0.22f, c.y + r * 0.2f))
                drawCircle(Color(0xFFE8D6A8), r * 0.06f, Offset(c.x - r * 0.05f, c.y + r * 0.3f))
            }
        }
        else -> {
            // Stars fading out toward the dawn, and the far hills.
            repeat(45) {
                val y = from + random.nextFloat() * (h * 0.55f - from)
                val fade = (1f - (y - top) / (h * 0.5f)).coerceIn(0f, 1f)
                drawCircle(look.ink.copy(alpha = 0.7f * fade), (0.6f + random.nextFloat()) * density, Offset(w * random.nextFloat(), y))
            }
            hills(look.far, 0.84f, 0.05f, random)
        }
    }
}

/** Scenery close up: it slides a little against the coins. */
internal fun DrawScope.sceneNear(chapter: Int, top: Float, stops: List<Offset>) {
    val look = sceneLook(chapter)
    val random = Random(chapter * 104729)
    val w = size.width
    val h = size.height
    val r = featureSize
    val spots = spots(stops)
    when (chapter) {
        1 -> {
            // Lit windows in every other open space.
            spots.forEachIndexed { i, c -> if (i % 2 == 1) window(look, c, r) }
            // The rail along the foot.
            val railTop = h - w * 0.12f
            drawRect(look.near, Offset(-w * 0.1f, railTop), Size(w * 1.2f, thin * 5))
            var x = -w * 0.1f
            while (x < w * 1.1f) {
                drawRect(look.near, Offset(x, railTop), Size(thin * 4, h - railTop + overscan))
                x += w * 0.06f
            }
            drawRect(look.near, Offset(-w * 0.1f, h - thin * 8), Size(w * 1.2f, thin * 8 + overscan))
        }
        2 -> {
            for (side in listOf(-1f, 1f)) {
                var y = -overscan + w * 0.05f
                while (y < h + overscan) {
                    val x = if (side < 0) w * -0.01f else w * 1.01f
                    drawCircle(look.near, w * (0.05f + random.nextFloat() * 0.02f), Offset(x, y))
                    y += w * 0.09f
                }
            }
            spots.forEachIndexed { i, c -> if (i % 2 == 0) bush(look, c, r, random) else fence(look, c, r, i) }
            // Grass along the foot.
            drawRect(look.near, Offset(-w * 0.1f, h - 2f), Size(w * 1.2f, overscan))
            var x = -w * 0.1f
            while (x < w * 1.1f) {
                val blade = Path().apply { moveTo(x, h); lineTo(x + w * 0.012f, h - w * (0.05f + random.nextFloat() * 0.05f)); lineTo(x + w * 0.024f, h); close() }
                drawPath(blade, look.near)
                x += w * 0.018f
            }
        }
        3 -> {
            var y = -overscan + w * 0.15f
            while (y < h + overscan + w * 0.2f) {
                pine(Offset(w * (0.01f + random.nextFloat() * 0.04f), y + w * 0.07f), w * 0.2f * (0.8f + random.nextFloat() * 0.4f), look.near)
                pine(Offset(w * (0.95f + random.nextFloat() * 0.04f), y + w * 0.11f), w * 0.2f * (0.8f + random.nextFloat() * 0.4f), look.near)
                y += w * 0.13f
            }
            spots.forEachIndexed { i, c ->
                if (i % 2 == 0) {
                    pine(Offset(c.x - r * 0.45f, c.y + r * 0.6f), r * 1.5f, look.far)
                    pine(Offset(c.x + r * 0.4f, c.y + r * 0.7f), r * 1.7f, look.far)
                    pine(Offset(c.x, c.y + r * 0.85f), r * 1.3f, look.near)
                } else {
                    log(look, c, r)
                }
            }
        }
        4 -> {
            spots.drop(1).forEach { block(look, it, r, random) }
            // The town's skyline along the foot.
            val skyline = Path().apply { moveTo(-w * 0.1f, h + overscan) }
            var x = -w * 0.1f
            while (x < w * 1.1f) {
                val roof = h - w * (0.08f + random.nextFloat() * 0.1f)
                val wide = w * (0.08f + random.nextFloat() * 0.07f)
                skyline.lineTo(x, roof); skyline.lineTo(x + wide / 2, roof - w * 0.04f); skyline.lineTo(x + wide, roof)
                x += wide
            }
            skyline.lineTo(w * 1.2f, h + overscan); skyline.close()
            drawPath(skyline, look.near)
        }
        else -> {
            hills(look.near, 0.92f, 0.04f, random)
            spots.lastOrNull()?.let { house(look, it, r) }
        }
    }
}

/** Everything that moves, at [t] seconds. */
internal fun DrawScope.sceneLife(chapter: Int, t: Float, top: Float, stops: List<Offset>) {
    val look = sceneLook(chapter)
    val random = Random(chapter * 31337)
    val w = size.width
    val h = size.height
    val r = featureSize
    val spots = spots(stops)
    when (chapter) {
        1 -> {
            spots.forEachIndexed { i, c ->
                if (i % 2 == 0) {
                    val at = Offset(c.x, c.y - r * 0.2f)
                    dust(look, at, r * 0.42f, t, i.toFloat())
                    lantern(look, at, r * 0.42f, t, i * 1.3f)
                    orbit(look, at, r * 0.95f, 3, t, 0.9f, i * 2f)
                }
            }
        }
        2 -> {
            // At the lowest quality step, half the fireflies, leaves and stars.
            fireflies(look, spots, r, t, random, if (Gpu.quality >= 2) 15 else 30)
            for (k in 0 until 3) {
                // Moths wandering the garden on long, lazy loops.
                val a = t * 0.18f + k * 2.1f
                val c = Offset(w * (0.5f + 0.42f * sin(a * 1.3f + k)), top + (h - top) * (0.5f + 0.45f * sin(a * 0.7f + k * 1.7f)))
                val dx = 0.42f * w * 1.3f * cos(a * 1.3f + k)
                val dy = 0.45f * (h - top) * 0.7f * cos(a * 0.7f + k * 1.7f)
                moth(c, r * 0.32f, t * 16f + k, look.ink, 0.8f, heading(dx, dy))
            }
        }
        3 -> {
            fog(look, t, top)
            leaves(look, t, top, random, if (Gpu.quality >= 2) 8 else 16)
            fireflies(look, emptyList(), r, t, random, if (Gpu.quality >= 2) 4 else 8)
        }
        4 -> {
            // Stars that twinkle, a shooting star now and then, clouds crossing the moon.
            repeat(if (Gpu.quality >= 2) 7 else 14) {
                val c = Offset(w * (0.04f + random.nextFloat() * 0.92f), top + random.nextFloat() * (h - top))
                val p = random.nextFloat() * 6.28f
                val glint = 0.5f + 0.5f * sin(t * (1.5f + random.nextFloat() * 2f) + p)
                twinkle(look, c, (3.5f + 3f * glint) * density, 0.25f + 0.75f * glint)
            }
            val period = 6.5f
            val run = (t % period) / 1.1f
            if (run < 1f) {
                val lap = (t / period).toInt()
                val start = Offset(w * (0.15f + 0.6f * ((lap * 0.37f) % 1f)), top + (h - top) * ((lap * 0.53f) % 0.6f))
                val head = Offset(start.x + w * 0.45f * run, start.y + w * 0.22f * run)
                val tail = Offset(head.x - w * 0.16f, head.y - w * 0.08f)
                val fade = sin(run * PI.toFloat())
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * fade)), tail, head), tail, head, thin * 1.6f, StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = fade), thin * 1.4f, head)
            }
            spots.firstOrNull()?.let { moon ->
                for (k in 0 until 2) {
                    val x = ((t * 9f * density + k * w * 0.8f) % (w * 1.6f)) - w * 0.3f
                    cloud(look, Offset(x, moon.y + r * (0.25f - k * 0.5f)), r * (1f + k * 0.3f))
                }
            }
        }
        else -> {
            spots.forEachIndexed { i, c ->
                when {
                    i == spots.lastIndex -> {
                        // The house's lamp: rays turning slowly, a pulse, and moths circling in.
                        val lamp = Offset(c.x, c.y + r * 0.9f - r * 0.55f)
                        val pulse = 0.5f + 0.5f * sin(t * 2.2f)
                        godRays(lamp, -90f, 210f, 6, r * 2.4f, look.light, 0.22f + 0.08f * pulse, t, seed = 5, motes = false)
                        glow(lamp, r * (1.2f + 0.2f * pulse), look.light, 0.55f + 0.2f * pulse)
                        drawCircle(Color(0xFFFFF4D6), r * 0.08f, lamp)
                        orbit(look, lamp, r * 1.05f, 5, t, 1.1f, 0f)
                    }
                    i % 2 == 0 -> {
                        lantern(look, c, r * 0.42f, t, i * 1.1f)
                        orbit(look, c, r * 0.9f, 4, t, 0.8f, i.toFloat())
                    }
                    else -> {
                        // A swarm drifting on its way to the light.
                        glow(c, r, look.light, 0.16f)
                        for (k in 0 until 6) {
                            val a = t * (0.6f + k * 0.07f) + k * 1.05f
                            val d = r * (0.35f + 0.5f * abs(sin(t * 0.4f + k)))
                            val depth = sin(a)
                            val at = Offset(c.x + d * cos(a), c.y + d * 0.45f * depth)
                            moth(at, r * (0.24f + 0.08f * depth), t * 17f + k, look.ink, 0.5f + 0.3f * depth, heading(-sin(a), 0.45f * cos(a)))
                        }
                    }
                }
            }
        }
    }
}

/** The vignette, the header band and the sheet's frame, over the scenery and under the path. */
internal fun DrawScope.sceneFrame(chapter: Int, top: Float, corner: Float) {
    val look = sceneLook(chapter)
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.45f),
            center = Offset(w / 2f, h / 2f),
            radius = maxOf(w, h) * 0.75f,
        ),
    )
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), 0f, top), size = Size(w, top))
    val inset = 5f * density
    drawRoundRect(
        look.ink.copy(alpha = 0.28f),
        Offset(inset, inset),
        Size(w - inset * 2, h - inset * 2),
        CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(1f * density),
    )
    drawLine(look.ink.copy(alpha = 0.3f), Offset(w * 0.05f, top), Offset(w * 0.95f, top), thin)
}

// ---------------------------------------------------------------------------------------------
// Pieces of scenery

private fun DrawScope.glow(c: Offset, radius: Float, color: Color, alpha: Float = 0.5f) {
    if (radius <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent), c, radius), radius, c)
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

private fun DrawScope.hills(color: Color, y0: Float, amp: Float, random: Random) {
    val w = size.width
    val h = size.height
    val hill = Path().apply {
        moveTo(-w * 0.1f, h + overscan)
        lineTo(-w * 0.1f, h * y0)
        var x = -w * 0.1f
        while (x < w * 1.1f) {
            quadraticTo(x + w * 0.12f, h * (y0 - amp) - random.nextFloat() * h * amp, x + w * 0.25f, h * y0)
            x += w * 0.25f
        }
        lineTo(x, h + overscan)
        close()
    }
    drawPath(hill, color)
}

private fun DrawScope.window(look: SceneLook, c: Offset, r: Float) {
    val ww = r * 1.2f
    val wh = r * 1.4f
    val tl = Offset(c.x - ww / 2, c.y - wh / 2)
    glow(c, r * 1.4f, look.light, 0.25f)
    drawRoundRect(look.near, Offset(tl.x - thin * 3, tl.y - thin * 3), Size(ww + thin * 6, wh + thin * 6), CornerRadius(thin * 2))
    drawRect(Brush.verticalGradient(listOf(look.light.copy(alpha = 0.95f), Dusk.glowDeep.copy(alpha = 0.8f)), tl.y, tl.y + wh), tl, Size(ww, wh))
    // A curtain's shadow in one pane, so it reads as a room.
    drawRect(look.near.copy(alpha = 0.25f), tl, Size(ww * 0.18f, wh))
    drawLine(look.near, Offset(c.x, tl.y), Offset(c.x, tl.y + wh), thin * 2.5f)
    drawLine(look.near, Offset(tl.x, c.y), Offset(tl.x + ww, c.y), thin * 2.5f)
    drawRect(look.far, Offset(tl.x - thin * 6, tl.y + wh + thin * 3), Size(ww + thin * 12, thin * 4))
}

private val PETALS = listOf(Color(0xFFF08AA6), Color(0xFFFFD36B), Color(0xFFB79CFF), Color(0xFFFFFFFF))

private fun DrawScope.bloom(c: Offset, s: Float, color: Color) {
    for (k in 0 until 10) {
        val a = k * 72.0 * PI / 180
        drawCircle(color.copy(alpha = 0.9f), s, Offset(c.x + (s * 1.2f * cos(a)).toFloat(), c.y + (s * 1.2f * sin(a)).toFloat()))
    }
    drawCircle(Dusk.glowDeep, s * 0.7f, c)
}

private fun DrawScope.bush(look: SceneLook, c: Offset, r: Float, random: Random) {
    for ((dx, dy, s) in listOf(Triple(-0.5f, 0.2f, 0.55f), Triple(0.45f, 0.25f, 0.5f), Triple(0f, -0.05f, 0.65f))) {
        drawCircle(look.far, r * s, Offset(c.x + r * dx, c.y + r * dy))
    }
    for ((dx, dy, s) in listOf(Triple(-0.3f, 0.35f, 0.4f), Triple(0.3f, 0.4f, 0.38f))) {
        drawCircle(look.near, r * s, Offset(c.x + r * dx, c.y + r * dy))
    }
    repeat(7) {
        val a = random.nextDouble() * PI * 2
        val d = r * 0.55f * random.nextFloat()
        bloom(Offset(c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat() * 0.8f), r * 0.07f, PETALS[random.nextInt(PETALS.size)])
    }
}

private fun DrawScope.fence(look: SceneLook, c: Offset, r: Float, i: Int) {
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
    for (k in 0 until 5) bloom(Offset(left + k * r * 0.48f + r * 0.1f, base - r * 0.02f), r * 0.07f, PETALS[(k + i) % PETALS.size])
}

private fun DrawScope.log(look: SceneLook, c: Offset, r: Float) {
    drawRoundRect(look.near, Offset(c.x - r, c.y + r * 0.1f), Size(r * 2f, r * 0.42f), CornerRadius(r * 0.2f))
    drawCircle(look.far, r * 0.17f, Offset(c.x + r * 0.85f, c.y + r * 0.31f))
    drawCircle(look.ink.copy(alpha = 0.3f), r * 0.17f, Offset(c.x + r * 0.85f, c.y + r * 0.31f), style = Stroke(thin))
    drawRect(look.ink.copy(alpha = 0.8f), Offset(c.x - r * 0.35f, c.y - r * 0.15f), Size(r * 0.1f, r * 0.27f))
    val cap = Path().apply {
        moveTo(c.x - r * 0.52f, c.y - r * 0.12f)
        quadraticTo(c.x - r * 0.3f, c.y - r * 0.55f, c.x - r * 0.08f, c.y - r * 0.12f)
        close()
    }
    glow(Offset(c.x - r * 0.3f, c.y - r * 0.2f), r * 0.6f, look.light, 0.25f)
    drawPath(cap, look.light)
    drawCircle(look.ink, r * 0.04f, Offset(c.x - r * 0.36f, c.y - r * 0.28f))
    drawCircle(look.ink, r * 0.03f, Offset(c.x - r * 0.22f, c.y - r * 0.22f))
}

private fun DrawScope.block(look: SceneLook, c: Offset, r: Float, random: Random) {
    // Three houses shoulder to shoulder, with chimneys and lit windows.
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
        drawPath(house, look.ink.copy(alpha = 0.18f), style = Stroke(thin))
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

private fun DrawScope.house(look: SceneLook, c: Offset, r: Float) {
    val base = c.y + r * 0.9f
    val house = Path().apply {
        moveTo(c.x - r * 0.8f, base); lineTo(c.x - r * 0.8f, base - r * 0.9f); lineTo(c.x, base - r * 1.5f)
        lineTo(c.x + r * 0.8f, base - r * 0.9f); lineTo(c.x + r * 0.8f, base); close()
    }
    drawPath(house, look.near)
    drawRect(look.light, Offset(c.x - r * 0.18f, base - r * 0.45f), Size(r * 0.36f, r * 0.45f))
    drawRect(look.light.copy(alpha = 0.8f), Offset(c.x - r * 0.62f, base - r * 0.7f), Size(r * 0.24f, r * 0.24f))
    drawRect(look.light.copy(alpha = 0.8f), Offset(c.x + r * 0.38f, base - r * 0.7f), Size(r * 0.24f, r * 0.24f))
}

private fun DrawScope.twinkle(look: SceneLook, c: Offset, s: Float, alpha: Float) {
    glow(c, s * 2.5f, look.ink, 0.3f * alpha)
    drawLine(look.ink.copy(alpha = alpha), Offset(c.x - s, c.y), Offset(c.x + s, c.y), thin, StrokeCap.Round)
    drawLine(look.ink.copy(alpha = alpha), Offset(c.x, c.y - s), Offset(c.x, c.y + s), thin, StrokeCap.Round)
}

private fun DrawScope.cloud(look: SceneLook, c: Offset, s: Float) {
    val tint = look.far.copy(alpha = 0.75f)
    drawOval(tint, Offset(c.x - s, c.y - s * 0.18f), Size(s * 2f, s * 0.36f))
    drawOval(tint, Offset(c.x - s * 0.55f, c.y - s * 0.38f), Size(s * 0.9f, s * 0.42f))
    drawOval(tint, Offset(c.x - s * 0.05f, c.y - s * 0.3f), Size(s * 0.75f, s * 0.36f))
}

// ---------------------------------------------------------------------------------------------
// Things that move

/** The angle, in degrees, a moth heading along (dx, dy) is turned from flying straight up. */
private fun heading(dx: Float, dy: Float) = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f

private val FOREWING = Path().apply {
    // The right forewing, from the body out to a pointed tip and back: unit size, body at the origin.
    moveTo(0.05f, -0.15f)
    cubicTo(0.35f, -0.75f, 0.95f, -0.8f, 1f, -0.45f)
    cubicTo(1.02f, -0.15f, 0.6f, 0.05f, 0.05f, 0.08f)
    close()
}
private val HINDWING = Path().apply {
    moveTo(0.05f, 0.05f)
    cubicTo(0.55f, 0.02f, 0.78f, 0.35f, 0.62f, 0.6f)
    cubicTo(0.45f, 0.82f, 0.15f, 0.6f, 0.05f, 0.35f)
    close()
}

/**
 * A moth, its wings beating with [flap], turned [heading] degrees. The wings narrow toward the body
 * and open again as they beat, so they seem to turn in 3D.
 */
private fun DrawScope.moth(c: Offset, size: Float, flap: Float, color: Color, alpha: Float, heading: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val open = 0.18f + 0.82f * abs(sin(flap))
    val edge = Color(0xFF2A1A12).copy(alpha = 0.75f * a)
    val wing = size * 0.62f
    rotate(heading, c) {
        for (side in listOf(1f, -1f)) {
            translate(c.x, c.y) {
                scale(side * wing * open, wing, Offset.Zero) {
                    drawPath(HINDWING, color.copy(alpha = a * 0.8f))
                    drawPath(FOREWING, color.copy(alpha = a))
                }
            }
            // Outline and an eyespot, drawn at full scale so the lines stay crisp.
            translate(c.x, c.y) {
                scale(side * wing * open, wing, Offset.Zero) {
                    drawPath(FOREWING, edge, style = Stroke(1.6f / wing))
                    drawCircle(Dusk.glowDeep.copy(alpha = a), 0.13f, Offset(0.62f, -0.38f))
                    drawCircle(edge, 0.06f, Offset(0.62f, -0.38f))
                }
            }
        }
        drawOval(Color(0xFF2A1A12).copy(alpha = 0.85f * a), Offset(c.x - size * 0.07f, c.y - size * 0.32f), Size(size * 0.14f, size * 0.7f))
        // Feelers.
        drawLine(edge, Offset(c.x - size * 0.03f, c.y - size * 0.3f), Offset(c.x - size * 0.18f, c.y - size * 0.55f), 1f * density)
        drawLine(edge, Offset(c.x + size * 0.03f, c.y - size * 0.3f), Offset(c.x + size * 0.18f, c.y - size * 0.55f), 1f * density)
    }
}

/** [count] moths circling [c] on a tilted ring: smaller and dimmer behind, bigger in front. */
private fun DrawScope.orbit(look: SceneLook, c: Offset, radius: Float, count: Int, t: Float, speed: Float, phase: Float) {
    for (k in 0 until count) {
        val a = t * speed + phase + k * (2 * PI.toFloat() / count)
        val depth = sin(a)
        val bob = sin(t * 2.6f + k * 1.9f) * radius * 0.08f
        val at = Offset(c.x + cos(a) * radius, c.y + depth * radius * 0.35f + bob)
        moth(at, radius * 0.36f * (0.7f + 0.3f * depth), t * 18f + k * 2f, look.ink, 0.45f + 0.4f * (depth + 1) / 2, heading(-sin(a), 0.35f * cos(a)))
    }
}

/** A lantern swinging on its chain, its glow flickering. */
private fun DrawScope.lantern(look: SceneLook, c: Offset, s: Float, t: Float, phase: Float) {
    val hook = Offset(c.x, c.y - s * 2.4f)
    val flicker = 0.08f * sin(t * 6.3f + phase) + 0.04f * sin(t * 13.7f + phase * 2)
    rotate(sin(t * 1.7f + phase) * 9f, hook) {
        glow(c, s * 2.6f, look.light, 0.42f + flicker)
        drawLine(look.ink.copy(alpha = 0.5f), hook, Offset(c.x, c.y - s * 0.8f), thin)
        glyph(Glyph.Lamp, c, s * 1.6f, look.light, alpha = 0.95f)
    }
}

/** Specks of dust rising through a lantern's light. */
private fun DrawScope.dust(look: SceneLook, c: Offset, s: Float, t: Float, phase: Float) {
    for (m in 0 until 7) {
        val p = (t * 0.11f + m / 7f + phase * 0.13f) % 1f
        val at = Offset(c.x + sin(t * 0.8f + m * 1.7f) * s * 1.8f, c.y + s * 2.2f - p * s * 5f)
        drawCircle(look.light.copy(alpha = 0.7f * sin(p * PI.toFloat())), 1.3f * density, at)
    }
}

/** Fireflies wandering near the edges and the features, each blinking in its own time. */
private fun DrawScope.fireflies(look: SceneLook, spots: List<Offset>, r: Float, t: Float, random: Random, count: Int) {
    val w = size.width
    val h = size.height
    repeat(count) { k ->
        val near = spots.isNotEmpty() && k % 3 == 0
        val base = if (near) {
            val s = spots[k % spots.size]
            Offset(s.x + (random.nextFloat() - 0.5f) * r * 2f, s.y + (random.nextFloat() - 0.5f) * r * 1.6f)
        } else {
            val x = if (random.nextBoolean()) w * (0.03f + random.nextFloat() * 0.15f) else w * (0.82f + random.nextFloat() * 0.15f)
            Offset(x, h * random.nextFloat())
        }
        val p = random.nextFloat() * 6.28f
        val wander = r * 0.35f
        val at = Offset(base.x + sin(t * 0.5f + p) * wander, base.y + cos(t * 0.37f + p * 1.3f) * wander)
        val blink = sin(t * (0.8f + random.nextFloat() * 0.9f) + p).coerceAtLeast(0f)
        val lit = blink * blink
        glow(at, 11f * density, look.light, 0.5f * lit)
        drawCircle(look.light.copy(alpha = 0.25f + 0.75f * lit), 1.8f * density, at)
    }
}

private val LEAF = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(0.7f, -0.3f, 0f, 1f)
    quadraticTo(-0.7f, -0.3f, 0f, -1f)
    close()
}

/** Leaves tumbling down the woods: they drift side to side and turn over as they fall. */
private fun DrawScope.leaves(look: SceneLook, t: Float, top: Float, random: Random, count: Int) {
    val w = size.width
    val h = size.height
    val span = h - top + 40f * density
    repeat(count) {
        val x0 = w * (0.04f + random.nextFloat() * 0.92f)
        val speed = (18f + random.nextFloat() * 22f) * density
        val p = random.nextFloat()
        val y = top - 20f * density + ((t * speed + p * span) % span)
        val x = x0 + sin(t * 1.1f + p * 9f) * w * 0.05f
        val leaf = w * (0.018f + random.nextFloat() * 0.012f)
        val tint = if (it % 3 == 0) Color(0xFFC9A24A) else look.light
        val at = Offset(x, y)
        rotate(t * (40f + p * 60f) * (if (it % 2 == 0) 1 else -1) + p * 360f, at) {
            // Turning over: the leaf narrows to an edge and widens again, as if seen in 3D.
            val turn = 0.15f + 0.85f * abs(cos(t * 2.1f + p * 7f))
            translate(x, y) {
                scale(leaf * turn, leaf, Offset.Zero) { drawPath(LEAF, tint.copy(alpha = 0.85f)) }
            }
        }
    }
}

/** Bands of mist drifting slowly across the woods. */
private fun DrawScope.fog(look: SceneLook, t: Float, top: Float) {
    val w = size.width
    val h = size.height
    var y = top + w * 0.1f
    var k = 0
    while (y < h) {
        val drift = ((t * 7f * density * (1f + k * 0.15f) + k * w * 0.7f) % (w * 1.8f)) - w * 0.9f
        val c = Offset(w / 2f + drift, y)
        drawOval(
            Brush.radialGradient(listOf(look.ink.copy(alpha = 0.13f), Color.Transparent), c, w * 0.6f),
            Offset(c.x - w * 0.6f, c.y - w * 0.09f),
            Size(w * 1.2f, w * 0.18f),
        )
        y += w * 0.42f
        k++
    }
}
