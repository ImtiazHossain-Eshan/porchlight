package app.porchlight.game

import kotlin.math.PI
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.cos
import kotlin.math.sin

/** The level emblem grows with the player, in four looks. */
internal enum class EmblemTier { Brass, Riveted, Lit, Gilded }

internal fun tierFor(level: Int): EmblemTier = when {
    level >= 20 -> EmblemTier.Gilded
    level >= 10 -> EmblemTier.Lit
    level >= 5 -> EmblemTier.Riveted
    else -> EmblemTier.Brass
}

private val BRASS_FACE = listOf(Color(0xFFF6D894), Color(0xFFC99A48), Color(0xFF7A5320))
private val SILVER_GOLD = listOf(Color(0xFFFFFFFF), Color(0xFFC3CDD6), Color(0xFF5E6874))
private val GOLD_FACE = listOf(Color(0xFFFFF2B8), Color(0xFFF0B83C), Color(0xFF9A6410))
private val ENGRAVE_DARK = Color(0xFF3E2810)
private val WING_BRASS = listOf(Color(0xFFE9C47A), Color(0xFF9C6D2C))
private val WING_GOLD = listOf(Color(0xFFFFE9A6), Color(0xFFE2A230))

/**
 * The player's level, drawn in brass. One composable for every place a level shows: the top bar,
 * Results, the level-up, the share card, the Moths and Nights screens.
 *
 * A moth medallion: a metal moth with its wings spread and the number on a disc over its body.
 *
 * - Levels 1-4: bronze.
 * - 5-9: brass, with an eye-spot on each forewing.
 * - 10-19: gold, with swallow tails on the hindwings.
 * - 20 and up: bright gold, with rays behind it and a shine across the disc every 4 s.
 *
 * [xp] is warm light filling the wings outward from the body. Every few seconds it gives one soft
 * wingbeat (not when [still]).
 * For the level-up: [from] and [flip] (0..1) run a split-flap from the old number to [level];
 * [parts] (0..1) spreads the wings open from folded and grows the gold rays; [burst] (0..1) throws
 * sparks out. [still] stops the shine (reduce motion, or a still image).
 */
@Composable
internal fun Emblem(
    level: Int,
    modifier: Modifier = Modifier,
    xp: Float? = null,
    from: Int = level,
    flip: Float = 1f,
    parts: Float = 1f,
    burst: Float = 0f,
    still: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val tier = tierFor(level)
    val loop = if (!still) rememberInfiniteTransition(label = "emblem") else null
    val shine = loop?.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    // Every few seconds the moth gives one soft wingbeat.
    val beat = loop?.animateFloat(0f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing), RepeatMode.Restart), label = "beat")
    Canvas(modifier.semantics { contentDescription = "Level $level" }) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val s = size.minDimension / 2f
        val b = beat?.value ?: 1f
        val flap = if (b < 0.18f) 1f - 0.22f * sin(b / 0.18f * PI.toFloat()) else 1f
        // On a level-up the wings spread open from folded as the new number lands.
        val spread = (0.35f + 0.65f * parts) * flap
        if (tier == EmblemTier.Gilded) rays(c, s * 0.62f, parts)
        mothWings(c, s, tier, xp ?: 1f, spread)
        medallion(c, s * 0.42f, tier, if (tier == EmblemTier.Gilded) shine?.value else null)
        number(measurer, c, s * 0.44f, level, from, flip)
        if (burst > 0f && burst < 1f) sparks(c, s * 0.5f, burst)
    }
}

/** The metals, from the first levels to the last. */
private fun metalFor(tier: EmblemTier) = when (tier) {
    EmblemTier.Brass -> listOf(Color(0xFFE7B97C), Color(0xFFB07A3A), Color(0xFF5E3816))
    EmblemTier.Riveted -> listOf(Color(0xFFF6D894), Color(0xFFC99A48), Color(0xFF6E4A1C))
    EmblemTier.Lit -> listOf(Color(0xFFFFEFC2), Color(0xFFE0B04E), Color(0xFF7A5216))
    EmblemTier.Gilded -> listOf(Color(0xFFFFF2B8), Color(0xFFF0B83C), Color(0xFF8A5A0E))
}

