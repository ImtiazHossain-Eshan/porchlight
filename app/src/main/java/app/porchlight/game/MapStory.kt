package app.porchlight.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The story the level map tells: Luna's way home to the last light.
//
// Read down each sheet: there is one moment of the story beside every coin that sits off to the side
// of the path, and Luna herself flies the path from the first coin to the last.
//   I   The porch: a cocoon splits, Luna dries her wings on the lamp, the cat in the window watches,
//       the screen door stands open, and through a knothole one far light calls.
//   II  The garden: a moonflower opens, a spider waits in its web, the moon swims in the pond, a
//       snail carries a firefly, and the fireflies stream out through the gate.
//   III The woods: the owl's eyes, a ring of glowing mushrooms, a log over the stream, a sleeping
//       fox, and the first lights of the town beyond the trees.
//   IV  The rooftops: the moon, the clock tower, a cat by the smoking chimney, washing on the line,
//       and a candle in an attic window, where Luna rests.
//   V   The last porch: a windmill at dawn, the wanderers gathering, the gate and its letterbox,
//       more moths coming in, and the porch light left on for them.
// Everything here is drawn every frame from the clock; under Reduce motion the clock stands still.

/** Luna: a pale green luna moth, the one the story follows. */
private val LUNA = Color(0xFFC4F2CF)

/** One line of the story for each chapter, shown under its name. */
internal fun chapterStory(chapter: Int): String = when (chapter) {
    1 -> "Luna wakes under the porch lamp."
    2 -> "Fireflies lead her through the garden."
    3 -> "Into the dark woods, past the owl."
    4 -> "Over the sleeping town, under the lights."
    else -> "At dawn, a porch light left on."
}

/** Every story moment of [chapter], then Luna flying down the path. */
internal fun DrawScope.story(chapter: Int, t: Float, stops: List<Offset>) {
    val look = sceneLook(chapter)
    val r = featureSize
    val spots = spots(stops)
    spots.forEachIndexed { i, c -> storyMoment(chapter, i, i == spots.lastIndex, c, r, t, look) }
    lunaFlight(stops, t, r)
}

