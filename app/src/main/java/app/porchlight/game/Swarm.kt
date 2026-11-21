package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** How long one firefly takes from where it was earned to the counter, and the gap between them. */
private const val TRAVEL_S = 0.85f
private const val STAGGER_S = 0.08f

/** The most fireflies one payment sends: big ones split their amount among these. */
private const val MAX_BUGS = 10

/**
 * Fireflies on their way into the counter. Each payment sends a few, from where it was earned (a
 * star, a badge, the results' tally, the jar for the daily gift) to wherever the counter is; the
 * counter holds back what is still in the air ([inFlight]) and rolls up as each one lands.
 */
internal class Swarm(private val clock: Clock) {
    class Bug(val from: Offset, val leaves: Float, val bend: Float, val share: Int)

    val bugs = mutableStateListOf<Bug>()

    /** Fireflies already in the wallet but still flying: the counter shows the balance less these. */
    var inFlight by mutableIntStateOf(0)
        private set

    /** Where the counter is on screen, in root pixels; unknown while none is showing. */
    var counterAt by mutableStateOf(Offset.Unspecified)

    /** Counts landings, so the counter can bob each time one arrives. */
    var landed by mutableIntStateOf(0)
        private set

    /** The pitch of the next landing's chime: it climbs as a flight comes in. */
    private var note by mutableFloatStateOf(1f)

    /**
     * Holds back [amount] from the counter before it flies: for a payment already in the wallet whose
     * flight starts a moment later (the results' tally builds up first).
     */
    fun hold(amount: Int) {
        if (amount > 0) inFlight += amount
    }

    /** Lets go of [amount] held by [hold] that will never fly (the screen closed first). */
    fun release(amount: Int) {
        if (amount > 0) inFlight = (inFlight - amount).coerceAtLeast(0)
    }

    /**
     * Sends [amount] from [from] to the counter. [held] is how much of it [hold] already set aside.
     * With [calm] (reduce motion), or no counter on screen, it simply arrives.
     */
    fun send(scope: CoroutineScope, from: Offset, amount: Int, sfx: Sfx, calm: Boolean, held: Int = 0) {
        if (amount <= 0) return
        inFlight += amount - held
        if (calm || counterAt == Offset.Unspecified || from == Offset.Unspecified) {
            inFlight = (inFlight - amount).coerceAtLeast(0)
            if (!calm) sfx.play(Sound.Chime)
            landed += 1
            return
        }
        val count = amount.coerceIn(1, MAX_BUGS)
        val now = clock.seconds
        note = 1f
        for (i in 0 until count) {
            val share = amount / count + if (i < amount % count) 1 else 0
            val bug = Bug(from, now + i * STAGGER_S, if (i % 2 == 0) 1f else -1f, share)
            bugs += bug
            scope.launch {
                delay(((i * STAGGER_S + TRAVEL_S) * 1_000).toLong())
                bugs -= bug
                inFlight = (inFlight - share).coerceAtLeast(0)
                landed += 1
                sfx.play(Sound.Chime, volume = 0.5f, pitch = note)
                note = (note + 0.04f).coerceAtMost(1.4f)
            }
        }
    }

    /** Everything lands at once: leaving a screen mid-flight must never lose a firefly from view. */
    fun settle() {
        bugs.clear()
        inFlight = 0
    }

    /** Where [bug] is at clock time [t], or null before it leaves or after it lands. */
    fun place(bug: Bug, t: Float): Pair<Offset, Float>? {
        val p = (t - bug.leaves) / TRAVEL_S
        if (p < 0f || p > 1f || counterAt == Offset.Unspecified) return null
        val to = counterAt
        // A curve that swings out to one side and eases in to land.
        val e = 1f - (1f - p) * (1f - p) * (1f - p)
        val mid = Offset((bug.from.x + to.x) / 2f, (bug.from.y + to.y) / 2f)
        val dx = to.x - bug.from.x
        val dy = to.y - bug.from.y
        val control = mid + Offset(-dy, dx) * (0.35f * bug.bend)
        val a = (1 - e) * (1 - e)
        val b = 2 * (1 - e) * e
        val c = e * e
        return Offset(a * bug.from.x + b * control.x + c * to.x, a * bug.from.y + b * control.y + c * to.y) to p
    }
}

/** The fireflies in flight, over everything else. The clock is read only while drawing. */
@Composable
internal fun SwarmLayer(swarm: Swarm, clock: Clock) {
    if (swarm.bugs.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.seconds
        val w = 22.dp.toPx()
        for (bug in swarm.bugs) {
            val (at, p) = swarm.place(bug, t) ?: continue
            // A short trail of fading light behind it.
            for (k in 1..3) {
                val (back, _) = swarm.place(bug, t - k * 0.035f) ?: continue
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE27A).copy(alpha = 0.35f / k), Color.Transparent), back, w * 0.5f), w * 0.5f, back)
            }
            val flicker = 0.75f + 0.25f * sin(t * 18f + bug.leaves * 7f)
            val grow = 0.7f + 0.5f * sin(p * PI.toFloat())
            firefly(at, w * grow, lit = flicker)
        }
    }
}