/** One side's forewing and hindwing, in units of the emblem's half-width, the body at the origin. */
private fun wingPath(c: Offset, s: Float, side: Float, spread: Float, tails: Boolean): Path {
    fun p(x: Float, y: Float) = Offset(c.x + side * x * s * spread, c.y + y * s)
    return Path().apply {
        // Forewing: up and out to a rounded tip.
        val f0 = p(0.12f, -0.08f)
        moveTo(f0.x, f0.y)
        p(0.35f, -0.62f).let { k1 -> p(0.92f, -0.82f).let { k2 -> p(0.97f, -0.42f).let { e -> cubicTo(k1.x, k1.y, k2.x, k2.y, e.x, e.y) } } }
        p(1.0f, -0.12f).let { k1 -> p(0.62f, 0.02f).let { k2 -> p(0.12f, 0.04f).let { e -> cubicTo(k1.x, k1.y, k2.x, k2.y, e.x, e.y) } } }
        close()
        // Hindwing: down and out, rounder; with a swallow tail on silver and gold.
        val h0 = p(0.12f, 0.06f)
        moveTo(h0.x, h0.y)
        p(0.58f, 0.04f).let { k1 -> p(0.86f, 0.36f).let { k2 -> p(0.66f, 0.6f).let { e -> cubicTo(k1.x, k1.y, k2.x, k2.y, e.x, e.y) } } }
        if (tails) {
            p(0.6f, 0.72f).let { k -> p(0.52f, 0.96f).let { e -> quadraticTo(k.x, k.y, e.x, e.y) } }
            p(0.42f, 0.76f).let { k -> p(0.3f, 0.66f).let { e -> quadraticTo(k.x, k.y, e.x, e.y) } }
        } else {
            p(0.48f, 0.82f).let { k -> p(0.28f, 0.66f).let { e -> quadraticTo(k.x, k.y, e.x, e.y) } }
        }
        p(0.18f, 0.5f).let { k -> p(0.12f, 0.06f).let { e -> quadraticTo(k.x, k.y, e.x, e.y) } }
        close()
    }
}

/**
 * The wings: metal, with the XP as warm light filling them outward from the body ([fill] 0..1),
 * dark veins, eye-spots from level 5, and swallow tails from level 10.
 */
