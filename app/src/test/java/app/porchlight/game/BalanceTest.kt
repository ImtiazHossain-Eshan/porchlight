package app.porchlight.game

import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

/**
 * Plays a simple simulated player with every lead and checks that no lead is the best choice on
 * both score and time by a wide margin.
 */
class BalanceTest {
    private class Outcome(val score: Double, val left: Double)

    /**
     * One round. Each card turn takes [turnMs]. A miss happens with chance [missRate]; a longer peek
     * makes misses rarer, and Atlas uses Second look after the first miss, which halves them after.
     */
    private fun play(lead: MothId, missRate: Double, random: Random, turnMs: Long = 600): Round {
        val deck = BOARD_MOTHS.take(6).flatMap { listOf(it, it) }
        var now = 0L
        var round = Round.start(lead).copy(deck = deck).go(now)
        var rate = missRate * (Rules().peekMs.toDouble() / round.rules.peekMs)
        var next = 0
        while (round.phase == Phase.Play) {
            val unmatched = deck.indices.filter { !round.matched[it] }
            if (unmatched.size > 2 && random.nextDouble() < rate) {
                val a = unmatched[0]
                val b = unmatched.first { deck[it] != deck[a] }
                now += turnMs
                round = round.flip(a, now)
                now += turnMs
                round = round.flip(b, now)
                if (!round.rules.tapThrough) now += round.rules.flipBackMs
                round = round.settle()
                if (round.flashes > 0) {
                    round = round.flash().unflash()
                    rate /= 2
                }
            } else {
                val a = unmatched[0]
                val b = unmatched.first { it != a && deck[it] == deck[a] }
                now += turnMs
                round = round.flip(a, now)
                now += turnMs
                round = round.flip(b, now)
            }
            round = round.tick(now)
            next++
        }
        return round
    }

    private fun average(lead: MothId, missRate: Double): Outcome {
        val random = Random(42)
        val rounds = List(400) { play(lead, missRate, random) }
        return Outcome(rounds.map { it.score }.average(), rounds.map { it.left }.average())
    }

    private fun check(missRate: Double) {
        val outcomes = LEADS.associateWith { average(it, missRate) }
        for ((lead, mine) in outcomes) {
            val others = outcomes.filterKeys { it != lead }.values
            val score = others.map { it.score }.average()
            val left = others.map { it.left }.average()
            val wins = mine.score > score * 1.15 && mine.left > left * 1.15
            assertFalse(
                "$lead leads on both score (${mine.score.toInt()} vs ${score.toInt()}) and time (${mine.left.toInt()} vs ${left.toInt()} ms)",
                wins,
            )
        }
    }

    @Test fun `no lead dominates for a perfect player`() = check(0.0)

    @Test fun `no lead dominates for a typical player`() = check(0.3)
}
