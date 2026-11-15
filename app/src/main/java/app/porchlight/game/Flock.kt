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

private const val FLIGHT = 0.52f

/** The share of the flight spent rising straight up out of the card, before the turn to the bulb. */
private const val RISE = 0.42f

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
 * Draws the flock. A found moth rises straight up out of its card to above the board, then curves
 * to the bulb, in about half a second, so it spends as little time over the cards as it can. Then it
 * circles the bulb on a tight ring that stays clear of the score and streak plates.
 *
 * The flock is drawn twice: [front] false, behind the bulb, for moths on the far half of their ring;
 * [front] true, over everything, for the near half and for moths still in flight. That is what makes
 * them read as circling the bulb rather than sitting on it.
 */
@Composable
internal fun FlockLayer(session: Session, front: Boolean, modifier: Modifier = Modifier) {
    val flock = session.flock
    Canvas(modifier.fillMaxSize()) {
        val now = session.clock.seconds
        val bulb = session.bulbAt
        val bulbW = session.bulbWidth
        if (bulbW <= 0f) return@Canvas
        val fled = if (flock.scatterAt >= 0f) now - flock.scatterAt else 0f
        val slide = parallaxPx(density) * DEPTH_MOTHS
        val top = (session.boardTop - bulbW * 0.12f).coerceAtLeast(bulb.y)

        for (i in flock.fliers.indices) {
            val f = flock.fliers[i]
            // Ring radius: at most 1.6 x the bulb's own radius.
            val ring = bulbW * (0.56f + 0.12f * (f.slot % 3))
            val speed = (0.55f + 0.09f * (f.slot % 4)) * if (f.slot % 2 == 0) 1f else -1f
            val phase = f.slot * 2.399f
            val restWidth = bulbW * 0.3f
            val age = now - f.born

            var x: Float
            var y: Float
            val width: Float
            val flap: Float
            val near: Boolean
            if (age < FLIGHT) {
                near = true
                val u = age / FLIGHT
                val arrive = phase + (f.born + FLIGHT) * speed
                val tx = bulb.x + cos(arrive) * ring
                val ty = bulb.y + sin(arrive) * ring * 0.42f
                val startY = minOf(f.from.y, top)
                if (u < RISE) {
                    // Straight up, quickly, out of the card.
                    val e = u / RISE
                    x = f.from.x
                    y = f.from.y + (startY - f.from.y) * (1f - (1f - e) * (1f - e))
                } else {
                    // Then a curve above the board to its place on the ring.
                    val e = (u - RISE) / (1f - RISE)
                    val s = e * e * (3f - 2f * e)
                    // The bend goes sideways along the band first, then up, so it passes under the
                    // score and streak plates instead of across them.
                    val cx = tx
                    val cy = startY
                    val m = 1f - s
                    x = m * m * f.from.x + 2f * m * s * cx + s * s * tx
                    y = m * m * startY + 2f * m * s * cy + s * s * ty
                }
                width = f.fromWidth + (restWidth - f.fromWidth) * u
                flap = 0.35f + 0.65f * abs(sin(now * 22f + f.slot))
            } else {
                val a = phase + now * speed
                val depth = sin(a)
                near = depth >= 0f
                x = bulb.x + cos(a) * ring
                y = bulb.y + depth * ring * 0.42f + sin(now * 2.3f + f.slot) * bulbW * 0.03f
                width = restWidth * (0.88f + 0.16f * depth)
                flap = 0.5f + 0.5f * abs(sin(now * 9f + f.slot * 1.7f))
            }
            if (near != front) continue

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
            x += session.tilt.x * slide
            y += session.tilt.y * slide
            moth(
                f.moth,
                Offset(x, y),
                width,
                flap,
                tilt = sin(now * 3f + f.slot) * 9f,
                gold = f.moth in session.store.progress.mastered,
            )
        }
    }
}