private fun DrawScope.storyMoment(chapter: Int, i: Int, last: Boolean, c: Offset, r: Float, t: Float, look: SceneLook) {
    when (chapter) {
        1 -> when (i) {
            0 -> cocoon(look, c, r, t)
            1 -> lampPerch(look, c, r, t)
            2 -> windowCat(look, c, r, t)
            3 -> screenDoor(look, c, r, t)
            else -> knothole(look, c, r, t)
        }
        2 -> when (i) {
            0 -> moonflower(look, c, r, t)
            1 -> web(look, c, r, t)
            2 -> pond(look, c, r, t)
            3 -> snail(look, c, r, t)
            else -> gate(look, c, r, t)
        }
        3 -> when (i) {
            0 -> owl(look, c, r, t)
            1 -> mushroomRing(c, r, t)
            2 -> stream(look, c, r, t)
            3 -> fox(look, c, r, t)
            else -> woodsEdge(look, c, r, t)
        }
        4 -> when (i) {
            0 -> Unit // the moon, drawn in the far layer
            1 -> clockTower(look, c, r, t)
            2 -> chimneyCat(look, c, r, t)
            3 -> washingLine(look, c, r, t)
            else -> attic(look, c, r, t)
        }
        else -> when {
            last -> lastPorch(look, c, r, t)
            i == 0 -> windmill(look, c, r, t)
            i == 2 -> letterbox(look, c, r, t)
            else -> gathering(look, c, r, t, i)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Luna's flight

/** A point [f] of the way along the path's curve from [a] to [b], and the way it is heading there. */
private fun along(a: Offset, b: Offset, f: Float): Pair<Offset, Offset> {
    val p1 = Offset(a.x, a.y + (b.y - a.y) * 0.7f)
    val p2 = Offset(b.x, b.y - (b.y - a.y) * 0.7f)
    val g = 1f - f
    val at = a * (g * g * g) + p1 * (3 * g * g * f) + p2 * (3 * g * f * f) + b * (f * f * f)
    val d = (p1 - a) * (3 * g * g) + (p2 - p1) * (6 * g * f) + (b - p2) * (3 * f * f)
    return at to d
}

/**
 * Luna flying the path coin to coin, top to bottom, weaving a little and trailing light. She fades
 * in at the first coin and out at the last, then sets off again.
 */
private fun DrawScope.lunaFlight(stops: List<Offset>, t: Float, r: Float) {
    if (stops.size < 2) return
    val n = stops.size - 1
    val period = 5.5f * n
    fun at(time: Float): Pair<Offset, Offset> {
        val u = (((time % period) + period) % period) / period * n
        val i = u.toInt().coerceAtMost(n - 1)
        val (p, d) = along(stops[i], stops[i + 1], u - i)
        val weave = Offset(sin(time * 1.3f) * r * 0.75f, -r * 0.55f + sin(time * 2.1f) * r * 0.12f)
        return (p + weave) to Offset(d.x / period * n + cos(time * 1.3f) * r * 0.75f * 1.3f, d.y / period * n)
    }
    val u = (t % period) / period * n
    val fade = (minOf(u, n - u) / 0.35f).coerceIn(0f, 1f)
    if (fade <= 0f) return
    for (k in 1..9) {
        val (p, _) = at(t - k * 0.09f)
        drawCircle(LUNA.copy(alpha = 0.32f * fade * (1f - k / 10f)), (2.2f - k * 0.16f) * density, p)
    }
    val (p, d) = at(t)
    glow(p, r * 0.75f, LUNA, 0.32f * fade)
    moth(p, r * 0.46f, t * 13f, LUNA, fade, heading(d.x, d.y))
}

// ---------------------------------------------------------------------------------------------
// I. The porch

private fun DrawScope.cocoon(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A beam, a silk thread, and the cocoon swinging, split open with light.
    val beamY = c.y - r * 0.8f
    drawRoundRect(look.near, Offset(c.x - r * 0.45f, beamY - r * 0.1f), Size(r * 0.9f, r * 0.16f), CornerRadius(r * 0.05f))
    drawLine(look.ink.copy(alpha = 0.15f), Offset(c.x - r * 0.45f, beamY - r * 0.1f), Offset(c.x + r * 0.45f, beamY - r * 0.1f), thin)
    val hook = Offset(c.x, beamY + r * 0.08f)
    rotate(sin(t * 1.1f) * 5f, hook) {
        drawLine(look.ink.copy(alpha = 0.6f), hook, Offset(c.x, c.y - r * 0.42f), thin)
        val pulse = 0.5f + 0.5f * sin(t * 2f)
        glow(Offset(c.x, c.y), r * 0.75f, look.light, 0.25f + 0.2f * pulse)
        drawOval(Color(0xFFC9AE7E), Offset(c.x - r * 0.2f, c.y - r * 0.45f), Size(r * 0.4f, r * 0.9f))
        for (k in 1..4) {
            val y = c.y - r * 0.45f + r * 0.9f * k / 5f
            drawLine(Color(0xFF8C6E45), Offset(c.x - r * 0.17f, y), Offset(c.x + r * 0.17f, y + r * 0.04f), thin)
        }
        val crack = Path().apply {
            moveTo(c.x - r * 0.02f, c.y - r * 0.3f); lineTo(c.x + r * 0.08f, c.y - r * 0.12f)
            lineTo(c.x - r * 0.04f, c.y + r * 0.02f); lineTo(c.x + r * 0.06f, c.y + r * 0.2f)
        }
        drawPath(crack, Color(0xFFFFF0C2), style = Stroke(thin * 2.2f, cap = StrokeCap.Round))
        // A wing pushing out of the split, unfolding and folding again.
        val unfold = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 0.7f))
        translate(c.x + r * 0.08f, c.y - r * 0.12f) {
            rotate(-30f, Offset.Zero) {
                scale(r * 0.42f * unfold, r * 0.42f, Offset.Zero) { drawPath(FOREWING, LUNA.copy(alpha = 0.9f)) }
            }
        }
    }
}

private fun DrawScope.lampPerch(look: SceneLook, c: Offset, r: Float, t: Float) {
    // The porch lamp on its bracket, moths round it, and Luna on top, trying her new wings.
    val lamp = Offset(c.x, c.y + r * 0.1f)
    drawRect(look.near, Offset(c.x + r * 0.3f, c.y - r * 0.55f), Size(r * 0.14f, r * 0.9f))
    drawLine(look.near, Offset(c.x + r * 0.35f, c.y - r * 0.4f), Offset(c.x, c.y - r * 0.4f), thin * 3f)
    val flicker = 0.06f * sin(t * 6.3f) + 0.03f * sin(t * 13.7f)
    glow(lamp, r * 1.5f, look.light, 0.45f + flicker)
    glyph(Glyph.Lamp, lamp, r * 0.75f, look.light)
    orbit(look, lamp, r * 0.95f, 3, t, 0.9f, 0f)
    moth(Offset(lamp.x, lamp.y - r * 0.48f), r * 0.5f, t * 0.8f, LUNA, 1f, 0f)
}

private fun DrawScope.windowCat(look: SceneLook, c: Offset, r: Float, t: Float) {
    window(look, c, r)
    // The cat on the sill inside, watching: its tail sways, and now and then it blinks.
    val sill = c.y + r * 0.7f
    val body = Offset(c.x + r * 0.15f, sill - r * 0.2f)
    drawOval(look.near, Offset(body.x - r * 0.24f, body.y - r * 0.2f), Size(r * 0.48f, r * 0.42f))
    val head = Offset(body.x - r * 0.05f, body.y - r * 0.32f)
    drawCircle(look.near, r * 0.16f, head)
    for (side in listOf(-1f, 1f)) {
        drawPath(triangle(Offset(head.x + side * r * 0.15f, head.y - r * 0.04f), Offset(head.x + side * r * 0.12f, head.y - r * 0.26f), Offset(head.x + side * r * 0.02f, head.y - r * 0.12f)), look.near)
    }
    val tail = Path().apply {
        moveTo(body.x + r * 0.2f, body.y + r * 0.1f)
        quadraticTo(body.x + r * (0.55f + 0.12f * sin(t * 1.4f)), body.y - r * 0.1f, body.x + r * (0.42f + 0.18f * sin(t * 1.4f + 0.8f)), body.y - r * 0.42f)
    }
    drawPath(tail, look.near, style = Stroke(r * 0.08f, cap = StrokeCap.Round))
    val blink = if ((t % 4.5f) < 0.15f) 0.15f else 1f
    for (side in listOf(-1f, 1f)) {
        drawOval(Color(0xFFD6F07A), Offset(head.x + side * r * 0.065f - r * 0.03f, head.y - r * 0.03f * blink), Size(r * 0.06f, r * 0.06f * blink))
    }
}

private fun DrawScope.screenDoor(look: SceneLook, c: Offset, r: Float, t: Float) {
    // The screen door, swung open: light spills over the boards, and out is the way.
    val left = c.x - r * 0.5f
    val topY = c.y - r * 0.95f
    val dw = r * 1.0f
    val dh = r * 1.9f
    drawRect(Brush.verticalGradient(listOf(look.light.copy(alpha = 0.9f), Dusk.glowDeep.copy(alpha = 0.85f)), topY, topY + dh), Offset(left, topY), Size(dw, dh))
    drawRect(look.near, Offset(left - thin * 3, topY - thin * 3), Size(dw + thin * 6, dh + thin * 3), style = Stroke(thin * 4))
    val swing = 0.62f + 0.06f * sin(t * 0.9f)
    val door = Path().apply {
        moveTo(left, topY); lineTo(left - dw * swing * 0.55f, topY - r * 0.12f)
        lineTo(left - dw * swing * 0.55f, topY + dh + r * 0.12f); lineTo(left, topY + dh); close()
    }
    drawPath(door, look.near.copy(alpha = 0.55f))
    drawPath(door, look.near, style = Stroke(thin * 3))
    val spill = Path().apply {
        moveTo(left, topY + dh); lineTo(left + dw, topY + dh)
        lineTo(left + dw + r * 0.6f, topY + dh + r * 0.4f); lineTo(left - r * 0.3f, topY + dh + r * 0.4f); close()
    }
    drawPath(spill, Brush.verticalGradient(listOf(look.light.copy(alpha = 0.45f), Color.Transparent), topY + dh, topY + dh + r * 0.4f))
    val a = t * 0.7f
    moth(Offset(left - r * 0.15f + cos(a) * r * 0.25f, c.y + sin(a * 1.3f) * r * 0.35f), r * 0.28f, t * 15f, look.ink, 0.7f, heading(-sin(a), cos(a * 1.3f)))
}

private fun DrawScope.knothole(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A knothole in the boards: through it the night, and one far light, calling.
    val hole = Size(r * 1.1f, r * 1.4f)
    val tl = Offset(c.x - hole.width / 2, c.y - hole.height / 2)
    drawOval(look.near, Offset(tl.x - r * 0.12f, tl.y - r * 0.12f), Size(hole.width + r * 0.24f, hole.height + r * 0.24f))
    drawOval(Brush.verticalGradient(listOf(Color(0xFF101538), Color(0xFF3A2A4E)), tl.y, tl.y + hole.height), tl, hole)
    val far = Offset(c.x + r * 0.08f, c.y + r * 0.25f)
    val call = 0.5f + 0.5f * sin(t * 1.6f)
    clipPath(Path().apply { addOval(Rect(tl, hole)) }) {
        drawRect(Color(0xFF1A1030), Offset(tl.x, far.y + r * 0.05f), Size(hole.width, hole.height))
        glow(far, r * (0.45f + 0.15f * call), Color(0xFFFFD58A), 0.7f)
        drawCircle(Color(0xFFFFF4D6), r * 0.05f, far)
        drawCircle(Color.White.copy(alpha = 0.7f), 1.2f * density, Offset(c.x - r * 0.25f, c.y - r * 0.35f))
        drawCircle(Color.White.copy(alpha = 0.5f), 1f * density, Offset(c.x + r * 0.3f, c.y - r * 0.2f))
    }
    drawOval(look.ink.copy(alpha = 0.18f), tl, hole, style = Stroke(thin))
}

// ---------------------------------------------------------------------------------------------
// II. The garden

private fun DrawScope.moonflower(look: SceneLook, c: Offset, r: Float, t: Float) {
    val head = Offset(c.x, c.y - r * 0.2f)
    val stem = Path().apply { moveTo(c.x - r * 0.2f, c.y + r * 1.0f); quadraticTo(c.x + r * 0.25f, c.y + r * 0.4f, head.x, head.y) }
    drawPath(stem, look.far, style = Stroke(thin * 3f, cap = StrokeCap.Round))
    for ((dx, dy, a) in listOf(Triple(-0.25f, 0.65f, -40f), Triple(0.15f, 0.35f, 35f))) {
        val leaf = Offset(c.x + r * dx, c.y + r * dy)
        rotate(a, leaf) { drawOval(look.far, Offset(leaf.x - r * 0.25f, leaf.y - r * 0.1f), Size(r * 0.5f, r * 0.2f)) }
    }
    // It opens slowly to the moon and half closes again.
    val open = 0.55f + 0.45f * (0.5f + 0.5f * sin(t * 0.35f))
    glow(head, r * 1.1f * open, Color(0xFFF4FFE8), 0.35f)
    for (k in 0 until 5) {
        rotate(k * 72f + t * 2f, head) {
            drawOval(Color(0xFFF6FBEA).copy(alpha = 0.95f), Offset(head.x - r * 0.12f, head.y - r * 0.5f * open), Size(r * 0.24f, r * 0.5f * open))
        }
    }
    drawCircle(Color(0xFFE6F57E), r * 0.08f, head)
    moth(Offset(head.x + r * 0.45f, head.y - r * 0.55f + sin(t * 2f) * r * 0.06f), r * 0.36f, t * 1.5f, LUNA, 0.95f, -20f)
}

private fun DrawScope.web(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A web strung between two stems, dew glinting on it, and the spider letting itself down.
    for (x in listOf(c.x - r * 0.95f, c.x + r * 0.95f)) {
        drawLine(look.far, Offset(x, c.y - r * 1.1f), Offset(x, c.y + r * 1.1f), thin * 3f)
    }
    val hub = Offset(c.x, c.y - r * 0.15f)
    val spokes = 9
    val reach = r * 0.85f
    fun point(d: Float, k: Double) = Offset(hub.x + (d * cos(k)).toFloat(), hub.y + (d * 0.9f * sin(k)).toFloat())
    for (k in 0 until spokes) drawLine(look.ink.copy(alpha = 0.4f), hub, point(reach, k * 2 * PI / spokes), thin * 0.7f)
    for (ring in 1..4) {
        val d = reach * ring / 4.5f
        val p = Path()
        for (k in 0..spokes) {
            val q = point(d, k * 2 * PI / spokes)
            if (k == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
        }
        drawPath(p, look.ink.copy(alpha = 0.3f), style = Stroke(thin * 0.7f))
    }
    for (k in 0 until 7) {
        val glint = sin(t * 1.4f + k * 1.7f).coerceAtLeast(0f)
        val q = point(reach * (0.3f + 0.1f * k), k * 2.4)
        glow(q, r * 0.12f, Color.White, 0.5f * glint)
        drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * glint), 1.4f * density, q)
    }
    val drop = hub.y + r * (0.45f + 0.35f * (0.5f + 0.5f * sin(t * 0.6f)))
    drawLine(look.ink.copy(alpha = 0.5f), hub, Offset(hub.x, drop), thin * 0.7f)
    for (k in 0 until 4) for (side in listOf(-1f, 1f)) {
        val a = (k - 1.5f) * 0.45f
        drawLine(Color(0xFF0A120C), Offset(hub.x, drop), Offset(hub.x + side * r * 0.16f * cos(a), drop + r * 0.12f * sin(a) - r * 0.02f), thin)
    }
    drawCircle(Color(0xFF0A120C), r * 0.08f, Offset(hub.x, drop))
}

