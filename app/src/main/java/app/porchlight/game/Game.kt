package app.porchlight.game

import kotlin.math.max
import kotlin.math.min

const val PAIR_POINTS = 100
const val SECOND_POINTS = 20
const val MAX_MULTIPLIER = 4
const val LOW_MS = 5_000L
const val FLASH_MS = 900L

enum class Phase { Idle, Peek, Play, Won, Lost }

/** The lead moth changes exactly one rule of the round. */
data class Rules(
    val roundMs: Long = 30_000,
    val peekMs: Long = 2_000,
    val flipBackMs: Long = 800,
    val streakStart: Int = 1,
    val pairBonusMs: Long = 0,
    val flashes: Int = 0,
)

fun rulesFor(lead: MothId): Rules = when (lead) {
    MothId.Wick -> Rules(roundMs = 34_000)
    MothId.Ember -> Rules(streakStart = 2)
    MothId.Atlas -> Rules(flashes = 1)
    MothId.Dot -> Rules(flipBackMs = 400)
    MothId.Iris -> Rules(peekMs = 3_400)
    MothId.Sorbet -> Rules(pairBonusMs = 1_500)
}

/** Atlas -> Atlases, Iris -> Irises, Wick -> Wicks. */
fun plural(name: String) = if (name.endsWith("s")) "${name}es" else "${name}s"

fun deal(): List<MothId> = MothId.entries.flatMap { listOf(it, it) }.shuffled()

fun multiplierAt(streak: Int, rules: Rules) = min(MAX_MULTIPLIER, rules.streakStart + streak)

data class Round(
    val phase: Phase = Phase.Idle,
    val deck: List<MothId> = deal(),
    val open: List<Int> = emptyList(),
    val matched: List<Boolean> = List(12) { false },
    val miss: Boolean = false,
    val flashing: Boolean = false,
    val flashes: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val pairs: Int = 0,
    val misses: Int = 0,
    val bestMultiplier: Int = 1,
    val endsAt: Long = 0,
    val left: Long = 30_000,
    val rules: Rules = Rules(),
    val say: String = "Pick a lead moth, then start the round.",
) {
    val running get() = phase == Phase.Peek || phase == Phase.Play
    val over get() = phase == Phase.Won || phase == Phase.Lost

    fun isFaceUp(index: Int) = phase != Phase.Idle &&
        (phase == Phase.Peek || phase == Phase.Lost || flashing || matched[index] || index in open)

    /** Peek is over: the clock starts. */
    fun go(now: Long) =
        if (phase != Phase.Peek) this
        else copy(phase = Phase.Play, endsAt = now + rules.roundMs, say = "Go. Find the pairs.")

    fun flip(index: Int, now: Long): Round {
        if (phase != Phase.Play || miss || flashing || matched[index] || index in open) return this
        val nowOpen = open + index
        val name = deck[index].label
        if (nowOpen.size < 2) return copy(open = nowOpen, say = "$name.")

        val (first, second) = nowOpen
        if (deck[first] != deck[second]) {
            return copy(open = nowOpen, miss = true, streak = 0, misses = misses + 1, say = "$name. No match.")
        }

        val nowMatched = matched.mapIndexed { i, done -> done || i == first || i == second }
        val multiplier = multiplierAt(streak, rules)
        val nowPairs = pairs + 1
        val nowEnds = endsAt + rules.pairBonusMs
        val nowLeft = max(0, nowEnds - now)
        val nowScore = score + PAIR_POINTS * multiplier
        val nowBest = max(bestMultiplier, multiplier)
        if (nowPairs < 6) {
            return copy(
                open = emptyList(), matched = nowMatched, pairs = nowPairs, endsAt = nowEnds, left = nowLeft,
                score = nowScore, streak = streak + 1, bestMultiplier = nowBest, say = "A pair of ${plural(name)}, times $multiplier.",
            )
        }
        val seconds = (nowLeft / 1000).toInt()
        val finalScore = nowScore + seconds * SECOND_POINTS
        return copy(
            phase = Phase.Won, open = emptyList(), matched = nowMatched, pairs = nowPairs, endsAt = nowEnds,
            left = nowLeft, score = finalScore, streak = streak + 1, bestMultiplier = nowBest,
            say = "All six pairs with $seconds seconds left.",
        )
    }

    /** A wrong pair folds back. */
    fun settle() = if (miss) copy(open = emptyList(), miss = false) else this

    fun tick(now: Long): Round {
        if (phase != Phase.Play) return this
        val nowLeft = endsAt - now
        if (nowLeft > 0) return copy(left = nowLeft)
        return copy(
            phase = Phase.Lost, left = 0, open = emptyList(), miss = false, flashing = false,
            say = "The bulb burned out. $pairs of 6 pairs.",
        )
    }

    fun flash() =
        if (phase != Phase.Play || flashes < 1 || miss || flashing) this
        else copy(flashing = true, flashes = flashes - 1, say = "Second look. Every card is open.")

    fun unflash() = if (flashing) copy(flashing = false) else this

    companion object {
        fun ready(lead: MothId): Round {
            val rules = rulesFor(lead)
            return Round(rules = rules, left = rules.roundMs, flashes = rules.flashes)
        }

        fun start(lead: MothId) = ready(lead).copy(
            phase = Phase.Peek,
            say = "Look closely. The cards are about to fold shut.",
        )
    }
}
