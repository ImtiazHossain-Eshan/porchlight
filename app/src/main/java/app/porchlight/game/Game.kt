package app.porchlight.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val PAIR_POINTS = 100
const val SECOND_POINTS = 20
const val MAX_MULTIPLIER = 4
const val LOW_MS = 5_000L
const val FLASH_MS = 900L
const val DUD_MS = 2_000L

enum class Phase { Idle, Peek, Play, Won, Lost }

/** One rule a night can bend, introduced one at a time on later nights. */
enum class Twist(val title: String, val tip: String) {
    None("", ""),
    Wanderer("Wanderer", "After a wrong pair, two face-down cards swap places."),
    Leaves("Dead leaves", "Two cards are dead leaves. Turning one costs 2 seconds."),
    Shadow("Shadow", "One moth shows only as a silhouette during the peek."),
}

/**
 * A difficulty step. More pairs means more time, about five seconds a pair. Each night is unlocked
 * by beating the one before it with two stars or more.
 */
data class Night(
    val number: Int,
    val pairs: Int,
    val leaves: Int = 0,
    val twist: Twist = Twist.None,
    val roundMs: Long = pairs * 5_000L,
) {
    val cards get() = pairs * 2 + leaves
}

val NIGHTS = listOf(
    Night(1, pairs = 6),
    Night(2, pairs = 8),
    Night(3, pairs = 10),
    Night(4, pairs = 10, twist = Twist.Wanderer),
    Night(5, pairs = 9, leaves = 2, twist = Twist.Leaves, roundMs = 46_000),
    Night(6, pairs = 10, twist = Twist.Shadow),
)

/**
 * The night to play. Night 1 is generous for a first win; once the player has won it, its bulb
 * shortens to four seconds a pair so it stays worth playing.
 */
fun nightFor(number: Int, seasoned: Boolean): Night {
    val night = NIGHTS[(number - 1).coerceIn(0, NIGHTS.size - 1)]
    return if (night.number == 1 && seasoned) night.copy(roundMs = night.pairs * 4_000L) else night
}

/** The daily porch: the same deal for everyone today. */
val DAILY = Night(0, pairs = 8)

/** The first-launch tutorial: three pairs in a 3 × 2 grid, and a long bulb. */
val TUTORIAL = Night(-1, pairs = 3, roundMs = 20_000)

/** What a night brings that the player hasn't met: new moths, or a twist. */
fun newOn(night: Night): List<MothId> {
    val before = NIGHTS.getOrNull(night.number - 2)?.pairs ?: return emptyList()
    return if (night.pairs > before) BOARD_MOTHS.subList(before, night.pairs) else emptyList()
}

/** The lead moth changes the round's rules. */
data class Rules(
    val roundMs: Long = 30_000,
    val peekMs: Long = 2_000,
    val flipBackMs: Long = 800,
    val streakStart: Int = 1,
    val pairBonusMs: Long = 0,
    val flashes: Int = 0,
    /** A wrong pair folds back as soon as you turn the next card, instead of making you wait. */
    val tapThrough: Boolean = false,
    /** Wrong pairs forgiven before the streak breaks. */
    val freeMisses: Int = 0,
)

fun rulesFor(lead: MothId, night: Night = NIGHTS[0]): Rules {
    val base = Rules(roundMs = night.roundMs)
    if (night == TUTORIAL) return base.copy(peekMs = 3_000)
    return when (lead) {
        MothId.Luna -> base.copy(roundMs = base.roundMs + 5_000)
        MothId.Wick -> base.copy(streakStart = 2, roundMs = base.roundMs - 3_000)
        MothId.Atlas -> base.copy(flashes = 1)
        MothId.Dot -> base.copy(tapThrough = true, freeMisses = 1)
        MothId.Iris -> base.copy(peekMs = 3_400)
        MothId.Sorbet -> base.copy(pairBonusMs = 1_000)
        else -> base
    }
}

/** Atlas -> Atlases, Iris -> Irises, Wick -> Wicks. */
fun plural(name: String) = if (name.endsWith("s")) "${name}es" else "${name}s"

