package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostTest {
    /** A known deck: pairs sit side by side at (0,1), (2,3), ... (10,11). */
    private val sorted = LEADS.flatMap { listOf(it, it) }

    private fun playing(boosts: Set<Boost> = emptySet(), lead: MothId = MothId.Moss, now: Long = 0): Round =
        Round.start(lead, CLASSIC, boosts = boosts).copy(deck = sorted).go(now)

    private fun Round.pair(first: Int, now: Long = 0) = flip(first, now).flip(first + 1, now)

    private fun Round.clear(now: Long = 0): Round = (0 until deck.size step 2).fold(this) { r, i -> if (r.matched[i]) r else r.pair(i, now) }

    // ---------------------------------------------------------------------------------------------
    // Prices and words come from the economy table

    @Test fun `boost prices`() {
        assertEquals(listOf(15, 10, 20, 25, 30), Boost.entries.map { it.price })
        assertEquals("+10 s on the bulb", Boost.Time.title)
        assertEquals("Every card flashes open for 0.9 s.", Boost.Look.detail)
        assertEquals(25, priceOf(setOf(Boost.Time, Boost.Peek)))
    }

    // ---------------------------------------------------------------------------------------------
    // Pre-round boosts change the rules

    @Test fun `more bulb and a longer peek`() {
        val plain = rulesFor(MothId.Moss, CLASSIC)
        val boosted = rulesFor(MothId.Moss, CLASSIC, boosts = setOf(Boost.Time, Boost.Peek))
        assertEquals(plain.roundMs + 10_000, boosted.roundMs)
        assertEquals(plain.peekMs + 1_500, boosted.peekMs)
        // They stack with the lead's own perks.
        assertEquals(rulesFor(MothId.Luna, CLASSIC).roundMs + 10_000, rulesFor(MothId.Luna, CLASSIC, boosts = setOf(Boost.Time)).roundMs)
        assertEquals(rulesFor(MothId.Iris, CLASSIC).peekMs + 1_500, rulesFor(MothId.Iris, CLASSIC, boosts = setOf(Boost.Peek)).peekMs)
    }

    @Test fun `a boosted round starts with the longer bulb and remembers its boosts`() {
        val round = Round.start(MothId.Moss, CLASSIC, boosts = setOf(Boost.Time))
        assertEquals(40_000L, round.left)
        assertEquals(setOf(Boost.Time), round.boosts)
    }

    @Test fun `only pre-round boosts can be bought ahead`() {
        val round = Round.start(MothId.Moss, CLASSIC, boosts = Boost.entries.toSet())
        assertEquals(setOf(Boost.Time, Boost.Peek), round.boosts)
        assertFalse(round.copy(phase = Phase.Play).canUse(Boost.Time))
    }

    @Test fun `the daily porch allows no boosts`() {
        val daily = Round.start(MothId.Moss, DAILY, boosts = setOf(Boost.Time, Boost.Peek))
        assertTrue(daily.boosts.isEmpty())
        assertEquals(rulesFor(MothId.Moss, DAILY), daily.rules)
        val playing = Round.daily(MothId.Moss, 20261114).go(0)
        assertTrue(Boost.entries.none { playing.canUse(it) })
        assertSame(playing, playing.secondLook())
        assertSame(playing, playing.lightPair(0))
        val out = playing.tick(playing.endsAt)
        assertEquals(Phase.Lost, out.phase)
        assertSame(out, out.relight(out.endsAt))
    }

    @Test fun `the tutorial allows no boosts`() {
        val round = Round.start(MothId.Luna, TUTORIAL, boosts = setOf(Boost.Time))
        assertTrue(round.boosts.isEmpty())
        assertFalse(round.go(0).canUse(Boost.Look))
    }

    // ---------------------------------------------------------------------------------------------
    // Second look

    @Test fun `second look flashes every card briefly, once`() {
        val round = playing().secondLook()
        assertTrue(round.flashing)
        assertEquals(900L, round.flashMs)
        assertTrue(Boost.Look in round.boosts)
        assertTrue(round.deck.indices.all { round.isFaceUp(it) })
        val after = round.unflash()
        assertFalse(after.canUse(Boost.Look))
        assertSame(after, after.secondLook())
    }

    @Test fun `atlas's own flash keeps its own length`() {
        val round = Round.start(MothId.Atlas, CLASSIC).go(0).flash()
        assertEquals(FLASH_MS, round.flashMs)
        assertTrue(round.boosts.isEmpty())
    }

    @Test fun `second look waits for a paused round`() {
        val paused = playing().pause(1_000)
        assertTrue(paused.canUse(Boost.Look))
        assertSame(paused, paused.secondLook())
    }

    // ---------------------------------------------------------------------------------------------
    // Light a pair

    @Test fun `light a pair finds one pair, scores nothing and keeps the streak`() {
        val before = playing().pair(0)
        val after = before.lightPair(0)
        assertEquals(before.pairs + 1, after.pairs)
        assertEquals(before.score, after.score)
        assertEquals(before.streak, after.streak)
        assertEquals(2 to 3, after.lit)
        assertTrue(after.matched[2] && after.matched[3])
        assertTrue(Boost.Pair in after.boosts)
        assertSame(after, after.lightPair(0))
    }

    @Test fun `light a pair finds the partner of a turned card`() {
        val one = playing().flip(7, 0)
        val after = one.lightPair(0)
        assertEquals(6 to 7, after.lit)
        assertTrue(after.open.isEmpty())
    }

    @Test fun `light a pair folds a wrong pair back first`() {
        val wrong = playing().flip(0, 0).flip(2, 0)
        assertTrue(wrong.miss)
        val after = wrong.lightPair(0)
        assertFalse(after.miss)
        assertEquals(1, after.pairs)
    }

    @Test fun `light a pair can finish the board`() {
        val last = (0 until 10 step 2).fold(playing()) { r, i -> r.pair(i) }
        val won = last.lightPair(5_000)
        assertEquals(Phase.Won, won.phase)
        assertEquals(last.score + 25 * SECOND_POINTS, won.score)
    }

    // ---------------------------------------------------------------------------------------------
    // Relight

    @Test fun `relight carries on after the bulb burns out, keeping found pairs`() {
        val found = playing().pair(0).pair(2)
        val out = found.tick(found.endsAt)
        assertEquals(Phase.Lost, out.phase)
        assertTrue(out.canUse(Boost.Relight))
        val relit = out.relight(50_000)
        assertEquals(Phase.Play, relit.phase)
        assertEquals(15_000L, relit.left)
        assertEquals(65_000L, relit.endsAt)
        assertEquals(2, relit.pairs)
        assertTrue(relit.matched[0] && relit.matched[3])
        // Once a round: the next burn-out is final.
        val again = relit.tick(relit.endsAt)
        assertEquals(Phase.Lost, again.phase)
        assertFalse(again.canUse(Boost.Relight))
        assertSame(again, again.relight(70_000))
    }

    @Test fun `while a relight is on offer the board stays face down`() {
        val out = playing().pair(0).let { it.tick(it.endsAt) }
        assertTrue(out.isFaceUp(5))
        val holding = out.copy(holding = true)
        assertFalse(holding.isFaceUp(5))
        assertTrue(holding.isFaceUp(0)) // found pairs stay showing
        assertFalse(holding.relight(0).holding)
    }

    // ---------------------------------------------------------------------------------------------
    // Fairness: the third star needs a boost-free clear

    private fun perfect(round: Round) = round.clear(0)

    @Test fun `a perfect clear earns three stars without boosts`() {
        assertEquals(3, starsFor(perfect(playing())))
    }

    @Test fun `any boost caps the stars at two`() {
        assertEquals(2, starsFor(perfect(playing(setOf(Boost.Time)))))
        assertEquals(2, starsFor(perfect(playing(setOf(Boost.Peek)))))
        assertEquals(2, starsFor(perfect(playing().secondLook().unflash())))
        assertEquals(2, starsFor(perfect(playing().lightPair(0))))
        val relit = playing().let { it.tick(it.endsAt) }.relight(0)
        assertEquals(2, starsFor(perfect(relit)))
    }

    @Test fun `a boost never raises a lower star count`() {
        val slow = perfect(playing(setOf(Boost.Time))).copy(left = 1_000)
        assertEquals(1, starsFor(slow))
    }

    @Test fun `a boosted perfect round can't take a level to three stars`() {
        val (after, report) = settleRound(Progress(), perfect(playing(setOf(Boost.Time))).copy(level = LEVELS[0]), MothId.Moss)
        assertEquals(2, report.stars)
        assertEquals(2, after.levelStars[0])
    }

    // ---------------------------------------------------------------------------------------------
    // Saved state

    @Test fun `boosts survive the app being killed`() {
        val round = Round.start(MothId.Moss, levelAt(4), boosts = setOf(Boost.Time)).go(0).secondLook().unflash()
        val (back, _) = restoreRound(round.saved(MothId.Moss))!!
        assertEquals(setOf(Boost.Time, Boost.Look), back.boosts)
        assertEquals(round.rules, back.rules)
        assertEquals(rulesFor(MothId.Moss, levelAt(4)).roundMs + 10_000, back.rules.roundMs)
    }

    @Test fun `an old saved round without boosts restores with none`() {
        val values = playing().saved(MothId.Moss) - "boosts"
        val (back, _) = restoreRound(values)!!
        assertTrue(back.boosts.isEmpty())
    }
}
