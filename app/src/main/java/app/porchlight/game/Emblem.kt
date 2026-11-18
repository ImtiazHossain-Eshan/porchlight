package app.porchlight.game

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
 * A small porch lamp: a milled metal bezel round a dome of warm glass with the number on it.
 *
 * - Levels 1-4: brass.
 * - 5-9: darker brass with four rivets.
 * - 10-19: silver, and brighter glass.
 * - 20 and up: gold with four rivets, rays behind it, and a shine across the glass every 4 s.
 *
 * [xp] lights the twenty tiny filaments round the bezel one after another, clockwise from the top.
 * For the level-up: [from] and [flip] (0..1) run a split-flap from the old number to [level];
 * [parts] (0..1) lights the glass up from dark and grows the gold rays; [burst] (0..1) throws
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
    val shine = if (tier == EmblemTier.Gilded && !still) {
        val loop = rememberInfiniteTransition(label = "shine")
        loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    } else null
    Canvas(modifier.semantics { contentDescription = "Level $level" }) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.4f
        // On a level-up the glass lights up from dark as the new number lands.
        val glow = 0.35f + 0.65f * parts
        if (tier == EmblemTier.Gilded) rays(c, r * 0.8f, parts)
        bezel(c, r, tier)
        filaments(c, r, xp)
        glass(c, r * 0.74f, tier, glow, shine?.value)
        number(measurer, c, r * 0.86f, level, from, flip)
        if (burst > 0f && burst < 1f) sparks(c, r * 0.9f, burst)
    }
}

/**
 * The lamp's bezel: a milled ring of metal, brass at first, darker riveted brass from level 5,
 * silver from 10, gold from 20, with a groove inside and a shadow under it.
 */
private fun DrawScope.bezel(c: Offset, r: Float, tier: EmblemTier) {
    val metal = when (tier) {
        EmblemTier.Brass -> listOf(Color(0xFFF6D894), Color(0xFFC99A48), Color(0xFF6E4A1C))
        EmblemTier.Riveted -> listOf(Color(0xFFE9B97A), Color(0xFFB0773A), Color(0xFF5A3414))
        EmblemTier.Lit -> listOf(Color(0xFFFFFFFF), Color(0xFFC3CDD6), Color(0xFF55606C))
        EmblemTier.Gilded -> listOf(Color(0xFFFFF2B8), Color(0xFFF0B83C), Color(0xFF8A5A0E))
    }
    drawCircle(Color.Black.copy(alpha = 0.4f), r * 1.02f, Offset(c.x, c.y + r * 0.08f))
    drawCircle(Brush.linearGradient(metal, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
    // Milling round the edge.
    for (k in 0 until 48) {
        val a = k * (2 * Math.PI / 48)
        val o = Offset(c.x + (r * 0.97f * cos(a)).toFloat(), c.y + (r * 0.97f * sin(a)).toFloat())
        val i = Offset(c.x + (r * 0.9f * cos(a)).toFloat(), c.y + (r * 0.9f * sin(a)).toFloat())
        drawLine(metal[2].copy(alpha = 0.45f), i, o, r * 0.025f)
    }
    drawCircle(metal[2], r, c, style = Stroke(r * 0.05f))
    // The groove the glass sits in.
    drawCircle(Brush.linearGradient(listOf(metal[2], metal[0]), Offset(c.x, c.y - r), Offset(c.x, c.y + r)), r * 0.8f, c, style = Stroke(r * 0.06f))
    if (tier == EmblemTier.Riveted || tier == EmblemTier.Gilded) {
        for (k in 0 until 4) {
            val a = Math.PI / 4 + k * Math.PI / 2
            val at = Offset(c.x + (r * 0.87f * cos(a)).toFloat(), c.y + (r * 0.87f * sin(a)).toFloat())
            drawCircle(metal[2], r * 0.06f, at)
            drawCircle(metal[0], r * 0.035f, Offset(at.x - r * 0.012f, at.y - r * 0.012f))
        }
    }
}

/**
 * XP as tiny filaments set round the bezel: they light up amber, one after another, as the level
 * fills; the rest are dark. With no [xp] they are all dark studs.
 */
private fun DrawScope.filaments(c: Offset, r: Float, xp: Float?) {
    val count = 20
    val lit = ((xp ?: 0f) * count)
    for (k in 0 until count) {
        val a = -Math.PI / 2 + k * (2 * Math.PI / count)
        val at = Offset(c.x + (r * 0.87f * cos(a)).toFloat(), c.y + (r * 0.87f * sin(a)).toFloat())
        val on = (lit - k).coerceIn(0f, 1f)
        drawCircle(Color(0xFF1A120A), r * 0.052f, at)
        if (on > 0f) {
            drawCircle(Brush.radialGradient(listOf(Dusk.glow.copy(alpha = 0.7f * on), Color.Transparent), at, r * 0.13f), r * 0.13f, at)
            drawCircle(Color(0xFFFFE6A8).copy(alpha = on), r * 0.04f, at)
        }
    }
}

/** The glass: a dome of warm light, brighter in the middle, with a highlight; on gold a shine crosses it. */
private fun DrawScope.glass(c: Offset, r: Float, tier: EmblemTier, glow: Float, shine: Float?) {
    val warm = if (tier == EmblemTier.Lit) Color(0xFFFFF1CF) else Color(0xFFFFE2A0)
    drawCircle(Brush.radialGradient(listOf(Dusk.glow.copy(alpha = 0.6f * glow), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(Color(0xFF6B4A22), warm, glow), lerp(Color(0xFF4A3216), Color(0xFFF2B456), glow), lerp(Color(0xFF2A1A0C), Color(0xFFB8641E), glow)),
            Offset(c.x, c.y - r * 0.1f),
            r,
        ),
        r,
        c,
    )
    // A curved highlight on the glass, upper left.
    drawArc(Color.White.copy(alpha = 0.45f), 200f, 70f, false, Offset(c.x - r * 0.78f, c.y - r * 0.78f), Size(r * 1.56f, r * 1.56f), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
    if (shine != null) {
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(disc) {
            val p = (shine * 3f).coerceAtMost(1f)
            val x = c.x - r * 1.6f + r * 3.2f * p
            rotate(25f, c) {
                drawRect(Color.White.copy(alpha = 0.45f), Offset(x - r * 0.14f, c.y - r * 1.5f), Size(r * 0.28f, r * 3f))
            }
        }
    }
}




/** Level 20 and up: short gold rays all round, behind the wings, so gold reads even when small. */
private fun DrawScope.rays(c: Offset, r: Float, amount: Float) {
    if (amount <= 0f) return
    for (i in 0 until 16) {
        val a = Math.toRadians(i * 22.5)
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
        val a = Math.toRadians(i * 30.0 + 8.0)
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
            val a = i * Math.PI / scallops
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
