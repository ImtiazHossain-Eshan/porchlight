package app.porchlight.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val PAIR_POINTS = 100
const val SECOND_POINTS = 20
const val MAX_MULTIPLIER = 4
const val LOW_MS = 5_000L
const val FLASH_MS = 1_500L
const val DUD_MS = 2_000L

enum class Phase { Idle, Peek, Play, Won, Lost }

/** One rule a level can bend. Each is introduced alone, on a small board, before it is mixed with others. */
enum class Twist(val title: String, val tip: String) {
    Wanderer("Wanderer", "After a wrong pair, two face-down cards swap places."),
    Leaves("Dead leaves", "Some cards are dead leaves. Turning one costs 2 seconds."),
    Shadow("Shadow", "One moth shows only as a silhouette during the peek."),
    Lookalikes("Lookalikes", "Two moths are near-twins. Check the hindwings."),
}

/** A normal level, a gentler one to catch your breath, or the chapter's boss. */
enum class Kind { Normal, Breather, Boss }

/** Five chapters of ten levels. A chapter opens once the player holds [gate] stars in all. */
data class Chapter(val number: Int, val name: String, val scene: String, val gate: Int) {
    val levels get() = LEVELS.filter { it.chapter == number }
    val boss get() = levels.last()
}

val CHAPTERS = listOf(
    Chapter(1, "First Light", "The porch", gate = 0),
    Chapter(2, "Wanderers", "The garden", gate = 15),
    Chapter(3, "Dead Leaves", "The woods", gate = 35),
    Chapter(4, "Shadows", "The rooftops", gate = 55),
    Chapter(5, "Last Light", "The last porch", gate = 80),
)

/** The board sizes there are: cards in all, and the grid each one is laid out in. */
val GRIDS = mapOf(6 to (3 to 2), 8 to (4 to 2), 12 to (4 to 3), 16 to (4 to 4), 20 to (4 to 5), 24 to (4 to 6))

/**
 * One step on the map. [peekMs] is how long the opening peek lasts before a lead's perks;
 * [maxMissesFor3Stars] is how many wrong pairs a three-star win may have.
 */
data class Level(
    val number: Int,
    val chapter: Int,
    val pairs: Int,
    val leaves: Int = 0,
    val twists: Set<Twist> = emptySet(),
    val roundMs: Long = pairs * 5_000L,
    val peekMs: Long = 2_000,
    val maxMissesFor3Stars: Int = 0,
    val kind: Kind = Kind.Normal,
) {
    val cards get() = pairs * 2 + leaves
    val boss get() = kind == Kind.Boss
    val breather get() = kind == Kind.Breather
    operator fun contains(twist: Twist) = twist in twists
}

private val N = Kind.Normal
private val BREATHER = Kind.Breather
private val BOSS = Kind.Boss
private val WANDERER = Twist.Wanderer
private val LEAVES = Twist.Leaves
private val SHADOW = Twist.Shadow
private val LOOKALIKES = Twist.Lookalikes

/** Wrong pairs a three-star win may have, by chapter; a boss allows one fewer. */
private val THREE_STAR_MISSES = listOf(2, 2, 3, 3, 4)

private fun level(number: Int, pairs: Int, leaves: Int, seconds: Int, peek: Double, kind: Kind, vararg twists: Twist): Level {
    val chapter = (number - 1) / 10 + 1
    return Level(
        number = number,
        chapter = chapter,
        pairs = pairs,
        leaves = leaves,
        twists = twists.toSet(),
        roundMs = seconds * 1_000L,
        peekMs = (peek * 1_000).toLong(),
        maxMissesFor3Stars = THREE_STAR_MISSES[chapter - 1] - if (kind == Kind.Boss) 1 else 0,
        kind = kind,
    )
}

/**
 * The fifty levels, tuned against a simulated player (see LevelCurveTest and docs/LEVELS.md).
 *           level pairs leaves  s   peek  kind      twists
 */