private fun DrawScope.pond(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A little pond: the moon in it, rings spreading out, a lily pad and a bulrush.
    val water = Size(r * 2.1f, r * 0.9f)
    val tl = Offset(c.x - water.width / 2, c.y - water.height / 2 + r * 0.2f)
    drawOval(look.near, Offset(tl.x - r * 0.08f, tl.y - r * 0.06f), Size(water.width + r * 0.16f, water.height + r * 0.12f))
    drawOval(Brush.verticalGradient(listOf(Color(0xFF13302A), Color(0xFF0B1E1A)), tl.y, tl.y + water.height), tl, water)
    clipPath(Path().apply { addOval(Rect(tl, water)) }) {
        val moon = Offset(c.x - r * 0.35f + sin(t * 0.8f) * r * 0.03f, c.y + r * 0.1f)
        glow(moon, r * 0.5f, Color(0xFFFFF4C8), 0.35f)
        drawOval(Color(0xFFFFF4C8).copy(alpha = 0.85f), Offset(moon.x - r * 0.16f, moon.y - r * 0.08f), Size(r * 0.32f, r * 0.16f))
        for (k in 0 until 3) {
            val p = (t * 0.25f + k / 3f) % 1f
            val ring = Offset(c.x + r * 0.35f, c.y + r * 0.25f)
            drawOval(look.ink.copy(alpha = 0.45f * (1f - p)), Offset(ring.x - r * 0.6f * p, ring.y - r * 0.22f * p), Size(r * 1.2f * p, r * 0.44f * p), style = Stroke(thin))
        }
    }
    val pad = Offset(c.x + r * 0.55f, c.y + r * 0.45f)
    drawArc(Color(0xFF2F6B3A), 30f, 310f, true, Offset(pad.x - r * 0.25f, pad.y - r * 0.1f), Size(r * 0.5f, r * 0.2f))
    val sway = sin(t * 0.9f) * r * 0.05f
    drawLine(look.far, Offset(c.x - r * 0.95f, c.y + r * 0.45f), Offset(c.x - r * 0.85f + sway, c.y - r * 0.8f), thin * 2.2f, StrokeCap.Round)
    drawOval(Color(0xFF5A3A22), Offset(c.x - r * 0.9f + sway, c.y - r * 0.75f), Size(r * 0.1f, r * 0.3f))
}

