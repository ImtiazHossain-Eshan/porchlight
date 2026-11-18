package app.porchlight.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The difficulty curve, checked against the simulated player (see [Simulator]): 2,000 seeded
 * games per level for each skill. The numbers come out the same on every run.
 */
class LevelCurveTest {
    companion object {
        /** Every level at every skill, played once for the whole class. */
        val results: Map<Int, Map<Skill, SimStats>> by lazy {
            LEVELS.parallelStream().map { level -> level.number to Skill.entries.associateWith { Simulator.run(level, it) } }
                .toList().toMap()
        }

        fun average(n: Int) = results.getValue(n).getValue(Skill.Average).winRate
    }

    @Test fun `the average player hits each level's target within five points`() {
        // The first five levels are the way in: they must never be harder than their target, but
        // they may be easier, since every one gives at least two seconds to look.
        val off = LEVELS.filter {
            val d = average(it.number) - targetWinRate(it)
            if (it.number <= 5) d < -5.0 else kotlin.math.abs(d) > 5.0
        }
            .map { "level ${it.number}: ${"%.1f".format(average(it.number))}% (target ${"%.1f".format(targetWinRate(it))})" }
        assertTrue("off target: $off", off.isEmpty())
    }

    @Test fun `a casual player wins the first five levels nine times in ten`() {
        for (n in 1..5) {
            val casual = results.getValue(n).getValue(Skill.Casual).winRate
            assertTrue("level $n: casual wins $casual%", casual >= 90.0)
        }
    }

    @Test fun `a strong player wins the last light half to two thirds of the time`() {
        val strong = results.getValue(50).getValue(Skill.Strong).winRate
        assertTrue("level 50: strong wins $strong%", strong in 50.0..70.0)
    }

    @Test fun `better players always do better`() {
        for (level in LEVELS) {
            val (casual, average, strong) = Skill.entries.map { results.getValue(level.number).getValue(it).winRate }
            assertTrue("level ${level.number}: $casual / $average / $strong", casual <= average + 1 && average <= strong + 1)
        }
    }

    @Test fun `no normal level is more than twelve points harder than the one before`() {
        for (level in LEVELS.drop(1).filter { it.kind == Kind.Normal }) {
            val drop = average(level.number - 1) - average(level.number)
            assertTrue("level ${level.number} is ${"%.1f".format(drop)} points harder than level ${level.number - 1}", drop <= 12.0)
        }
    }

    @Test fun `the rolling five-level average never eases by more than five points, except after a boss`() {
        val rolling = (5..LEVELS.size).map { end -> (end - 4..end).map { average(it) }.average() }
        for (i in 1 until rolling.size) {
            val end = i + 5 // the window's newest level
            val afterBoss = levelAt(end - 1).boss
            val eased = rolling[i] - rolling[i - 1]
            assertTrue("levels ${end - 4}-$end are ${"%.1f".format(eased)} points easier than the window before", afterBoss || eased <= 5.0)
        }
    }

    @Test fun `breathers are easier than their neighbours and bosses harder`() {
        for (level in LEVELS) {
            val neighbours = listOfNotNull(LEVELS.getOrNull(level.number - 2), LEVELS.getOrNull(level.number))
                .filter { it.kind == Kind.Normal }.map { average(it.number) }.average()
            when (level.kind) {
                // Where the neighbours are already near-certain wins, a breather at 98% or more is easy enough.
                Kind.Breather -> assertTrue("breather ${level.number}", average(level.number) >= minOf(neighbours + 4, 98.0))
                Kind.Boss -> assertTrue("boss ${level.number}", average(level.number) <= average(level.number - 1) - 4)
                Kind.Normal -> Unit
            }
        }
    }
}