val LEVELS: List<Level> = listOf(
    // Chapter 1: First Light, the porch.
    level(1, 3, 0, 10, 2.0, N),
    level(2, 4, 0, 13, 2.0, N),
    level(3, 4, 0, 12, 2.0, N),
    level(4, 6, 0, 22, 2.0, N),
    level(5, 6, 0, 22, 2.0, N),
    level(6, 6, 0, 15, 3.2, N),
    level(7, 6, 0, 17, 3.3, BREATHER),
    level(8, 8, 0, 22, 3.5, N),
    level(9, 8, 0, 22, 3.3, N),
    level(10, 8, 0, 22, 3.0, BOSS),
    // Chapter 2: Wanderers, the garden.
    level(11, 6, 0, 17, 2.6, N, WANDERER),
    level(12, 6, 0, 17, 2.6, N, WANDERER),
    level(13, 8, 0, 23, 3.0, N),
    level(14, 8, 0, 27, 3.0, N, WANDERER),
    level(15, 8, 0, 28, 2.8, N, WANDERER),
    level(16, 10, 0, 32, 3.2, N),
    level(17, 8, 0, 24, 3.0, BREATHER),
    level(18, 10, 0, 38, 3.0, N, WANDERER),
    level(19, 10, 0, 40, 2.8, N, WANDERER),
    level(20, 10, 0, 37, 2.8, BOSS, WANDERER),
    // Chapter 3: Dead Leaves, the woods.
    level(21, 5, 2, 14, 3.0, N, LEAVES),
    level(22, 7, 2, 23, 3.0, N, LEAVES),
    level(23, 7, 2, 24, 2.8, N, LEAVES),
    level(24, 10, 0, 39, 2.8, N, WANDERER),
    level(25, 9, 2, 34, 2.8, N, LEAVES),
    level(26, 9, 2, 34, 2.7, N, LEAVES),
    level(27, 8, 0, 24, 2.6, BREATHER),
    level(28, 9, 2, 40, 2.6, N, LEAVES, WANDERER),
    level(29, 8, 4, 35, 2.6, N, LEAVES),
    level(30, 9, 2, 37, 2.5, BOSS, LEAVES, WANDERER),
    // Chapter 4: Shadows, the rooftops.
    level(31, 8, 0, 25, 2.6, N, SHADOW),
    level(32, 10, 0, 34, 2.6, N, SHADOW),
    level(33, 10, 0, 34, 2.5, N, SHADOW),
    level(34, 8, 0, 26, 2.5, N, LOOKALIKES),
    level(35, 10, 0, 35, 2.5, N, LOOKALIKES),
    level(36, 12, 0, 42, 2.6, N),
    level(37, 10, 0, 33, 2.4, BREATHER),
    level(38, 12, 0, 43, 2.4, N, SHADOW),
    level(39, 12, 0, 53, 2.3, N, SHADOW, WANDERER),
    level(40, 12, 0, 44, 2.2, BOSS, SHADOW, LOOKALIKES),
    // Chapter 5: Last Light, the last porch.
    level(41, 8, 0, 29, 2.4, N, SHADOW, WANDERER),
    level(42, 10, 0, 43, 2.3, N, LOOKALIKES, WANDERER),
    level(43, 11, 2, 44, 2.3, N, LEAVES),
    level(44, 12, 0, 46, 2.2, N, SHADOW, LOOKALIKES),
    level(45, 11, 2, 52, 2.2, N, LEAVES, WANDERER),
    level(46, 12, 0, 53, 2.1, N, SHADOW, WANDERER),
    level(47, 10, 0, 34, 2.1, BREATHER),
    level(48, 12, 0, 55, 2.0, N, LOOKALIKES, WANDERER),
    level(49, 11, 2, 45, 2.0, N, LEAVES, SHADOW),
    level(50, 12, 0, 42, 3.8, BOSS, WANDERER, SHADOW, LOOKALIKES),
)

/** The level to play by number, kept in range. */
fun levelAt(number: Int): Level = LEVELS[(number - 1).coerceIn(0, LEVELS.size - 1)]

fun chapterOf(level: Level): Chapter = CHAPTERS[level.chapter - 1]

/** The daily porch: the same deal for everyone today. */
val DAILY = Level(0, chapter = 0, pairs = 8)

/** The first-launch tutorial: three pairs in a 3 × 2 grid, and a long bulb. */
val TUTORIAL = Level(-1, chapter = 0, pairs = 3, roundMs = 20_000, peekMs = 3_000)

/** What a level brings that the player hasn't met: new moths. */
fun newOn(level: Level): List<MothId> {
    if (level.number < 2) return emptyList()
    val before = LEVELS.take(level.number - 1).maxOf { it.pairs }
    return if (level.pairs > before) BOARD_MOTHS.subList(before, level.pairs) else emptyList()
}

/** The twist a level is the first to bring, if any: that level shows its tip before the peek. */
fun introducedOn(level: Level): Twist? {
    if (level.number < 1) return null
    val seen = LEVELS.take(level.number - 1).flatMap { it.twists }.toSet()
    return level.twists.firstOrNull { it !in seen }
}