private fun DrawScope.snail(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A snail on its slow way across, carrying a firefly on its shell like a lamp.
    val facing = if (cos(t * 0.12f) >= 0f) 1f else -1f
    val at = Offset(c.x + sin(t * 0.12f) * r * 0.45f, c.y + r * 0.35f)
    drawLine(look.ink.copy(alpha = 0.25f), Offset(c.x - r * 1.1f, at.y + r * 0.2f), Offset(c.x + r * 1.1f, at.y + r * 0.2f), thin * 1.5f)
    val foot = Path().apply {
        moveTo(at.x - facing * r * 0.45f, at.y + r * 0.18f)
        quadraticTo(at.x, at.y + r * 0.22f, at.x + facing * r * 0.5f, at.y + r * 0.12f)
        quadraticTo(at.x + facing * r * 0.55f, at.y - r * 0.08f, at.x + facing * r * 0.35f, at.y - r * 0.02f)
        lineTo(at.x - facing * r * 0.3f, at.y + r * 0.08f)
        close()
    }
    drawPath(foot, Color(0xFF9DA28A))
    for (side in listOf(0f, 1f)) {
        val base = Offset(at.x + facing * r * 0.42f, at.y - r * 0.02f)
        val tip = Offset(base.x + facing * r * (0.12f + side * 0.08f), base.y - r * 0.28f + sin(t * 2f + side) * r * 0.04f)
        drawLine(Color(0xFF9DA28A), base, tip, thin * 1.5f, StrokeCap.Round)
        drawCircle(Color(0xFF9DA28A), thin * 1.6f, tip)
    }
    val shell = Offset(at.x - facing * r * 0.08f, at.y - r * 0.15f)
    drawCircle(Color(0xFF8A5A33), r * 0.3f, shell)
    drawCircle(Color(0xFFB07A45), r * 0.3f, shell, style = Stroke(thin * 2f))
    drawArc(Color(0xFFD9A867), 0f, 300f, false, Offset(shell.x - r * 0.17f, shell.y - r * 0.17f), Size(r * 0.34f, r * 0.34f), style = Stroke(thin * 1.6f))
    drawArc(Color(0xFFD9A867), 60f, 260f, false, Offset(shell.x - r * 0.08f, shell.y - r * 0.08f), Size(r * 0.16f, r * 0.16f), style = Stroke(thin * 1.4f))
    val bug = Offset(shell.x, shell.y - r * 0.38f)
    val lit = 0.5f + 0.5f * sin(t * 2.4f)
    glow(bug, r * 0.5f, look.light, 0.55f * lit + 0.1f)
    drawCircle(look.light, 2.2f * density, bug)
}

private fun DrawScope.gate(look: SceneLook, c: Offset, r: Float, t: Float) {
    // The garden gate, swung open under an arch of vine, and the fireflies streaming out through it.
    val base = c.y + r * 0.9f
    val postW = r * 0.14f
    for (x in listOf(c.x - r * 0.75f, c.x + r * 0.75f - postW)) drawRect(look.near, Offset(x, base - r * 1.5f), Size(postW, r * 1.5f))
    val arch = Path().apply { moveTo(c.x - r * 0.7f, base - r * 1.5f); quadraticTo(c.x, base - r * 2.2f, c.x + r * 0.7f, base - r * 1.5f) }
    drawPath(arch, look.far, style = Stroke(thin * 4f, cap = StrokeCap.Round))
    for (k in 0..6) {
        val f = k / 6f
        bloom(Offset(c.x - r * 0.7f + r * 1.4f * f, base - r * 1.5f - r * 0.7f * 2 * f * (1 - f)), r * 0.05f, PETALS[k % PETALS.size])
    }
    val hinge = c.x - r * 0.6f
    for (k in 0 until 4) {
        val x = hinge - r * 0.12f * (k + 1)
        drawLine(look.ink.copy(alpha = 0.5f), Offset(x, base - r * (1.05f - k * 0.02f)), Offset(x, base + r * 0.05f * k), thin * 3f)
    }
    drawLine(look.ink.copy(alpha = 0.45f), Offset(hinge, base - r * 0.8f), Offset(hinge - r * 0.5f, base - r * 0.72f), thin * 2.5f)
    drawLine(look.ink.copy(alpha = 0.45f), Offset(hinge, base - r * 0.3f), Offset(hinge - r * 0.5f, base - r * 0.2f), thin * 2.5f)
    for (k in 0 until 6) {
        val p = (t * 0.12f + k / 6f) % 1f
        val at = Offset(c.x + r * (1.2f * p - 0.5f) * 0.8f, base - r * 0.7f + r * 1.2f * p * p)
        val lit = sin(p * PI.toFloat())
        glow(at, r * 0.25f, look.light, 0.6f * lit)
        drawCircle(look.light.copy(alpha = lit), 1.8f * density, at)
    }
}

// ---------------------------------------------------------------------------------------------
// III. The woods

