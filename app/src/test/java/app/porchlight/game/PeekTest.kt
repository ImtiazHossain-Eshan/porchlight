package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Test

class PeekTest {
    /** What the countdown shows at each 100 ms step of a peek of [peekMs]. */
    private fun shown(peekMs: Long) = (peekMs downTo 1 step 100).map { peekNumber(it) }

    @Test fun `a two second peek counts 2 then 1`() {
        assertEquals(listOf(2, 1), peekCountdown(2_000))
        assertEquals(2, peekNumber(2_000))
        assertEquals(2, peekNumber(1_001))
        assertEquals(1, peekNumber(1_000))
        assertEquals(1, peekNumber(1))
        assertEquals(0, peekNumber(0))
    }

    @Test fun `iris's 3_4 second peek counts 4, 3, 2, 1`() {
        assertEquals(listOf(4, 3, 2, 1), peekCountdown(3_400))
        assertEquals(4, peekNumber(3_400))
        assertEquals(4, peekNumber(3_001))
        assertEquals(3, peekNumber(3_000))
    }

    @Test fun `the number is always the whole seconds left, rounded up`() {
        for (peek in listOf(2_000L, 3_400L)) {
            for (left in peek downTo 1) {
                assertEquals("at $left ms", Math.ceil(left / 1000.0).toInt(), peekNumber(left))
            }
        }
    }

    @Test fun `the numbers shown through a peek are the countdown, in order, each once`() {
        assertEquals(peekCountdown(2_000), shown(2_000).distinct())
        assertEquals(peekCountdown(3_400), shown(3_400).distinct())
    }

    @Test fun `every lead's peek starts on its own whole seconds`() {
        LEADS.forEach { lead ->
            val peek = rulesFor(lead).peekMs
            assertEquals(Math.ceil(peek / 1000.0).toInt(), peekCountdown(peek).first())
        }
    }

    @Test fun `the opening is screen timing only and leaves the round's rules alone`() {
        val round = Round.start(MothId.Iris)
        assertEquals(Phase.Peek, round.phase)
        assertEquals(3_400L, round.rules.peekMs)
        assertEquals(round.rules.roundMs, round.left)
    }
}
