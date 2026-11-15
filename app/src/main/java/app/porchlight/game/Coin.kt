package app.porchlight.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Brass, and the dull iron of anything locked or off. */
private val BRASS = listOf(Color(0xFFFFE9A8), Color(0xFFE0B65C), Color(0xFFB8873B), Color(0xFF6E4A1A), Color(0xFF3E2810))
private val IRON = listOf(Color(0xFF8A9098), Color(0xFF5C636B), Color(0xFF3A3F45), Color(0xFF22262B), Color(0xFF111316))
private val GOLDEN = listOf(Color(0xFFFFF4C2), Color(0xFFFFD35A), Color(0xFFE2A230), Color(0xFF8C5E12), Color(0xFF4A3008))

/**
 * A thick metal coin seen from slightly above, so it reads as a solid object, not a flat disc:
 * a soft shadow on the surface below, the coin's edge showing under its face, a bevelled rim lit
 * from the top left, a sunken face, and a highlight across the top.
 *
 * [r] is the face's radius. [press] (0..1) pushes the coin down onto its edge, as a button does
 * under a finger. [dull] is iron (locked, off); [gold] is bright gold (chosen).
 */
internal fun DrawScope.coin(c: Offset, r: Float, press: Float = 0f, dull: Boolean = false, gold: Boolean = false) {
    val metal = when {
        dull -> IRON
        gold -> GOLDEN
        else -> BRASS
    }
    val depth = r * 0.16f * (1f - 0.7f * press)
    val face = Offset(c.x, c.y - depth / 2f + r * 0.16f * 0.35f * press)

    // The shadow it casts on the surface, wider when the coin stands proud.
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), Offset(c.x, c.y + depth), r * 1.2f),
        topLeft = Offset(c.x - r * 1.15f, c.y + depth - r * 0.85f),
        size = Size(r * 2.3f, r * 2f),
    )
    // The edge: the coin's thickness, showing below the face, milled with fine lines.
    val edgeCentre = Offset(face.x, face.y + depth)
    drawCircle(Brush.verticalGradient(listOf(metal[3], metal[4]), edgeCentre.y - r, edgeCentre.y + r), r, edgeCentre)
    for (i in -6..6) {
        val x = face.x + r * i / 7f
        val y = edgeCentre.y + kotlin.math.sqrt((r * r - (x - face.x) * (x - face.x)).coerceAtLeast(0f))
        drawLine(metal[4], Offset(x, y - depth * 0.9f), Offset(x, y - depth * 0.15f), r * 0.025f, alpha = 0.6f)
    }
    // The rim, lit from the top left.
    drawCircle(Brush.linearGradient(listOf(metal[0], metal[2], metal[3]), Offset(face.x - r, face.y - r), Offset(face.x + r, face.y + r)), r, face)
    // The face, sunk inside the rim: its bevel catches light at the bottom right instead.
    val inner = r * 0.8f
    drawCircle(Brush.linearGradient(listOf(metal[3], metal[1]), Offset(face.x - inner, face.y - inner), Offset(face.x + inner, face.y + inner)), inner, face)
    drawCircle(
        Brush.radialGradient(listOf(metal[1], metal[2]), Offset(face.x - inner * 0.35f, face.y - inner * 0.4f), inner * 1.3f),
        inner * 0.9f,
        face,
    )
    // A highlight across the upper left of the rim, and a dark line where rim meets face.
    drawArc(
        Color.White,
        startAngle = 200f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(face.x - r * 0.9f, face.y - r * 0.9f),
        size = Size(r * 1.8f, r * 1.8f),
        alpha = if (dull) 0.25f else 0.6f,
        style = Stroke(r * 0.07f, cap = StrokeCap.Round),
    )
    drawCircle(metal[4], inner * 0.9f, face, alpha = 0.35f, style = Stroke(r * 0.03f))
    drawCircle(metal[4], r, face, alpha = 0.6f, style = Stroke(r * 0.04f))
}

/** Where a coin's face sits for a box of [size]: centred, with room for its edge and shadow below. */
internal fun coinFace(size: Size): Pair<Offset, Float> {
    val r = minOf(size.width, size.height) * 0.42f
    return Offset(size.width / 2f, size.height / 2f - r * 0.06f) to r
}

/** The face's bounds, for laying text or a glyph on it. */
internal fun coinRect(c: Offset, r: Float) = Rect(c, r * 0.8f)