private fun DrawScope.owl(look: SceneLook, c: Offset, r: Float, t: Float) {
    // An old trunk and a branch, and on it the owl: its head turns to follow, and it blinks.
    val trunkX = c.x + r * 0.7f
    drawRect(look.near, Offset(trunkX, c.y - r * 1.4f), Size(r * 0.4f, r * 2.6f))
    val branch = Path().apply {
        moveTo(trunkX, c.y + r * 0.25f); lineTo(c.x - r * 0.9f, c.y + r * 0.35f)
        lineTo(c.x - r * 0.9f, c.y + r * 0.45f); lineTo(trunkX, c.y + r * 0.42f); close()
    }
    drawPath(branch, look.near)
    val feather = Color(0xFF2E3A36)
    val body = Offset(c.x - r * 0.1f, c.y - r * 0.05f)
    drawOval(feather, Offset(body.x - r * 0.3f, body.y - r * 0.35f), Size(r * 0.6f, r * 0.7f))
    drawOval(Color(0xFF3F4E48), Offset(body.x - r * 0.18f, body.y - r * 0.05f), Size(r * 0.36f, r * 0.36f))
    val turn = sin(t * 0.4f) * r * 0.06f
    val head = Offset(body.x + turn, body.y - r * 0.38f)
    drawOval(feather, Offset(head.x - r * 0.28f, head.y - r * 0.22f), Size(r * 0.56f, r * 0.44f))
    for (side in listOf(-1f, 1f)) {
        drawPath(triangle(Offset(head.x + side * r * 0.24f, head.y - r * 0.12f), Offset(head.x + side * r * 0.3f, head.y - r * 0.36f), Offset(head.x + side * r * 0.1f, head.y - r * 0.18f)), feather)
    }
    val open = if ((t % 5.2f) < 0.22f) 0.1f else 1f
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(head.x + side * r * 0.12f + turn * 0.5f, head.y)
        glow(eye, r * 0.22f, Color(0xFFFFC94A), 0.35f * open)
        drawOval(Color(0xFFFFC94A), Offset(eye.x - r * 0.09f, eye.y - r * 0.09f * open), Size(r * 0.18f, r * 0.18f * open))
        if (open > 0.5f) drawCircle(Color(0xFF0A0D0C), r * 0.045f, eye)
    }
    val beak = head.x + turn * 0.5f
    drawPath(triangle(Offset(beak - r * 0.04f, head.y + r * 0.08f), Offset(beak + r * 0.04f, head.y + r * 0.08f), Offset(beak, head.y + r * 0.17f)), Color(0xFFC9A24A))
}

private fun DrawScope.mushroomRing(c: Offset, r: Float, t: Float) {
    // A fairy ring: each cap glows in turn, round and round, and spores drift up.
    val n = 7
    for (k in 0 until n) {
        val a = k * 2 * PI / n
        val at = Offset(c.x + (r * 0.85f * cos(a)).toFloat(), c.y + r * 0.3f + (r * 0.32f * sin(a)).toFloat())
        val s = r * (if (sin(a) < 0) 0.2f else 0.26f)
        val on = ((sin(t * 1.2f - k * 0.9f) + 1f) / 2f).let { it * it }
        glow(at, s * 3f, Color(0xFF8CF0D4), 0.15f + 0.4f * on)
        drawRect(Color(0xFFD8E8DC), Offset(at.x - s * 0.18f, at.y - s * 0.1f), Size(s * 0.36f, s * 0.9f))
        drawArc(Color(0xFF8CF0D4).copy(alpha = 0.55f + 0.45f * on), 180f, 180f, true, Offset(at.x - s * 0.7f, at.y - s * 0.55f), Size(s * 1.4f, s * 1.1f))
    }
    for (m in 0 until 8) {
        val p = (t * 0.08f + m / 8f) % 1f
        val at = Offset(c.x + sin(t * 0.5f + m * 2.1f) * r * 0.6f, c.y + r * 0.3f - p * r * 1.4f)
        drawCircle(Color(0xFFBFFFE8).copy(alpha = 0.6f * sin(p * PI.toFloat())), 1.3f * density, at)
    }
}

private fun DrawScope.stream(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A stream running across, a fallen log over it, and the water's light sliding downstream.
    val band = Path().apply {
        moveTo(c.x - r * 1.4f, c.y - r * 0.1f)
        cubicTo(c.x - r * 0.4f, c.y - r * 0.4f, c.x + r * 0.3f, c.y + r * 0.5f, c.x + r * 1.4f, c.y + r * 0.2f)
        lineTo(c.x + r * 1.4f, c.y + r * 0.62f)
        cubicTo(c.x + r * 0.3f, c.y + r * 0.95f, c.x - r * 0.4f, c.y + r * 0.05f, c.x - r * 1.4f, c.y + r * 0.35f)
        close()
    }
    drawPath(band, Brush.verticalGradient(listOf(Color(0xFF1E5560), Color(0xFF123842)), c.y - r * 0.4f, c.y + r * 0.9f))
    clipPath(band) {
        for (k in 0 until 7) {
            val p = (t * 0.09f + k / 7f) % 1f
            val x = c.x - r * 1.4f + r * 2.8f * p
            val y = c.y + r * 0.22f + sin(p * 6f + k) * r * 0.18f + (k % 3 - 1) * r * 0.1f
            drawLine(Color(0xFFBFE2DA).copy(alpha = 0.5f * sin(p * PI.toFloat())), Offset(x, y), Offset(x + r * 0.22f, y + r * 0.03f), thin * 1.4f, StrokeCap.Round)
        }
    }
    rotate(-14f, c) {
        drawRoundRect(look.near, Offset(c.x - r * 1.1f, c.y + r * 0.05f), Size(r * 2.2f, r * 0.28f), CornerRadius(r * 0.14f))
        drawCircle(look.far, r * 0.12f, Offset(c.x + r * 1.0f, c.y + r * 0.19f))
        drawCircle(look.ink.copy(alpha = 0.3f), r * 0.12f, Offset(c.x + r * 1.0f, c.y + r * 0.19f), style = Stroke(thin))
    }
}

private fun DrawScope.fox(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A fox curled up asleep under the ferns, its side rising and falling.
    val breath = 1f + 0.04f * sin(t * 1.3f)
    val at = Offset(c.x, c.y + r * 0.35f)
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(at.x - r * 0.75f, at.y + r * 0.2f), Size(r * 1.5f, r * 0.2f))
    scale(breath, breath, at) {
        drawOval(Color(0xFF9A4A24), Offset(at.x - r * 0.55f, at.y - r * 0.35f), Size(r * 1.1f, r * 0.62f))
    }
    val tail = Path().apply {
        moveTo(at.x + r * 0.45f, at.y)
        quadraticTo(at.x + r * 0.7f, at.y + r * 0.45f, at.x - r * 0.1f, at.y + r * 0.28f)
        quadraticTo(at.x - r * 0.45f, at.y + r * 0.2f, at.x - r * 0.55f, at.y + r * 0.05f)
        quadraticTo(at.x - r * 0.2f, at.y + r * 0.15f, at.x + r * 0.45f, at.y)
        close()
    }
    drawPath(tail, Color(0xFFB85A2C))
    drawCircle(Color(0xFFF2E6D6), r * 0.09f, Offset(at.x - r * 0.52f, at.y + r * 0.07f))
    val head = Offset(at.x - r * 0.3f, at.y - r * 0.12f)
    drawOval(Color(0xFFB85A2C), Offset(head.x - r * 0.25f, head.y - r * 0.15f), Size(r * 0.42f, r * 0.3f))
    for (dx in listOf(-0.18f, 0.02f)) {
        drawPath(triangle(Offset(head.x + r * dx, head.y - r * 0.1f), Offset(head.x + r * (dx + 0.06f), head.y - r * 0.32f), Offset(head.x + r * (dx + 0.14f), head.y - r * 0.08f)), Color(0xFF7A3518))
    }
    drawLine(Color(0xFF2A140A), Offset(head.x - r * 0.15f, head.y), Offset(head.x - r * 0.06f, head.y + r * 0.02f), thin, StrokeCap.Round)
    // Its breath clouding in the cold.
    val p = (t * 0.25f) % 1f
    drawCircle(look.ink.copy(alpha = 0.25f * sin(p * PI.toFloat())), r * (0.04f + 0.1f * p), Offset(head.x - r * 0.35f - p * r * 0.3f, head.y - p * r * 0.25f))
    for ((dx, s) in listOf(-0.8f to 0.9f, 0.75f to 1.1f)) fern(look, Offset(at.x + r * dx, at.y + r * 0.3f), r * 0.5f * s)
}

