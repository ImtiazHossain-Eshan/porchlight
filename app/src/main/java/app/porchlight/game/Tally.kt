package app.porchlight.game

import java.util.Locale

/**
 * What the Results tally says about a round, as plain values: pure, so it can be tested without a
 * screen. Only lines that say something are listed, and the points on them add up to the score.
 */
enum class TallyKind { Pairs, Streak, Time, Misses }

data class TallyLine(val kind: TallyKind, val text: String, val points: Int)

/** The seconds bonus a round earned: only a win, for the whole seconds left on the bulb. */
fun timeBonus(round: Round): Int = if (round.phase == Phase.Won) (round.left / 1000).toInt() * SECOND_POINTS else 0

/**
 * The tally, in the order it builds the score: the pairs (with their points), the best streak if it
 * reached ×2, the time left on a win (with its bonus), and the misses if there were any. A line with
 * nothing to say is left out: no "Time left none +0", no "Wrong pairs 0".
 */
fun tally(round: Round): List<TallyLine> = buildList {
    val bonus = timeBonus(round)
    if (round.pairs > 0) {
        add(TallyLine(TallyKind.Pairs, if (round.pairs == 1) "1 pair" else "${round.pairs} pairs", round.score - bonus))
    }
    if (round.bestMultiplier >= 2) add(TallyLine(TallyKind.Streak, "Best streak ×${round.bestMultiplier}", 0))
    if (bonus > 0) add(TallyLine(TallyKind.Time, "${round.left / 1000} s left", bonus))
    if (round.misses > 0) add(TallyLine(TallyKind.Misses, if (round.misses == 1) "1 miss" else "${round.misses} misses", 0))
}

/** Tips for a round that ended far from the last pair, one picked per round. */
val LOSS_TIPS = listOf(
    "Start with the corners.",
    "Watch the pairs you've already seen.",
    "Say each moth's name as it turns.",
    "Turn a card you know second, not first.",
)

/**
 * The line under a lost round's headline: "So close. 2 pairs to go." with one or two pairs left,
 * otherwise a tip, chosen from the round's seed so it stays put on a rotation. Null for a win.
 */
fun lossNote(round: Round): String? {
    if (round.phase != Phase.Lost) return null
    val left = round.pairCount - round.pairs
    return when (left) {
        1 -> "So close. 1 pair to go."
        2 -> "So close. 2 pairs to go."
        else -> LOSS_TIPS[Math.floorMod(round.seed, LOSS_TIPS.size.toLong()).toInt()]
    }
}

/** What earns star [index] (0, 1, 2), for the hint under an empty one. Mirrors [starsFor]. */
fun starHint(index: Int, round: Round): String = when (index) {
    0 -> "Find every pair"
    1 -> if (round.daily) String.format(Locale.US, "%d s left", (round.level.roundMs / 3 + 999) / 1000)
        else String.format(Locale.US, "%d s left", (round.level.roundMs / 4 + 999) / 1000)
    else -> when {
        round.daily || round.level.maxMissesFor3Stars == 0 -> "No misses"
        round.level.maxMissesFor3Stars == 1 -> "1 miss at most"
        else -> "${round.level.maxMissesFor3Stars} misses at most"
    }
}
