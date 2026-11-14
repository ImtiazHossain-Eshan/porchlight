package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {
    /** A known deck: pairs sit side by side at (0,1), (2,3), ... (10,11). */
    private val sorted = LEADS.flatMap { listOf(it, it) }

    private fun playing(lead: MothId = MothId.Wick, now: Long = 0): Round =
        Round.start(lead).copy(deck = sorted).go(now)

    private fun Round.pair(first: Int, now: Long = 0) = flip(first, now).flip(first + 1, now)

    // Dealing

    @Test fun `deal has twelve cards with every moth exactly twice`() {
        val deck = deal()
        assertEquals(12, deck.size)
        LEADS.forEach { assertEquals(2, deck.count { card -> card == it }) }
    }

    @Test fun `deal shuffles`() {
        val decks = List(40) { deal() }.toSet()
        assertTrue("40 deals should not all come out the same", decks.size > 1)
    }

    // Lead moth perks

    @Test fun `wick adds five seconds`() = assertEquals(35_000L, rulesFor(MothId.Wick).roundMs)

    @Test fun `ember starts the streak at two on a shorter bulb`() {
        assertEquals(2, rulesFor(MothId.Ember).streakStart)
        assertEquals(27_000L, rulesFor(MothId.Ember).roundMs)
    }

    @Test fun `atlas gets one second look`() = assertEquals(1, rulesFor(MothId.Atlas).flashes)

    @Test fun `dot taps through and forgives one miss`() {
        assertTrue(rulesFor(MothId.Dot).tapThrough)
        assertEquals(1, rulesFor(MothId.Dot).freeMisses)
    }

    @Test fun `iris lengthens the peek`() = assertEquals(3_400L, rulesFor(MothId.Iris).peekMs)

    @Test fun `sorbet tops up the bulb per pair`() = assertEquals(1_000L, rulesFor(MothId.Sorbet).pairBonusMs)

    @Test fun `every lead bends the rules`() {
        LEADS.forEach { assertNotEquals("$it should change something", Rules(), rulesFor(it)) }
    }

    @Test fun `leads add to the night's own time`() {
        assertEquals(NIGHTS[2].roundMs + 5_000, rulesFor(MothId.Wick, NIGHTS[2]).roundMs)
    }

    // Round flow

    @Test fun `a ready round is idle with a full bulb`() {
        val round = Round.ready(MothId.Wick)
        assertEquals(Phase.Idle, round.phase)
        assertEquals(35_000L, round.left)
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
        round = round.pair(10, now = 10_000) // 25 s left of Wick's 35
        assertEquals(Phase.Won, round.phase)
        val pairs = 100 + 200 + 300 + 400 + 400 + 400
        assertEquals(pairs + 25 * SECOND_POINTS, round.score)
    }

    @Test fun `the clock ticks down`() {
        val round = playing(now = 0).tick(4_000)
        assertEquals(31_000L, round.left)
        assertEquals(Phase.Play, round.phase)
    }

    @Test fun `the bulb burning out loses the round`() {
        val round = playing(now = 0).pair(0).tick(35_000)
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
        assertEquals(31_000L, round.endsAt)
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

    // Nights

    @Test fun `nights grow and give about five seconds a pair`() {
        assertEquals(listOf(12, 16, 20, 20, 20, 20), NIGHTS.map { it.cards })
        NIGHTS.forEach { assertTrue(it.roundMs in it.pairs * 4_500L..it.pairs * 5_500L) }
        assertEquals((1..NIGHTS.size).toList(), NIGHTS.map { it.number })
    }

    @Test fun `each night deals its own moths`() {
        val deck = deal(NIGHTS[1])
        assertEquals(16, deck.size)
        assertEquals(8, deck.toSet().size)
        assertTrue(MothId.Sulphur in deck && MothId.Rose in deck)
        assertFalse(MothId.Leaf in deck)
    }

    @Test fun `a seeded deal comes out the same every time`() {
        assertEquals(deal(DAILY, Random(20261114)), deal(DAILY, Random(20261114)))
        assertNotEquals(deal(DAILY, Random(20261114)), deal(DAILY, Random(20261115)))
    }

    @Test fun `the daily porch is the same for everyone today`() {
        val a = Round.daily(MothId.Wick, 20261114)
        val b = Round.daily(MothId.Iris, 20261114)
        assertEquals(a.deck, b.deck)
        assertTrue(a.daily)
        assertEquals(20261114L, dailySeed(2026, 11, 14))
    }

    @Test fun `a bigger night needs every pair to win`() {
        val deck = BOARD_MOTHS.take(8).flatMap { listOf(it, it) }
        var round = Round.start(MothId.Wick, NIGHTS[1]).copy(deck = deck).go(0)
        for (first in 0 until 12 step 2) round = round.pair(first)
        assertEquals(Phase.Play, round.phase)
        round = round.pair(12).pair(14)
        assertEquals(Phase.Won, round.phase)
    }

    // Twists

    @Test fun `a dead leaf costs two seconds and stays turned`() {
        val deck = listOf(MothId.Leaf) + sorted
        var round = Round.start(MothId.Wick, NIGHTS[4]).copy(deck = deck, matched = List(deck.size) { false }).go(0)
        val before = round.endsAt
        round = round.flip(0, 1_000)
        assertEquals(before - DUD_MS, round.endsAt)
        assertTrue(round.isFaceUp(0))
        assertSame(round, round.flip(0, 1_000))
        assertTrue(round.open.isEmpty())
    }

    @Test fun `night five deals two dead leaves`() {
        assertEquals(2, deal(NIGHTS[4]).count { it == MothId.Leaf })
    }

    @Test fun `the wanderer swaps two face-down cards after a miss`() {
        val start = Round.start(MothId.Wick, NIGHTS[3]).copy(deck = sorted, seed = 9).go(0).pair(0)
        val settled = start.flip(2, 0).flip(4, 0).settle()
        val (a, b) = settled.swap!!
        assertEquals(1, settled.swaps)
        assertEquals(start.deck[a], settled.deck[b])
        assertEquals(start.deck[b], settled.deck[a])
        assertFalse(a in listOf(0, 1) || b in listOf(0, 1)) // matched cards never move
        assertEquals(settled, start.flip(2, 0).flip(4, 0).settle()) // and it is deterministic
    }

    @Test fun `other nights don't swap`() {
        assertEquals(null, playing().flip(2, 0).flip(4, 0).settle().swap)
    }

    @Test fun `a shadow night hides one moth in the peek`() {
        assertEquals(null, Round.start(MothId.Wick).shadow)
        val shadow = Round.start(MothId.Wick, NIGHTS[5])
        assertTrue(shadow.shadow in shadow.deck)
        // The hidden moth follows the seed, not the first card.
        val hidden = (0L until 40L).map { Round.start(MothId.Wick, NIGHTS[5], Random(1), seed = it).shadow }.toSet()
        assertTrue("the shadow should vary between rounds", hidden.size > 5)
    }

    // Dot

    @Test fun `dot taps straight through a wrong pair`() {
        val miss = playing(MothId.Dot).flip(2, 0).flip(4, 0)
        val next = miss.flip(6, 0)
        assertFalse(next.miss)
        assertEquals(listOf(6), next.open)
    }

    @Test fun `others wait for a wrong pair to fold`() {
        val miss = playing(MothId.Wick).flip(2, 0).flip(4, 0)
        assertSame(miss, miss.flip(6, 0))
    }

    @Test fun `dot's first miss keeps the streak, the second breaks it`() {
        var round = playing(MothId.Dot).pair(0).pair(2)
        round = round.flip(4, 0).flip(6, 0).settle()
        assertEquals(2, round.streak)
        round = round.flip(4, 0).flip(6, 0).settle()
        assertEquals(0, round.streak)
    }

    // Pause

    @Test fun `pausing freezes the bulb`() {
        val paused = playing(now = 0).pause(10_000)
        assertTrue(paused.paused)
        assertEquals(25_000L, paused.left)
        assertSame(paused, paused.tick(99_000))
        assertSame(paused, paused.flip(0, 99_000))
    }

    @Test fun `resuming restarts the clock with the time that was left`() {
        val resumed = playing(now = 0).pause(10_000).resume(60_000)
        assertFalse(resumed.paused)
        assertEquals(85_000L, resumed.endsAt)
        assertEquals(Phase.Play, resumed.tick(84_000).phase)
    }

    @Test fun `only a live round pauses`() {
        val idle = Round.ready(MothId.Wick)
        assertSame(idle, idle.pause(0))
        assertSame(idle, idle.resume(0))
    }

    @Test fun `the longest run is kept`() {
        val round = playing().pair(0).pair(2).pair(4).flip(6, 0).flip(8, 0).settle().pair(6)
        assertEquals(3, round.bestRun)
    }

    // Onboarding

    @Test fun `the tutorial is two pairs with a long look`() {
        val round = Round.start(MothId.Wick, TUTORIAL)
        assertEquals(4, round.deck.size)
        assertTrue(round.tutorial)
        assertEquals(3_000L, round.rules.peekMs)
    }

    @Test fun `nights name the moths they add`() {
        assertEquals(emptyList<MothId>(), newOn(NIGHTS[0]))
        assertEquals(listOf(MothId.Sulphur, MothId.Rose), newOn(NIGHTS[1]))
        assertEquals(listOf(MothId.Midnight, MothId.Moss), newOn(NIGHTS[2]))
        assertEquals(emptyList<MothId>(), newOn(NIGHTS[3]))
    }

    // Saved state

    @Test fun `a round in play survives being saved and comes back paused`() {
        val round = Round.start(MothId.Sorbet, NIGHTS[4], Random(3), seed = 3).go(0).tick(12_000)
        val played = round.flip(round.deck.indexOfFirst { it != MothId.Leaf }, 12_000)
        val (back, lead) = restoreRound(played.saved(MothId.Sorbet))!!
        assertEquals(MothId.Sorbet, lead)
        assertTrue(back.paused)
        assertEquals(played.deck, back.deck)
        assertEquals(played.open, back.open)
        assertEquals(played.left, back.left)
        assertEquals(played.night, back.night)
        assertEquals(rulesFor(MothId.Sorbet, NIGHTS[4]), back.rules)
        assertEquals(Phase.Play, back.resume(50_000).tick(51_000).phase)
    }

    @Test fun `a wrong pair that was showing comes back folded`() {
        val miss = playing().flip(2, 0).flip(4, 0)
        val (back, _) = restoreRound(miss.saved(MothId.Wick))!!
        assertFalse(back.miss)
        assertTrue(back.open.isEmpty())
        assertEquals(1, back.misses)
    }

    @Test fun `the daily porch and the tutorial restore as themselves`() {
        assertEquals(DAILY, restoreRound(Round.daily(MothId.Wick, 1).saved(MothId.Wick))!!.first.night)
        assertEquals(TUTORIAL, nightNumbered(-1))
    }

    @Test fun `junk does not restore`() {
        assertEquals(null, restoreRound(mapOf("lead" to "Nobody")))
    }
}