private fun DrawScope.fern(look: SceneLook, base: Offset, s: Float) {
    for (k in -2..2) {
        val tip = Offset(base.x + k * s * 0.35f, base.y - s * (1f - abs(k) * 0.15f))
        val frond = Path().apply { moveTo(base.x, base.y); quadraticTo(base.x + k * s * 0.05f, tip.y + s * 0.3f, tip.x, tip.y) }
        drawPath(frond, look.near, style = Stroke(thin * 2.4f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.woodsEdge(look: SceneLook, c: Offset, r: Float, t: Float) {
    // Where the trees part: the town far off on its hill, its windows coming on one by one.
    val view = Size(r * 2.1f, r * 1.3f)
    val tl = Offset(c.x - view.width / 2, c.y - view.height / 2)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1B2D52), Color(0xFF2E3A6A)), tl.y, tl.y + view.height), tl, view, CornerRadius(r * 0.5f))
    val roofs = listOf(0.62f, 0.5f, 0.56f, 0.38f, 0.58f, 0.46f, 0.6f)
    val town = Path().apply {
        moveTo(tl.x, tl.y + view.height)
        roofs.forEachIndexed { k, h ->
            val x0 = tl.x + view.width * k / roofs.size
            lineTo(x0, tl.y + view.height * h); lineTo(x0 + view.width / roofs.size * 0.5f, tl.y + view.height * (h - 0.08f)); lineTo(x0 + view.width / roofs.size, tl.y + view.height * h)
        }
        lineTo(tl.x + view.width, tl.y + view.height); close()
    }
    drawPath(town, Color(0xFF12142E))
    for (k in 0 until 9) {
        val x = tl.x + view.width * (0.08f + k * 0.1f)
        val y = tl.y + view.height * (0.68f + (k % 3) * 0.08f)
        val on = if ((sin(t * 0.5f + k * 1.3f) + 1f) / 2f > 0.35f) 1f else 0.2f
        drawRect(Color(0xFFFFD27A).copy(alpha = on), Offset(x, y), Size(r * 0.06f, r * 0.08f))
    }
    for ((dx, s) in listOf(-1.1f to 1.7f, 1.1f to 1.9f, -0.85f to 1.2f, 0.9f to 1.3f)) pine(Offset(c.x + r * dx, c.y + r * 0.9f), r * s, look.near)
}

// ---------------------------------------------------------------------------------------------
// IV. The rooftops

private fun DrawScope.clockTower(look: SceneLook, c: Offset, r: Float, t: Float) {
    // The town clock, lit, its hands moving on.
    val base = c.y + r * 1.1f
    val tw = r * 0.62f
    drawRect(look.near, Offset(c.x - tw / 2, c.y - r * 0.75f), Size(tw, base - (c.y - r * 0.75f)))
    drawPath(triangle(Offset(c.x - tw * 0.62f, c.y - r * 0.75f), Offset(c.x, c.y - r * 1.45f), Offset(c.x + tw * 0.62f, c.y - r * 0.75f)), look.near)
    drawLine(look.ink.copy(alpha = 0.6f), Offset(c.x, c.y - r * 1.45f), Offset(c.x, c.y - r * 1.65f), thin * 1.5f)
    val face = Offset(c.x, c.y - r * 0.38f)
    glow(face, r * 0.8f, look.light, 0.35f)
    drawCircle(Color(0xFFFFF0C8), r * 0.25f, face)
    drawCircle(look.near, r * 0.25f, face, style = Stroke(thin * 2f))
    for (k in 0 until 12) {
        val a = k * PI / 6
        drawCircle(look.near, thin * 0.8f, Offset(face.x + (r * 0.19f * cos(a)).toFloat(), face.y + (r * 0.19f * sin(a)).toFloat()))
    }
    val minute = t * 0.5f
    val hour = minute / 12f + 2f
    drawLine(look.near, face, Offset(face.x + r * 0.17f * sin(minute), face.y - r * 0.17f * cos(minute)), thin * 1.4f, StrokeCap.Round)
    drawLine(look.near, face, Offset(face.x + r * 0.11f * sin(hour), face.y - r * 0.11f * cos(hour)), thin * 2.2f, StrokeCap.Round)
    for (k in 0 until 2) drawRect(look.light.copy(alpha = 0.8f), Offset(c.x - r * 0.07f, c.y + r * (0.12f + k * 0.35f)), Size(r * 0.14f, r * 0.2f))
}