/** The moths that can appear on a board, in the order nights add them. */
val BOARD_MOTHS: List<MothId> = MothId.entries.filter { it != MothId.Leaf }

/** A shuffled deck for [night]. Pass a seeded [random] for a deal that must come out the same every time. */
fun deal(night: Night = NIGHTS[0], random: Random = Random.Default): List<MothId> {
    val moths = if (night == DAILY) BOARD_MOTHS.shuffled(random).take(night.pairs) else BOARD_MOTHS.take(night.pairs)
    return (moths.flatMap { listOf(it, it) } + List(night.leaves) { MothId.Leaf }).shuffled(random)
}

/** Today's seed, from the date: 2026-11-14 -> 20261114. */
fun dailySeed(year: Int, month: Int, day: Int): Long = year * 10_000L + month * 100L + day

fun multiplierAt(streak: Int, rules: Rules) = min(MAX_MULTIPLIER, rules.streakStart + streak)

data class Round(
    val phase: Phase = Phase.Idle,
    val night: Night = NIGHTS[0],
    val deck: List<MothId> = deal(night),
    val open: List<Int> = emptyList(),
    val matched: List<Boolean> = List(deck.size) { false },
    /** Dead leaves that have been turned: they stay face up. */
    val spent: Set<Int> = emptySet(),
    val miss: Boolean = false,
    val flashing: Boolean = false,
    val flashes: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val pairs: Int = 0,
    val misses: Int = 0,
    val forgiven: Int = 0,
    val bestMultiplier: Int = 1,
    val bestRun: Int = 0,
    val endsAt: Long = 0,
    val left: Long = 30_000,
    val paused: Boolean = false,
    val rules: Rules = Rules(),
    /** The Wanderer's last swap, and how many there have been, so the board can animate each one. */
    val swap: Pair<Int, Int>? = null,
    val swaps: Int = 0,
    val seed: Long = 0,
    val daily: Boolean = false,
    val say: String = "Pick a lead moth, then light the porch.",
) {
    val running get() = phase == Phase.Peek || phase == Phase.Play
    val over get() = phase == Phase.Won || phase == Phase.Lost
    val pairCount get() = night.pairs
    val tutorial get() = night == TUTORIAL

    /** The moth shown only as a silhouette during the peek, on a Shadow night. */
    val shadow: MothId? get() {
        if (night.twist != Twist.Shadow) return null
        // Picked from the round's seed, not from where the cards lie, so its place can't be learned.
        val moths = deck.filter { it != MothId.Leaf }.distinct().sorted()
        return moths.getOrNull(Math.floorMod(seed, moths.size.coerceAtLeast(1)))
    }

    fun isFaceUp(index: Int) = phase != Phase.Idle &&
        (phase == Phase.Peek || phase == Phase.Lost || flashing || matched[index] || index in open || index in spent)

    /** Peek is over: the clock starts. */
    fun go(now: Long) =
        if (phase != Phase.Peek) this
        else copy(phase = Phase.Play, endsAt = now + rules.roundMs, say = "Go. Find the pairs.")

    fun flip(index: Int, now: Long): Round {
        if (phase != Phase.Play || paused || flashing) return this
        if (miss) {
            // Dot taps straight through a wrong pair; everyone else waits for it to fold back.
            if (!rules.tapThrough || index in open) return this
            return settle().flip(index, now)
        }
        if (matched[index] || index in open || index in spent) return this

        if (deck[index] == MothId.Leaf) {
            val nowEnds = endsAt - DUD_MS
            return copy(spent = spent + index, endsAt = nowEnds, left = max(0, nowEnds - now), say = "A dead leaf. Two seconds gone.")
                .tick(now)
        }

        val nowOpen = open + index
        val name = deck[index].label
        if (nowOpen.size < 2) return copy(open = nowOpen, say = "$name.")

        val (first, second) = nowOpen
        if (deck[first] != deck[second]) {
            val free = forgiven < rules.freeMisses
            return copy(
                open = nowOpen,
                miss = true,
                streak = if (free) streak else 0,
                forgiven = if (free) forgiven + 1 else forgiven,
                misses = misses + 1,
                say = if (free) "$name. No match, but the streak holds." else "$name. No match.",
            )
        }

        val nowMatched = matched.mapIndexed { i, done -> done || i == first || i == second }
        val multiplier = multiplierAt(streak, rules)
        val nowPairs = pairs + 1
        val nowEnds = endsAt + rules.pairBonusMs
        val nowLeft = max(0, nowEnds - now)
        val nowScore = score + PAIR_POINTS * multiplier
        val nowBest = max(bestMultiplier, multiplier)
        val nowRun = max(bestRun, streak + 1)
        if (nowPairs < pairCount) {
            return copy(
                open = emptyList(), matched = nowMatched, pairs = nowPairs, endsAt = nowEnds, left = nowLeft,
                score = nowScore, streak = streak + 1, bestMultiplier = nowBest, bestRun = nowRun,
                say = "A pair of ${plural(name)}, times $multiplier.",
            )
        }
        val seconds = (nowLeft / 1000).toInt()
        return copy(
            phase = Phase.Won, open = emptyList(), matched = nowMatched, pairs = nowPairs, endsAt = nowEnds,
            left = nowLeft, score = nowScore + seconds * SECOND_POINTS, streak = streak + 1, bestMultiplier = nowBest,
            bestRun = nowRun, say = "Every pair with $seconds seconds left.",
        )
    }

    /** A wrong pair folds back. On a Wanderer night, two face-down cards then swap places. */
    fun settle(): Round {
        if (!miss) return this
        val folded = copy(open = emptyList(), miss = false)
        if (night.twist != Twist.Wanderer) return folded
        val down = deck.indices.filter { !matched[it] && it !in spent }
        if (down.size < 2) return folded
        val random = Random(seed * 31 + swaps)
        val i = random.nextInt(down.size)
        val a = down[i]
        val b = down[(i + 1 + random.nextInt(down.size - 1)) % down.size]
        val swapped = deck.toMutableList().apply {
            this[a] = deck[b]
            this[b] = deck[a]
        }
        return folded.copy(deck = swapped, swap = a to b, swaps = swaps + 1)
    }

    fun tick(now: Long): Round {
        if (phase != Phase.Play || paused) return this
        val nowLeft = endsAt - now
        if (nowLeft > 0) return copy(left = nowLeft)
        return copy(
            phase = Phase.Lost, left = 0, open = emptyList(), miss = false, flashing = false,
            say = "The bulb burned out. $pairs of $pairCount pairs.",
        )
    }

    /** Freezes the clock, for when the app goes to the background. */
    fun pause(now: Long): Round {
        if (phase != Phase.Play || paused) return this
        return copy(paused = true, left = max(0, endsAt - now), say = "Paused.")
    }

    /** Starts the clock again with the time that was left when it paused. */
    fun resume(now: Long): Round {
        if (!paused) return this
        return copy(paused = false, endsAt = now + left, say = "Back on.")
    }

    fun flash() =
        if (phase != Phase.Play || paused || flashes < 1 || miss || flashing) this
        else copy(flashing = true, flashes = flashes - 1, say = "Second look. Every card is open.")

    fun unflash() = if (flashing) copy(flashing = false) else this

    companion object {
        fun ready(lead: MothId, night: Night = NIGHTS[0], random: Random = Random.Default, seed: Long = random.nextLong()): Round {
            val rules = rulesFor(lead, night)
            return Round(
                night = night,
                deck = deal(night, random),
                rules = rules,
                left = rules.roundMs,
                flashes = rules.flashes,
                seed = seed,
                daily = night == DAILY,
            )
        }

        fun start(lead: MothId, night: Night = NIGHTS[0], random: Random = Random.Default, seed: Long = random.nextLong()) =
            ready(lead, night, random, seed).copy(phase = Phase.Peek, say = "Look closely. The cards are about to fold shut.")

        /** Today's porch: a deal seeded from the date, the same for every player. */
        fun daily(lead: MothId, seed: Long) = start(lead, DAILY, Random(seed), seed)
    }
}