/** The "Easier bulb" retry offered after three losses in a row: this much more time, and one star at most. */
const val EASIER_BULB = 1.2

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

/** Iris's longer look, on top of the level's own peek. */
const val IRIS_PEEK_MS = 1_400L

fun rulesFor(lead: MothId, level: Level = LEVELS[0], easy: Boolean = false): Rules {
    val ms = if (easy) (level.roundMs * EASIER_BULB).toLong() else level.roundMs
    val base = Rules(roundMs = ms, peekMs = level.peekMs)
    if (level == TUTORIAL) return base
    return when (lead) {
        MothId.Luna -> base.copy(roundMs = base.roundMs + 5_000)
        MothId.Wick -> base.copy(streakStart = 2, roundMs = base.roundMs - 3_000)
        MothId.Atlas -> base.copy(flashes = 1)
        MothId.Dot -> base.copy(tapThrough = true, freeMisses = 1)
        MothId.Iris -> base.copy(peekMs = base.peekMs + IRIS_PEEK_MS)
        MothId.Sorbet -> base.copy(pairBonusMs = 1_000)
        else -> base
    }
}

/** Atlas -> Atlases, Iris -> Irises, Wick -> Wicks. */
fun plural(name: String) = if (name.endsWith("s")) "${name}es" else "${name}s"

/** The moths that can appear on a board, in the order levels add them. */
val BOARD_MOTHS: List<MothId> = MothId.entries.filter { it.board }

/** The daily porch draws from the ten moths it always has, so a date deals what it always dealt. */
val DAILY_MOTHS: List<MothId> = BOARD_MOTHS.take(10)

/** Each lookalike and the moth it is a near-twin of. */
val TWINS: Map<MothId, MothId> = mapOf(MothId.Domino to MothId.Dot, MothId.Lilac to MothId.Iris)

/** A shuffled deck for [level]. Pass a seeded [random] for a deal that must come out the same every time. */
fun deal(level: Level = LEVELS[0], random: Random = Random.Default): List<MothId> {
    var moths = if (level == DAILY) DAILY_MOTHS.shuffled(random).take(level.pairs) else BOARD_MOTHS.take(level.pairs)
    if (Twist.Lookalikes in level) {
        // The last moth on the board gives way to a near-twin of one already there.
        val twins = TWINS.filter { (_, of) -> of in moths }.keys.toList()
        if (twins.isNotEmpty()) moths = moths.dropLast(1) + twins[random.nextInt(twins.size)]
    }
    return (moths.flatMap { listOf(it, it) } + List(level.leaves) { MothId.Leaf }).shuffled(random)
}

/** Today's seed, from the date: 2026-11-14 -> 20261114. */
fun dailySeed(year: Int, month: Int, day: Int): Long = year * 10_000L + month * 100L + day

fun multiplierAt(streak: Int, rules: Rules) = min(MAX_MULTIPLIER, rules.streakStart + streak)

/**
 * One turned pair, in the order it happened: a match (which moth, at what multiplier) or a miss.
 * It never says where the cards lay, so a daily porch can be shared without spoiling the deal.
 */
sealed interface LogEntry {
    data class Match(val moth: MothId, val multiplier: Int) : LogEntry
    data object Miss : LogEntry
}

/** "Luna*2,-,Atlas*3": a log as one string, for saved state and storage. */
fun encodeLog(log: List<LogEntry>): String = log.joinToString(",") {
    when (it) {
        is LogEntry.Match -> "${it.moth.name}*${it.multiplier}"
        LogEntry.Miss -> "-"
    }
}

/** Reads [encodeLog]'s string back. Anything it can't read is dropped, never thrown. */
fun decodeLog(text: String?): List<LogEntry> =
    text.orEmpty().split(",").mapNotNull { part ->
        if (part == "-") return@mapNotNull LogEntry.Miss
        val bits = part.split("*")
        val moth = MothId.entries.find { it.name == bits.getOrNull(0) } ?: return@mapNotNull null
        val multiplier = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        LogEntry.Match(moth, multiplier)
    }

/** The round's story for the share card: matches and misses in order, and nothing about positions. */
fun flightLog(round: Round): List<LogEntry> = round.history.toList()