private fun DrawScope.chimneyCat(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A roof with a chimney breathing smoke, and a cat on the ridge, watching the moths go over.
    val ridge = c.y - r * 0.05f
    val roof = Path().apply {
        moveTo(c.x - r * 1.25f, c.y + r * 1.0f); lineTo(c.x - r * 0.75f, ridge); lineTo(c.x + r * 0.95f, ridge); lineTo(c.x + r * 1.35f, c.y + r * 1.0f); close()
    }
    drawPath(roof, look.near)
    for (k in 1..3) drawLine(look.ink.copy(alpha = 0.1f), Offset(c.x - r * (0.75f + k * 0.12f), ridge + r * 0.25f * k), Offset(c.x + r * (0.95f + k * 0.1f), ridge + r * 0.25f * k), thin)
    val chim = Offset(c.x + r * 0.55f, ridge)
    drawRect(look.near, Offset(chim.x - r * 0.14f, chim.y - r * 0.5f), Size(r * 0.28f, r * 0.5f))
    drawRect(look.near, Offset(chim.x - r * 0.18f, chim.y - r * 0.56f), Size(r * 0.36f, r * 0.08f))
    for (k in 0 until 5) {
        val p = (t * 0.18f + k / 5f) % 1f
        val at = Offset(chim.x + p * r * 0.5f + sin(t * 0.8f + k) * r * 0.08f, chim.y - r * 0.6f - p * r * 1.1f)
        drawCircle(look.ink.copy(alpha = 0.22f * (1f - p)), r * (0.08f + 0.2f * p), at)
    }
    val cat = Offset(c.x - r * 0.35f, ridge)
    drawOval(look.near, Offset(cat.x - r * 0.15f, cat.y - r * 0.42f), Size(r * 0.3f, r * 0.44f))
    val head = Offset(cat.x, cat.y - r * 0.5f)
    drawCircle(look.near, r * 0.13f, head)
    for (side in listOf(-1f, 1f)) drawPath(triangle(Offset(head.x + side * r * 0.12f, head.y - r * 0.02f), Offset(head.x + side * r * 0.1f, head.y - r * 0.22f), Offset(head.x, head.y - r * 0.1f)), look.near)
    val tail = Path().apply {
        moveTo(cat.x + r * 0.12f, cat.y - r * 0.05f)
        quadraticTo(cat.x + r * (0.45f + 0.1f * sin(t * 1.2f)), cat.y + r * 0.05f, cat.x + r * (0.4f + 0.15f * sin(t * 1.2f + 1f)), cat.y + r * 0.35f)
    }
    drawPath(tail, look.near, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
    val gaze = sin(t * 0.3f) * r * 0.03f
    val blink = if ((t % 6f) < 0.15f) 0.15f else 1f
    for (side in listOf(-1f, 1f)) drawOval(Color(0xFFFFE27A), Offset(head.x + side * r * 0.05f - r * 0.025f + gaze, head.y - r * 0.025f * blink), Size(r * 0.05f, r * 0.05f * blink))
}

private fun DrawScope.washingLine(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A line strung between two chimneys, the washing lifting in the night air.
    val a = Offset(c.x - r * 1.0f, c.y - r * 0.55f)
    val b = Offset(c.x + r * 1.0f, c.y - r * 0.45f)
    // Two roofs, a gap of night between them, a chimney on each.
    for ((p, side) in listOf(a to -1f, b to 1f)) {
        val eave = c.y + r * 0.55f
        val roof = Path().apply {
            moveTo(p.x - side * r * 0.35f, eave + r * 0.7f); lineTo(p.x - side * r * 0.35f, eave)
            lineTo(p.x + side * r * 0.25f, eave - r * 0.35f); lineTo(p.x + side * r * 0.75f, eave + r * 0.05f)
            lineTo(p.x + side * r * 0.75f, eave + r * 0.7f); close()
        }
        drawPath(roof, look.near)
        drawPath(roof, look.ink.copy(alpha = 0.15f), style = Stroke(thin))
        drawRect(look.near, Offset(p.x - r * 0.1f, p.y - r * 0.05f), Size(r * 0.2f, eave - p.y))
        drawRect(look.near, Offset(p.x - r * 0.14f, p.y - r * 0.11f), Size(r * 0.28f, r * 0.08f))
        drawRect(look.light.copy(alpha = 0.75f), Offset(p.x + side * r * 0.2f - r * 0.07f, eave + r * 0.18f), Size(r * 0.14f, r * 0.2f))
    }
    val sag = r * 0.25f
    fun line(f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f + sag * 4 * f * (1 - f))
    drawPath(Path().apply { moveTo(a.x, a.y); quadraticTo(c.x, (a.y + b.y) / 2 + sag * 2, b.x, b.y) }, look.ink.copy(alpha = 0.5f), style = Stroke(thin))
    val clothes = listOf(Triple(0.22f, Color(0xFFE9E3F6), 0.42f), Triple(0.5f, Color(0xFFB9C7F2), 0.52f), Triple(0.76f, Color(0xFFF2D6C9), 0.36f))
    clothes.forEachIndexed { k, (f, colour, len) ->
        val top = line(f)
        val wide = r * 0.36f
        val lift = sin(t * 1.3f + k * 1.1f) * r * 0.12f
        val sheet = Path().apply {
            moveTo(top.x - wide / 2, top.y); lineTo(top.x + wide / 2, top.y + r * 0.02f)
            quadraticTo(top.x + wide / 2 + lift, top.y + r * len * 0.6f, top.x + wide / 2 + lift * 1.6f, top.y + r * len)
            lineTo(top.x - wide / 2 + lift * 1.6f, top.y + r * len)
            quadraticTo(top.x - wide / 2 + lift, top.y + r * len * 0.6f, top.x - wide / 2, top.y)
            close()
        }
        drawPath(sheet, colour.copy(alpha = 0.85f))
        drawPath(sheet, look.near.copy(alpha = 0.3f), style = Stroke(thin))
    }
}

private fun DrawScope.attic(look: SceneLook, c: Offset, r: Float, t: Float) {
    // A gable with a round attic window, a candle burning in it, and Luna resting on the sill.
    val base = c.y + r * 1.1f
    val gable = Path().apply {
        moveTo(c.x - r * 0.95f, base); lineTo(c.x - r * 0.95f, c.y - r * 0.15f); lineTo(c.x, c.y - r * 1.0f)
        lineTo(c.x + r * 0.95f, c.y - r * 0.15f); lineTo(c.x + r * 0.95f, base); close()
    }
    drawPath(gable, look.near)
    drawPath(gable, look.ink.copy(alpha = 0.15f), style = Stroke(thin))
    val win = Offset(c.x, c.y - r * 0.05f)
    val flicker = 0.08f * sin(t * 7f) + 0.05f * sin(t * 15f)
    glow(win, r * 1.0f, look.light, 0.4f + flicker)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9B0), look.light, Dusk.glowDeep), win, r * 0.35f), r * 0.33f, win)
    drawLine(look.near, Offset(win.x - r * 0.33f, win.y), Offset(win.x + r * 0.33f, win.y), thin * 2f)
    drawLine(look.near, Offset(win.x, win.y - r * 0.33f), Offset(win.x, win.y + r * 0.33f), thin * 2f)
    drawRect(Color(0xFFF6EAD0), Offset(win.x + r * 0.06f, win.y + r * 0.08f), Size(r * 0.07f, r * 0.17f))
    drawOval(Color(0xFFFFF4C8), Offset(win.x + r * 0.065f, win.y - r * (0.02f + 0.02f * sin(t * 9f))), Size(r * 0.06f, r * 0.11f))
    drawRect(look.far, Offset(win.x - r * 0.45f, win.y + r * 0.36f), Size(r * 0.9f, r * 0.07f))
    moth(Offset(win.x - r * 0.2f, win.y + r * 0.3f), r * 0.36f, t * 0.6f, LUNA, 0.95f, 0f)
    for (k in 0 until 2) drawRect(look.light.copy(alpha = 0.75f), Offset(c.x + r * (-0.6f + k * 0.85f), c.y + r * 0.55f), Size(r * 0.18f, r * 0.24f))
}