/** Finds a night by number, including the daily porch (0) and the tutorial (-1). */
fun nightNumbered(number: Int): Night = when (number) {
    DAILY.number -> DAILY
    TUTORIAL.number -> TUTORIAL
    else -> NIGHTS.getOrElse(number - 1) { NIGHTS[0] }
}

/**
 * The round as plain values that fit in a saved-state Bundle, so a round survives the app being
 * killed in the background. Rules are not stored: they follow from the lead and the night.
 */
fun Round.saved(lead: MothId): Map<String, Any> = mapOf(
    "phase" to phase.name,
    "night" to night.number,
    "nightMs" to night.roundMs,
    "lead" to lead.name,
    "deck" to deck.joinToString(",") { it.name },
    "open" to open.toIntArray(),
    "matched" to matched.toBooleanArray(),
    "spent" to spent.toIntArray(),
    "miss" to miss,
    "flashes" to flashes,
    "score" to score,
    "streak" to streak,
    "pairs" to pairs,
    "misses" to misses,
    "forgiven" to forgiven,
    "bestMultiplier" to bestMultiplier,
    "bestRun" to bestRun,
    "left" to left,
    "swaps" to swaps,
    "seed" to seed,
    "daily" to daily,
)

/**
 * Rebuilds a saved round. A round that was in play comes back paused with the time it had left;
 * one caught in the peek starts its peek again. Returns null if the values don't make a round.
 */