private fun DrawScope.mothWings(c: Offset, s: Float, tier: EmblemTier, fill: Float, spread: Float) {
    val metal = metalFor(tier)
    val tails = tier >= EmblemTier.Lit
    val both = Path().apply {
        addPath(wingPath(c, s, 1f, spread, tails))
        addPath(wingPath(c, s, -1f, spread, tails))
    }
    // A soft shadow under the wings.
    translate(0f, s * 0.06f) { drawPath(both, Color.Black.copy(alpha = 0.35f)) }
    // Dark metal where the light has not reached yet.
    drawPath(both, Brush.linearGradient(listOf(lerp(metal[1], metal[2], 0.55f), lerp(metal[2], Color.Black, 0.25f)), Offset(c.x, c.y - s), Offset(c.x, c.y + s)))
    // The light, spreading out from the body as the level fills.
    if (fill > 0f) {
        clipPath(both) {
            // The light starts at the medallion's edge, so even a little XP shows on the wings.
            val reach = s * (0.42f + 0.7f * fill.coerceIn(0f, 1f))
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFFFF4D6),
                    0.45f to metal[0],
                    0.85f to metal[1],
                    1f to metal[1].copy(alpha = 0.85f),
                    center = c,
                    radius = reach.coerceAtLeast(1f),
                ),
                reach,
                c,
            )
            // The leading edge of the light glows.
            drawCircle(Color(0xFFFFE6A8).copy(alpha = 0.6f), reach, c, style = Stroke(s * 0.05f))
        }
    }
    // Veins from the body to the wing edges.
    for (side in listOf(1f, -1f)) {
        for ((x, y) in listOf(0.85f to -0.6f, 0.95f to -0.3f, 0.7f to 0.45f, 0.5f to 0.7f)) {
            drawLine(
                metal[2].copy(alpha = 0.6f),
                Offset(c.x + side * 0.16f * s * spread, c.y),
                Offset(c.x + side * x * s * spread * 0.92f, c.y + y * s * 0.92f),
                s * 0.025f,
                StrokeCap.Round,
            )
        }
        if (tier >= EmblemTier.Riveted) {
            // An eye-spot on each forewing.
            val eye = Offset(c.x + side * 0.66f * s * spread, c.y - 0.42f * s)
            drawCircle(metal[2], s * 0.1f, eye)
            drawCircle(Color(0xFFE38A2A), s * 0.075f, eye)
            drawCircle(metal[2], s * 0.04f, eye)
        }
    }
    drawPath(both, metal[2], style = Stroke(s * 0.045f, join = StrokeJoin.Round))
    // Antennae, curling out from above the medallion.
    for (side in listOf(1f, -1f)) {
        val path = Path().apply {
            moveTo(c.x + side * s * 0.08f, c.y - s * 0.38f)
            quadraticTo(c.x + side * s * 0.16f, c.y - s * 0.78f, c.x + side * s * 0.36f, c.y - s * 0.84f)
        }
        drawPath(path, metal[2], style = Stroke(s * 0.04f, cap = StrokeCap.Round))
        drawCircle(metal[1], s * 0.045f, Offset(c.x + side * s * 0.36f, c.y - s * 0.84f))
    }
}

/** The medallion over the moth's body: a struck disc with a rim, on which the number sits. */
private fun DrawScope.medallion(c: Offset, r: Float, tier: EmblemTier, shine: Float?) {
    val metal = metalFor(tier)
    drawCircle(Color.Black.copy(alpha = 0.4f), r * 1.04f, Offset(c.x, c.y + r * 0.1f))
    drawCircle(Brush.linearGradient(listOf(metal[0], metal[1], metal[2]), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
    drawCircle(Brush.radialGradient(listOf(metal[0], metal[1]), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.3f), r * 0.82f, c)
    drawCircle(metal[2], r * 0.82f, c, style = Stroke(r * 0.06f))
    drawCircle(metal[2], r, c, style = Stroke(r * 0.07f))
    if (shine != null) {
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r * 0.82f)) }
        clipPath(disc) {
            val p = (shine * 3f).coerceAtMost(1f)
            val x = c.x - r * 1.6f + r * 3.2f * p
            rotate(25f, c) {
                drawRect(Color.White.copy(alpha = 0.45f), Offset(x - r * 0.12f, c.y - r * 1.5f), Size(r * 0.24f, r * 3f))
            }
        }
    }
}




/** Level 20 and up: short gold rays all round, behind the wings, so gold reads even when small. */
private fun DrawScope.rays(c: Offset, r: Float, amount: Float) {
    if (amount <= 0f) return
    for (i in 0 until 16) {
        val a = toRadians(i * 22.5)
        val inner = r * 1.3f
        val outer = r * (if (i % 2 == 0) 1.95f else 1.7f) * amount
        drawLine(
            Craft.gold,
            Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
            Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
            r * 0.07f,
            StrokeCap.Round,
            alpha = 0.75f,
        )
    }
}



/**
 * The number, engraved: a dark cut above and a light edge below, no drop shadow. During a
 * level-up the old number's top half folds down onto the new one, split-flap style.
 */