// ---------------------------------------------------------------------------------------------
// V. The last porch

private fun DrawScope.windmill(look: SceneLook, at: Offset, r: Float, t: Float) {
    // A windmill on its hill, its sails turning slowly in the dawn wind.
    val c = Offset(at.x, at.y + r * 0.35f)
    val base = c.y + r * 1.05f
    drawPath(Path().apply { moveTo(c.x - r * 1.3f, base + r * 0.2f); quadraticTo(c.x, base - r * 0.4f, c.x + r * 1.3f, base + r * 0.2f); close() }, look.far)
    drawPath(Path().apply { moveTo(c.x - r * 0.28f, base - r * 0.1f); lineTo(c.x - r * 0.16f, c.y - r * 0.4f); lineTo(c.x + r * 0.16f, c.y - r * 0.4f); lineTo(c.x + r * 0.28f, base - r * 0.1f); close() }, look.near)
    drawPath(triangle(Offset(c.x - r * 0.22f, c.y - r * 0.38f), Offset(c.x, c.y - r * 0.62f), Offset(c.x + r * 0.22f, c.y - r * 0.38f)), look.near)
    drawRect(look.light.copy(alpha = 0.85f), Offset(c.x - r * 0.06f, c.y + r * 0.1f), Size(r * 0.12f, r * 0.18f))
    val hub = Offset(c.x, c.y - r * 0.45f)
    rotate(t * 14f, hub) {
        for (k in 0 until 4) {
            rotate(k * 90f, hub) {
                drawLine(look.near, hub, Offset(hub.x, hub.y - r * 0.85f), thin * 2f)
                drawRect(look.ink.copy(alpha = 0.35f), Offset(hub.x + thin, hub.y - r * 0.82f), Size(r * 0.16f, r * 0.6f))
                drawRect(look.near, Offset(hub.x + thin, hub.y - r * 0.82f), Size(r * 0.16f, r * 0.6f), style = Stroke(thin))
            }
        }
    }
    drawCircle(look.near, r * 0.05f, hub)
}

private fun DrawScope.letterbox(look: SceneLook, c: Offset, r: Float, t: Float) {
    // The lane to the last porch: a bit of fence, the letterbox with its flag up, moths about it.
    val ground = c.y + r * 0.85f
    for (k in 0 until 5) {
        val x = c.x - r * 1.1f + k * r * 0.3f
        drawPath(Path().apply { moveTo(x, ground); lineTo(x, ground - r * 0.65f); lineTo(x + r * 0.08f, ground - r * 0.75f); lineTo(x + r * 0.16f, ground - r * 0.65f); lineTo(x + r * 0.16f, ground); close() }, look.ink.copy(alpha = 0.5f))
    }
    drawRect(look.ink.copy(alpha = 0.45f), Offset(c.x - r * 1.15f, ground - r * 0.45f), Size(r * 1.5f, r * 0.06f))
    val post = Offset(c.x + r * 0.6f, ground)
    drawRect(look.near, Offset(post.x - r * 0.05f, post.y - r * 0.9f), Size(r * 0.1f, r * 0.9f))
    val box = Offset(post.x - r * 0.32f, post.y - r * 1.2f)
    drawRoundRect(Color(0xFF6A3A4A), box, Size(r * 0.64f, r * 0.32f), CornerRadius(r * 0.14f))
    drawRoundRect(look.ink.copy(alpha = 0.3f), box, Size(r * 0.64f, r * 0.32f), CornerRadius(r * 0.14f), style = Stroke(thin))
    val pivot = Offset(box.x + r * 0.56f, box.y + r * 0.2f)
    rotate(10f * sin(t * 1.5f), pivot) {
        drawRect(Color(0xFFE85A4A), Offset(pivot.x - thin, pivot.y - r * 0.32f), Size(thin * 2f, r * 0.32f))
        drawRect(Color(0xFFE85A4A), Offset(pivot.x - thin, pivot.y - r * 0.32f), Size(r * 0.14f, r * 0.1f))
    }
    for (k in 0 until 2) {
        val a = t * 0.8f + k * 3f
        moth(Offset(box.x + r * 0.32f + cos(a) * r * 0.5f, box.y - r * 0.3f + sin(a * 1.3f) * r * 0.15f), r * 0.24f, t * 16f + k, look.ink, 0.6f, heading(-sin(a), cos(a * 1.3f)))
    }
}

private fun DrawScope.gathering(look: SceneLook, c: Offset, r: Float, t: Float, i: Int) {
    // The wanderers from every chapter, drifting in on their way to the light.
    glow(c, r, look.light, 0.16f)
    for (k in 0 until 6) {
        val a = t * (0.6f + k * 0.07f) + k * 1.05f + i
        val d = r * (0.35f + 0.5f * abs(sin(t * 0.4f + k)))
        val depth = sin(a)
        val at = Offset(c.x + d * cos(a), c.y + d * 0.45f * depth)
        moth(at, r * (0.24f + 0.08f * depth), t * 17f + k, look.ink, 0.5f + 0.3f * depth, heading(-sin(a), 0.45f * cos(a)))
    }
}

private fun DrawScope.lastPorch(look: SceneLook, c: Offset, r: Float, t: Float) {
    // Home: the house with its porch light left on, the moths circling it, and Luna come to rest.
    house(look, c, r)
    val lamp = Offset(c.x, c.y + r * 0.35f)
    val pulse = 0.5f + 0.5f * sin(t * 2.2f)
    godRays(lamp, -90f, 210f, 6, r * 2.4f, look.light, 0.22f + 0.08f * pulse, t, seed = 5, motes = false)
    glow(lamp, r * (1.2f + 0.2f * pulse), look.light, 0.55f + 0.2f * pulse)
    drawCircle(Color(0xFFFFF4D6), r * 0.08f, lamp)
    orbit(look, lamp, r * 1.05f, 5, t, 1.1f, 0f)
    moth(Offset(lamp.x + r * 0.32f, lamp.y + r * 0.02f), r * 0.36f, t * 0.7f, LUNA, 1f, 20f)
}

private fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
