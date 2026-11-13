package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    /** A known deck: pairs sit side by side at (0,1), (2,3), ... (10,11). */
    private val sorted = MothId.entries.flatMap { listOf(it, it) }

    private fun playing(lead: MothId = MothId.Wick, now: Long = 0): Round =
        Round.start(lead).copy(deck = sorted).go(now)

    private fun Round.pair(first: Int, now: Long = 0) = flip(first, now).flip(first + 1, now)

    // Dealing

    @Test fun `deal has twelve cards with every moth exactly twice`() {
        val deck = deal()
        assertEquals(12, deck.size)
        MothId.entries.forEach { assertEquals(2, deck.count { card -> card == it }) }
    }

    @Test fun `deal shuffles`() {
        val decks = List(40) { deal() }.toSet()
        assertTrue("40 deals should not all come out the same", decks.size > 1)
    }

    // Lead moth perks

    @Test fun `wick adds four seconds`() = assertEquals(34_000L, rulesFor(MothId.Wick).roundMs)

    @Test fun `ember starts the streak at two`() = assertEquals(2, rulesFor(MothId.Ember).streakStart)

    @Test fun `atlas gets one second look`() = assertEquals(1, rulesFor(MothId.Atlas).flashes)

    @Test fun `dot halves the fold back`() = assertEquals(400L, rulesFor(MothId.Dot).flipBackMs)

    @Test fun `iris lengthens the peek`() = assertEquals(3_400L, rulesFor(MothId.Iris).peekMs)

    @Test fun `sorbet tops up the bulb per pair`() = assertEquals(1_500L, rulesFor(MothId.Sorbet).pairBonusMs)

    @Test fun `each lead changes exactly one rule`() {
        val base = Rules()
        MothId.entries.forEach { id ->
            val r = rulesFor(id)
            val changed = listOf(
                r.roundMs != base.roundMs, r.peekMs != base.peekMs, r.flipBackMs != base.flipBackMs,
                r.streakStart != base.streakStart, r.pairBonusMs != base.pairBonusMs, r.flashes != base.flashes,
            ).count { it }
            assertEquals("$id should change one rule", 1, changed)
        }
    }

    // Round flow

    @Test fun `a ready round is idle with a full bulb`() {
        val round = Round.ready(MothId.Wick)
        assertEquals(Phase.Idle, round.phase)
        assertEquals(34_000L, round.left)
        assertFalse(round.running)
    }

    @Test fun `starting opens the peek`() {
        val round = Round.start(MothId.Dot)
        assertEquals(Phase.Peek, round.phase)
        assertTrue(round.running)
        assertTrue((0 until 12).all { round.isFaceUp(it) })
    }

    @Test fun `go starts the clock from the peek`() {
        val round = Round.start(MothId.Dot).go(now = 5_000)
        assertEquals(Phase.Play, round.phase)
        assertEquals(35_000L, round.endsAt)
    }

    @Test fun `go does nothing outside the peek`() {
        val idle = Round.ready(MothId.Dot)
        assertSame(idle, idle.go(0))
    }

    @Test fun `cards cannot be flipped during the peek`() {
        val peek = Round.start(MothId.Dot)
        assertSame(peek, peek.flip(0, 0))
    }

    @Test fun `flipping one card opens it`() {
        val round = playing().flip(4, 0)
        assertEquals(listOf(4), round.open)
        assertTrue(round.isFaceUp(4))
        assertFalse(round.isFaceUp(5))
    }

    @Test fun `flipping the same card twice is ignored`() {
        val once = playing().flip(4, 0)
        assertSame(once, once.flip(4, 0))
    }

    // Matching and scoring

    @Test fun `a pair scores one hundred and stays open`() {
        val round = playing().pair(0)
        assertEquals(PAIR_POINTS, round.score)
        assertEquals(1, round.pairs)
        assertTrue(round.matched[0] && round.matched[1])
        assertTrue(round.open.isEmpty())
        assertTrue(round.isFaceUp(0) && round.isFaceUp(1))
    }

    @Test fun `matched cards cannot be flipped again`() {
        val round = playing().pair(0)
        assertSame(round, round.flip(0, 0))
    }

    @Test fun `pairs in a row multiply up to four`() {
        var round = playing()
        val gains = mutableListOf<Int>()
        for (first in listOf(0, 2, 4, 6, 8)) {
            val before = round.score
            round = round.pair(first)
            gains += round.score - before
        }
        assertEquals(listOf(100, 200, 300, 400, 400), gains)
        assertEquals(MAX_MULTIPLIER, round.bestMultiplier)
    }

    @Test fun `ember's first pair is already doubled`() {
        assertEquals(200, playing(MothId.Ember).pair(0).score)
    }

    @Test fun `a wrong pair resets the streak and counts a miss`() {
        val round = playing().pair(0).flip(2, 0).flip(4, 0)
        assertTrue(round.miss)
        assertEquals(0, round.streak)
        assertEquals(1, round.misses)
        assertEquals(listOf(2, 4), round.open)
    }

    @Test fun `no flips land while a wrong pair is showing`() {
        val miss = playing().flip(2, 0).flip(4, 0)
        assertSame(miss, miss.flip(6, 0))
    }

    @Test fun `settle folds a wrong pair back`() {
        val settled = playing().flip(2, 0).flip(4, 0).settle()
        assertFalse(settled.miss)
        assertTrue(settled.open.isEmpty())
        assertFalse(settled.isFaceUp(2))
    }

    @Test fun `streak multiplier restarts after a miss`() {
        val round = playing().pair(0).pair(2).flip(4, 0).flip(6, 0).settle().pair(4)
        assertEquals(100 + 200 + 100, round.score)
    }

    // Winning and losing

    @Test fun `finding all six pairs wins with a time bonus`() {
        var round = playing(now = 0)
        for (first in listOf(0, 2, 4, 6, 8)) round = round.pair(first, now = 1_000)
        round = round.pair(10, now = 10_000) // 24 s left of Wick's 34
        assertEquals(Phase.Won, round.phase)
        val pairs = 100 + 200 + 300 + 400 + 400 + 400
        assertEquals(pairs + 24 * SECOND_POINTS, round.score)
    }

    @Test fun `the clock ticks down`() {
        val round = playing(now = 0).tick(4_000)
        assertEquals(30_000L, round.left)
        assertEquals(Phase.Play, round.phase)
    }

    @Test fun `the bulb burning out loses the round`() {
        val round = playing(now = 0).pair(0).tick(34_000)
        assertEquals(Phase.Lost, round.phase)
        assertEquals(0L, round.left)
        assertEquals(1, round.pairs)
    }

    @Test fun `every card shows once the bulb burns out`() {
        val lost = playing(now = 0).tick(40_000)
        assertTrue((0 until 12).all { lost.isFaceUp(it) })
    }

    @Test fun `ticks outside play change nothing`() {
        val idle = Round.ready(MothId.Wick)
        assertSame(idle, idle.tick(99_000))
    }

    @Test fun `sorbet pushes the deadline back on every pair`() {
        val round = playing(MothId.Sorbet, now = 0).pair(0, now = 1_000)
        assertEquals(31_500L, round.endsAt)
    }

    // Second look (Atlas)

    @Test fun `second look opens every card once`() {
        val flashed = playing(MothId.Atlas).flash()
        assertTrue(flashed.flashing)
        assertEquals(0, flashed.flashes)
        assertTrue((0 until 12).all { flashed.isFaceUp(it) })
        val after = flashed.unflash()
        assertFalse(after.flashing)
        assertSame(after, after.flash())
    }

    @Test fun `other leads have no second look`() {
        val round = playing(MothId.Wick)
        assertSame(round, round.flash())
    }

    // Copy

    @Test fun `plural names read naturally`() {
        assertEquals("Atlases", plural("Atlas"))
        assertEquals("Irises", plural("Iris"))
        assertEquals("Wicks", plural("Wick"))
    }

    @Test fun `messages name the moth`() {
        assertEquals("A pair of Embers, times 1.", playing().pair(2).say)
        assertNotEquals(playing().say, playing().flip(0, 0).say)
    }
}