fun restoreRound(values: Map<String, Any?>): Pair<Round, MothId>? = runCatching {
    val lead = MothId.valueOf(values["lead"] as String)
    val night = nightNumbered(values["night"] as Int).let { base ->
        val ms = values["nightMs"] as? Long
        if (ms != null && ms != base.roundMs) base.copy(roundMs = ms) else base
    }
    val deck = (values["deck"] as String).split(",").map { MothId.valueOf(it) }
    val phase = Phase.valueOf(values["phase"] as String)
    val rules = rulesFor(lead, night)
    val matched = (values["matched"] as BooleanArray).toList()
    require(matched.size == deck.size)
    val wasPlaying = phase == Phase.Play
    Round(
        phase = phase,
        night = night,
        deck = deck,
        // A wrong pair that was showing has folded back by the time the round returns.
        open = if (values["miss"] == true) emptyList() else (values["open"] as IntArray).toList(),
        matched = matched,
        spent = (values["spent"] as IntArray).toSet(),
        flashes = values["flashes"] as Int,
        score = values["score"] as Int,
        streak = values["streak"] as Int,
        pairs = values["pairs"] as Int,
        misses = values["misses"] as Int,
        forgiven = values["forgiven"] as Int,
        bestMultiplier = values["bestMultiplier"] as Int,
        bestRun = values["bestRun"] as Int,
        left = values["left"] as Long,
        paused = wasPlaying,
        rules = rules,
        swaps = values["swaps"] as Int,
        seed = values["seed"] as Long,
        daily = values["daily"] as Boolean,
        say = if (wasPlaying) "Paused." else "Look closely. The cards are about to fold shut.",
    ) to lead
}.getOrNull()

/** A lead's strengths as one to three pips each, for its showcase card. */
data class LeadStats(val time: Int, val look: Int, val streak: Int)

fun leadStats(lead: MothId): LeadStats {
    val base = Rules()
    val rules = rulesFor(lead)
    val time = when {
        rules.roundMs > base.roundMs || rules.pairBonusMs > 0 -> 3
        rules.tapThrough -> 2
        rules.roundMs < base.roundMs -> 1
        else -> 2
    }
    val look = if (rules.peekMs > base.peekMs || rules.flashes > 0) 3 else 1
    val streak = when {
        rules.streakStart > base.streakStart -> 3
        rules.freeMisses > 0 -> 2
        else -> 1
    }
    return LeadStats(time, look, streak)
}
