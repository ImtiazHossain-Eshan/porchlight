package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val FLIGHT = 0.95f

/** A matched moth: it leaves its card at [born] and joins the ring around the bulb. */
internal class Flier(val moth: MothId, val from: Offset, val fromWidth: Float, val born: Float, val slot: Int)

/** Every moth found this round. They circle the bulb until the round ends. */
internal class Flock {
    val fliers = mutableStateListOf<Flier>()

    /** Clock time the bulb died and the moths fled, or -1 while they are still circling. */
    var scatterAt = -1f

    fun add(moth: MothId, from: Offset, fromWidth: Float, now: Float) {
        fliers += Flier(moth, from, fromWidth, now, fliers.size)
    }

    fun clear() {
        fliers.clear()
        scatterAt = -1f
    }
}

/**
 * Draws the flock above the board. Each moth takes a curved, slightly wobbling path from its card to
 * the bulb, then circles it on one of three tilted rings.
 */
@Composable
internal fun FlockLayer(session: Session, modifier: Modifier = Modifier) {
    val flock = session.flock
    Canvas(modifier.fillMaxSize()) {
        val now = session.clock.seconds
        val bulb = session.bulbAt
        val bulbW = session.bulbWidth
        if (bulbW <= 0f) return@Canvas
        val fled = if (flock.scatterAt >= 0f) now - flock.scatterAt else 0f

        for (i in flock.fliers.indices) {
            val f = flock.fliers[i]
            val ring = bulbW * (0.74f + 0.17f * (f.slot % 3))
            val speed = (0.55f + 0.09f * (f.slot % 4)) * if (f.slot % 2 == 0) 1f else -1f
            val phase = f.slot * 2.399f
            val restWidth = bulbW * 0.36f
            val age = now - f.born

            var x: Float
            var y: Float
            var width: Float
            val flap: Float
            if (age < FLIGHT) {
                val u = age / FLIGHT
                val e = u * u * (3f - 2f * u)
                val arrive = phase + (f.born + FLIGHT) * speed
                val tx = bulb.x + cos(arrive) * ring
                val ty = bulb.y + sin(arrive) * ring * 0.42f
                val side = if (f.slot % 2 == 0) 1f else -1f
                // Cubic bezier: up and out from the card, then swinging in under the bulb.
                val c1x = f.from.x + side * bulbW * 0.9f
                val c1y = f.from.y - bulbW * 1.1f
                val c2x = tx - side * bulbW * 1.2f
                val c2y = ty + bulbW * 1.3f
                val m = 1f - e
                x = m * m * m * f.from.x + 3f * m * m * e * c1x + 3f * m * e * e * c2x + e * e * e * tx
                y = m * m * m * f.from.y + 3f * m * m * e * c1y + 3f * m * e * e * c2y + e * e * e * ty
                x += sin(u * 18f + f.slot) * bulbW * 0.07f * m
                width = f.fromWidth + (restWidth - f.fromWidth) * e
                flap = 0.35f + 0.65f * abs(sin(now * 22f + f.slot))
            } else {
                val a = phase + now * speed
                val depth = sin(a)
                x = bulb.x + cos(a) * ring
                y = bulb.y + depth * ring * 0.42f + sin(now * 2.3f + f.slot) * bulbW * 0.03f
                width = restWidth * (0.9f + 0.14f * depth)
                flap = 0.5f + 0.5f * abs(sin(now * 9f + f.slot * 1.7f))
            }

            if (fled > 0f) {
                // The bulb popped: everyone bolts away from where it was.
                var dx = x - bulb.x
                var dy = y - bulb.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                dx /= d
                dy /= d
                val run = fled * fled * size.height * 2.2f + fled * bulbW
                x += dx * run
                y += dy * run * 0.8f - fled * bulbW
                if (x < -width || x > size.width + width || y < -width || y > size.height + width) continue
            }
            moth(f.moth, Offset(x, y), width, flap, tilt = sin(now * 3f + f.slot) * 9f, gold = f.moth in session.store.progress.mastered)
        }
    }
}