data class Round(
    val phase: Phase = Phase.Idle,
    val level: Level = LEVELS[0],
    val deck: List<MothId> = deal(level),
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
    /** The "Easier bulb" retry: a longer bulb, and one star at most. */
    val easy: Boolean = false,
    /** Every turned pair so far, in order. */
    val history: List<LogEntry> = emptyList(),
    val say: String = "Pick a lead moth, then light the porch.",
) {
    val running get() = phase == Phase.Peek || phase == Phase.Play
    val over get() = phase == Phase.Won || phase == Phase.Lost
    val pairCount get() = level.pairs
    val tutorial get() = level == TUTORIAL

    /** The moth shown only as a silhouette during the peek, on a Shadow level. */
    val shadow: MothId? get() {
        if (Twist.Shadow !in level) return null
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
                history = history + LogEntry.Miss,
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
        val nowHistory = history + LogEntry.Match(deck[first], multiplier)
        if (nowPairs < pairCount) {
            return copy(
                open = emptyList(), matched = nowMatched, pairs = nowPairs, endsAt = nowEnds, left = nowLeft,
                score = nowScore, streak = streak + 1, bestMultiplier = nowBest, bestRun = nowRun, history = nowHistory,
                say = "A pair of ${plural(name)}, times $multiplier.",
            )
        }
        val seconds = (nowLeft / 1000).toInt()
        return copy(
            phase = Phase.Won, open = emptyList(), matched = nowMatched, pairs = nowPairs, endsAt = nowEnds,
            left = nowLeft, score = nowScore + seconds * SECOND_POINTS, streak = streak + 1, bestMultiplier = nowBest,
            bestRun = nowRun, history = nowHistory, say = "Every pair with $seconds seconds left.",
        )
    }

    /** A wrong pair folds back. On a Wanderer level, two face-down cards then swap places. */
    fun settle(): Round {
        if (!miss) return this
        val folded = copy(open = emptyList(), miss = false)
        if (Twist.Wanderer !in level) return folded
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
        fun ready(lead: MothId, level: Level = LEVELS[0], random: Random = Random.Default, seed: Long = random.nextLong(), easy: Boolean = false): Round {
            val rules = rulesFor(lead, level, easy)
            return Round(
                level = level,
                deck = deal(level, random),
                rules = rules,
                left = rules.roundMs,
                flashes = rules.flashes,
                seed = seed,
                daily = level == DAILY,
                easy = easy,
            )
        }

        fun start(lead: MothId, level: Level = LEVELS[0], random: Random = Random.Default, seed: Long = random.nextLong(), easy: Boolean = false) =
            ready(lead, level, random, seed, easy).copy(phase = Phase.Peek, say = "Look closely. The cards are about to fold shut.")

        /** Today's porch: a deal seeded from the date, the same for every player. */
        fun daily(lead: MothId, seed: Long) = start(lead, DAILY, Random(seed), seed)
    }
}

/** Finds a level by number, including the daily porch (0) and the tutorial (-1). */
fun levelNumbered(number: Int): Level = when (number) {
    DAILY.number -> DAILY
    TUTORIAL.number -> TUTORIAL
    else -> levelAt(number)
}

/**
 * The round as plain values that fit in a saved-state Bundle, so a round survives the app being
 * killed in the background. Rules are not stored: they follow from the lead and the level.
 */
fun Round.saved(lead: MothId): Map<String, Any> = mapOf(
    "phase" to phase.name,
    "level" to level.number,
    "easy" to easy,
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
    "history" to encodeLog(history),
)

/**
 * Rebuilds a saved round. A round that was in play comes back paused with the time it had left;
 * one caught in the peek starts its peek again. Returns null if the values don't make a round.
 */
fun restoreRound(values: Map<String, Any?>): Pair<Round, MothId>? = runCatching {
    val lead = MothId.valueOf(values["lead"] as String)
    val level = levelNumbered(values["level"] as Int)
    val easy = values["easy"] as? Boolean ?: false
    val deck = (values["deck"] as String).split(",").map { MothId.valueOf(it) }
    val phase = Phase.valueOf(values["phase"] as String)
    val rules = rulesFor(lead, level, easy)
    val matched = (values["matched"] as BooleanArray).toList()
    require(matched.size == deck.size)
    val wasPlaying = phase == Phase.Play
    Round(
        phase = phase,
        level = level,
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
        easy = easy,
        history = decodeLog(values["history"] as? String),
        say = if (wasPlaying) "Paused." else "Look closely. The cards are about to fold shut.",
    ) to lead
}.getOrNull()

/** A lead's strengths as one to three pips each, for its showcase card. */
data class LeadStats(val time: Int, val look: Int, val streak: Int)

fun leadStats(lead: MothId): LeadStats {
    val base = Rules(roundMs = LEVELS[0].roundMs, peekMs = LEVELS[0].peekMs)
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