private fun DrawScope.number(measurer: TextMeasurer, c: Offset, r: Float, level: Int, from: Int, flip: Float) {
    val fontSize = (r * (if (level >= 100) 0.62f else 0.82f)).toSp()
    fun layout(n: Int) = measurer.measure("$n", Display.copy(fontSize = fontSize))
    val centreY = c.y + r * 0.04f
    fun draw(n: Int) {
        val text = layout(n)
        val at = Offset(c.x - text.size.width / 2f, centreY - text.size.height / 2f)
        drawText(text, color = ENGRAVE_DARK.copy(alpha = 0.9f), topLeft = Offset(at.x, at.y - (1f * density)))
        drawText(text, color = Color.White.copy(alpha = 0.55f), topLeft = Offset(at.x, at.y + (1f * density)))
        drawText(text, color = Dusk.paperInk, topLeft = at)
    }
    if (flip >= 1f || from == level) {
        draw(level)
        return
    }
    val hinge = centreY
    // Top: the new number's top half waits behind; the old one's top half falls over the hinge.
    clipRect(top = 0f, bottom = hinge) {
        draw(level)
        if (flip < 0.5f) scale(1f, 1f - flip * 2f, Offset(c.x, hinge)) { draw(from) }
    }
    // Bottom: the old bottom half shows until the new one swings down over it.
    clipRect(top = hinge, bottom = size.height) {
        draw(from)
        if (flip >= 0.5f) scale(1f, (flip - 0.5f) * 2f, Offset(c.x, hinge)) { draw(level) }
    }
    drawLine(ENGRAVE_DARK, Offset(c.x - r * 0.5f, hinge), Offset(c.x + r * 0.5f, hinge), r * 0.03f, alpha = 0.5f)
}

/** Twelve sparks flung out from the emblem as [t] runs 0 to 1. */
private fun DrawScope.sparks(c: Offset, r: Float, t: Float) {
    for (i in 0 until 12) {
        val a = toRadians(i * 30.0 + 8.0)
        val d = r * (1.1f + 1.4f * t)
        val at = Offset(c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat())
        drawCircle(Dusk.glow, r * 0.09f * (1f - t), at, alpha = 1f - t)
    }
}


// Badges

/** Each badge's own mark. No two badges share one. */
internal fun Badge.glyph(): Glyph = when (this) {
    Badge.FirstLight -> Glyph.Bulb
    Badge.CleanSweep -> Glyph.Check
    Badge.QuickWings -> Glyph.Clock
    Badge.Overdrive -> Glyph.Flame
    Badge.Melody -> Glyph.Note
    Badge.NightShift -> Glyph.Moon
    Badge.FullRoster -> Glyph.Roster
    Badge.Wanderers -> Glyph.Swap
    Badge.LastLight -> Glyph.Trophy
    Badge.Regular -> Glyph.Calendar
    Badge.GoldenWing -> Glyph.Wing
    Badge.GildedPorch -> Glyph.Crown
    Badge.Chapter1 -> Glyph.Porch
    Badge.Chapter2 -> Glyph.Flower
    Badge.Chapter3 -> Glyph.Pine
    Badge.Chapter4 -> Glyph.Roofs
    Badge.Chapter5 -> Glyph.Lamp
}

private val BRONZE = listOf(Color(0xFFF0B07A), Color(0xFFB8673A), Color(0xFF5C2E15))
private val SILVER = listOf(Color(0xFFFFFFFF), Color(0xFFB9C6D2), Color(0xFF55606C))
private val GOLD = listOf(Color(0xFFFFF0B0), Color(0xFFF0B83C), Color(0xFF8C5E12))

/** The colour of a badge's tier, for its ribbon and its words. */
internal fun Metal.tint(): Color = when (this) {
    Metal.Bronze -> Craft.copper
    Metal.Silver -> Color(0xFFB9C6D2)
    Metal.Gold -> Craft.gold
}

