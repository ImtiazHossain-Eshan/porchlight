package app.porchlight.game

import kotlin.math.pow
import kotlin.random.Random

/** How well a simulated player takes in the peek: a multiple of the base chance of remembering a card. */
enum class Skill(val factor: Double) { Casual(0.6), Average(1.0), Strong(1.3) }

/** One simulated round's outcome. */
data class SimGame(val won: Boolean, val stars: Int, val leftMs: Long)

/** Many simulated rounds of one level at one skill. */
data class SimStats(val games: Int, val wins: Int, val stars: Double, val leftMs: Double) {
    val winRate get() = 100.0 * wins / games
}

/**
 * A simulated player, for tuning the levels. It plays the real [Round] engine, so every twist
 * behaves exactly as it does in the game, and adds a simple memory on top:
 *
 * - During the peek it takes in each card with chance p = clamp(peek per card / 260 ms, 0.15, 0.95),
 *   times its [Skill]. The Shadow moth's cards can't be taken in.
 * - Every card it turns, it sees.
 * - It forgets 4% of what it knows every second.
 * - A turn takes [FLIP_MS] on average, anywhere from half to one and a half times that, and a wrong
 *   pair shows for the game's own fold-back time before the next card can turn. It always plays a
 *   pair it knows; otherwise it turns a card it doesn't know, then that card's partner if it knows
 *   it, or another unknown card.
 *
 * The turn time is 1.0 s, a little slower than the spec's 0.7 s so rounds are kinder to real
 * players. PORCH_FLIP_MS and PORCH_RECALL_TURNED (remember turned
 * cards only with chance p) override it for experiments.
 * - It steers clear of dead leaves it knows about.
 * - A Lookalike is taken for its twin 15% of the times it is seen.
 * - When the Wanderer swaps two cards, it loses track of both.
 *
 * The lead is a moth without a perk, so the level's own numbers are what is measured.
 */
object Simulator {
    val FLIP_MS = System.getenv("PORCH_FLIP_MS")?.toLongOrNull() ?: 1_000L
    private val recallTurned = System.getenv("PORCH_RECALL_TURNED") == "1"
    const val CARD_MS = 260.0
    const val FORGET_PER_SECOND = 0.04
    const val CONFUSE = 0.15
    private fun forgetting(ms: Long) = 1 - (1 - FORGET_PER_SECOND).pow(ms / 1000.0)
    private val twinOf: Map<MothId, MothId> = TWINS + TWINS.entries.associate { (twin, of) -> of to twin }

    fun recall(level: Level, skill: Skill): Double =
        ((level.peekMs.toDouble() / level.cards / CARD_MS).coerceIn(0.15, 0.95) * skill.factor).coerceAtMost(1.0)

    fun play(level: Level, skill: Skill, seed: Long): SimGame {
        val round = playRound(level, skill, seed)
        val won = round.phase == Phase.Won
        return SimGame(won, starsFor(round), if (won) round.left else 0)
    }

    /** One round played through by a simulated player, as the finished [Round] (for the economy sim). */
    fun playRound(level: Level, skill: Skill, seed: Long, easy: Boolean = false): Round {
        val random = Random(seed)
        var round = Round.start(MothId.Moss, level, Random(seed), seed, easy = easy).go(0)
        val n = round.deck.size
        val p = recall(level, skill)
        val lookalikes = Twist.Lookalikes in level
        // What the player believes lies at each place, or null if it doesn't know.
        val memory = arrayOfNulls<MothId>(n)

        fun perceive(id: MothId): MothId {
            val twin = twinOf[id]
            return if (lookalikes && twin != null && twin in round.deck && random.nextDouble() < CONFUSE) twin else id
        }

        val shadow = round.shadow
        for (i in 0 until n) if (round.deck[i] != shadow && random.nextDouble() < p) memory[i] = perceive(round.deck[i])

        var now = 0L
        fun live(i: Int) = !round.matched[i] && i !in round.spent

        fun turn(i: Int) {
            // A turn takes FLIP_MS on average, some quicker and some slower.
            val took = (FLIP_MS * (0.5 + random.nextDouble())).toLong()
            now += took
            val forget = forgetting(took)
            for (k in 0 until n) if (memory[k] != null && live(k) && random.nextDouble() < forget) memory[k] = null
            round = round.tick(now)
            if (round.phase != Phase.Play) return
            round = round.flip(i, now)
            if (!recallTurned || random.nextDouble() < p) memory[i] = perceive(round.deck[i])
        }

        fun unknown(except: Int = -1): Int? {
            val choices = (0 until n).filter { it != except && live(it) && memory[it] == null }
            return if (choices.isEmpty()) null else choices[random.nextInt(choices.size)]
        }

        fun anyCard(except: Int = -1): Int? {
            val choices = (0 until n).filter { it != except && live(it) && memory[it] != MothId.Leaf }
            return if (choices.isEmpty()) null else choices[random.nextInt(choices.size)]
        }

        fun knownPair(): Pair<Int, Int>? {
            val seen = HashMap<MothId, Int>()
            for (i in 0 until n) {
                val id = memory[i] ?: continue
                if (!live(i) || id == MothId.Leaf) continue
                val other = seen[id]
                if (other != null) return other to i
                seen[id] = i
            }
            return null
        }

        while (round.phase == Phase.Play) {
            val pair = knownPair()
            val first = pair?.first ?: unknown() ?: anyCard() ?: break
            turn(first)
            if (round.phase != Phase.Play) break
            if (first in round.spent) continue // a dead leaf: it costs its seconds, and the turn is over
            val belief = memory[first]
            val second = pair?.second
                ?: (0 until n).firstOrNull { it != first && live(it) && belief != null && memory[it] == belief }
                ?: unknown(except = first)
                ?: anyCard(except = first)
                ?: break
            turn(second)
            if (round.miss) {
                // The wrong pair shows until it folds back; no card turns meanwhile.
                now += round.rules.flipBackMs
                round = round.settle()
                round.swap?.let { (a, b) ->
                    memory[a] = null
                    memory[b] = null
                }
            }
        }
        return round.tick(now)
    }

    fun run(level: Level, skill: Skill, games: Int = 2_000, seed: Long = 1): SimStats {
        var wins = 0
        var stars = 0
        var left = 0L
        for (g in 0 until games) {
            val game = play(level, skill, seed * 1_000_003 + g * 7_919L + level.number)
            if (game.won) {
                wins++
                left += game.leftMs
            }
            stars += game.stars
        }
        return SimStats(games, wins, stars.toDouble() / games, if (wins > 0) left.toDouble() / wins else 0.0)
    }
}

/**
 * The win rate an average player should have on [level]: a straight line across each chapter's
 * normal levels, a breather six points easier, a boss eight points harder.
 */
fun targetWinRate(level: Level): Double {
    val (from, to) = when (level.chapter) {
        1 -> 98.0 to 94.0
        2 -> 94.0 to 88.0
        3 -> 90.0 to 80.0
        4 -> 86.0 to 74.0
        else -> 80.0 to 64.0
    }
    val i = (level.number - 1) % 10
    val base = from + (to - from) * i / 9.0
    return when (level.kind) {
        Kind.Normal -> base
        Kind.Breather -> (base + 6).coerceAtMost(99.0)
        Kind.Boss -> base - 8
    }
}