/**
 * A badge medal, drawn like the emblem: a scalloped rim, a sunken face, the badge's own mark.
 * Bronze is warm copper, silver is cool with a bright shine line, gold is warm with a glow.
 * Not yet earned it is a dark outline only, so it can never be mistaken for silver.
 * [shine] (0..1) sweeps a band of light across, as when it is first earned.
 */
@Composable
internal fun BadgeMedal(badge: Badge, earned: Boolean, modifier: Modifier = Modifier, shine: Float = -1f) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - (2f * density)
        val rim = Path()
        val scallops = 16
        for (i in 0..scallops * 2) {
            val a = i * PI / scallops
            val rr = if (i % 2 == 0) r else r * 0.92f
            val x = c.x + (rr * cos(a)).toFloat()
            val y = c.y + (rr * sin(a)).toFloat()
            if (i == 0) rim.moveTo(x, y) else rim.lineTo(x, y)
        }
        rim.close()
        val line = Stroke(1.6f * density, join = StrokeJoin.Round)
        if (!earned) {
            // Locked: an outline of the medal and its mark, nothing filled.
            drawPath(rim, Dusk.locked, alpha = 0.7f, style = line)
            drawCircle(Dusk.locked, r * 0.72f, c, alpha = 0.45f, style = line)
            glyph(badge.glyph(), c, r * 0.9f, Dusk.locked, alpha = 0.6f)
            return@Canvas
        }
        val metal = when (badge.metal) {
            Metal.Bronze -> BRONZE
            Metal.Silver -> SILVER
            Metal.Gold -> GOLD
        }
        if (badge.metal == Metal.Gold) {
            drawCircle(Brush.radialGradient(listOf(Craft.gold.copy(alpha = 0.5f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
        }
        val across = Offset(size.width, size.height)
        drawPath(rim, Brush.linearGradient(listOf(metal[0], metal[1], metal[2]), Offset.Zero, across))
        drawPath(rim, metal[2], style = line)
        drawCircle(Brush.linearGradient(listOf(metal[2], metal[1]), Offset.Zero, across), r * 0.74f, c)
        drawCircle(metal[0], r * 0.74f, c, alpha = 0.6f, style = Stroke(density))
        // The mark: a dark cut with a light edge below it.
        val w = r * 0.9f
        glyph(badge.glyph(), Offset(c.x, c.y + density), w, metal[0], alpha = 0.7f)
        glyph(badge.glyph(), c, w, metal[2])
        if (badge.metal == Metal.Silver) {
            // Silver's bright shine line, so it reads as polished, never as locked.
            clipPath(rim) {
                rotate(-35f, c) {
                    drawRect(Color.White.copy(alpha = 0.75f), Offset(c.x - r * 0.55f, c.y - r * 1.2f), Size(r * 0.1f, r * 2.4f))
                }
            }
        }
        if (shine in 0f..1f) {
            clipPath(rim) {
                rotate(25f, c) {
                    val x = c.x - r * 1.5f + r * 3f * shine
                    drawRect(Color.White.copy(alpha = 0.6f), Offset(x - r * 0.15f, c.y - r * 1.5f), Size(r * 0.3f, r * 3f))
                }
            }
        }
    }
}

/** The tier ribbon under an earned badge, unfurling from the middle as [open] runs 0 to 1. */
@Composable
internal fun TierRibbon(metal: Metal, modifier: Modifier = Modifier, open: Float = 1f) {
    Canvas(modifier) {
        val w = size.width * open.coerceIn(0f, 1f)
        if (w <= 0f) return@Canvas
        val h = size.height
        val left = (size.width - w) / 2f
        val tint = metal.tint()
        val band = Path().apply {
            moveTo(left, 0f)
            lineTo(left + w, 0f)
            lineTo(left + w - h * 0.4f, h / 2f)
            lineTo(left + w, h)
            lineTo(left, h)
            lineTo(left + h * 0.4f, h / 2f)
            close()
        }
        drawPath(band, Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.7f))))
        drawPath(band, Color.Black.copy(alpha = 0.35f), style = Stroke(density))
    }
}
