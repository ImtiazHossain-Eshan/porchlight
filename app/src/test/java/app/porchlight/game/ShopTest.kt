package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopTest {
    private fun rich(p: Progress = Progress(), balance: Int = 10_000) = p.copy(wallet = Wallet(balance = balance, earned = balance))

    private fun SpendResult.done(): Progress = (this as SpendResult.Spent).progress

    // ---------------------------------------------------------------------------------------------
    // Boosts

    @Test fun `boosts are paid for together`() {
        val after = buyBoosts(rich(balance = 100), setOf(Boost.Time, Boost.Peek), 1).done()
        assertEquals(75, after.wallet.balance)
        assertEquals("Spend/boost.Time+Peek", after.wallet.ledger.last().source)
        assertEquals(Progress(), buyBoosts(Progress(), emptySet(), 1).done())
        assertTrue(buyBoosts(rich(balance = 20), setOf(Boost.Time, Boost.Peek), 1) is SpendResult.Short)
    }

    // ---------------------------------------------------------------------------------------------
    // Opening a chapter early

    /** Standing at level 11 with [stars] stars over the first ten levels. */
    private fun atGate(stars: List<Int>) = Progress(reached = 11, current = 10, levelStars = stars + List(40) { 0 })

    @Test fun `a locked chapter opens early for ten a missing star`() {
        // 3 stars short of chapter 2's 15: 30 Fireflies. (12 short would be 120.)
        val waiting = atGate(List(10) { if (it < 2) 2 else 1 })
        assertEquals(12, waiting.totalStars)
        assertEquals(2, waiting.waitingChapter)
        assertEquals(30, chapterPrice(waiting, 2))
        assertEquals(120, chapterPrice(atGate(List(10) { if (it < 3) 1 else 0 }), 2))
        val opened = openChapterEarly(rich(waiting), 2, 1).done()
        assertTrue(opened.chapterOpen(2))
        assertTrue(opened.isOpen(11))
        assertEquals(11, opened.current)
        assertEquals(10_000 - 30, opened.wallet.balance)
        // Bought stars? No: the stars are what they were.
        assertEquals(12, opened.totalStars)
    }

    @Test fun `only the chapter the player is waiting at is on offer`() {
        val waiting = atGate(List(10) { 1 })
        assertNull(chapterPrice(waiting, 3))
        assertEquals(SpendResult.NotOffered, openChapterEarly(rich(waiting), 3, 1))
        val open = atGate(List(10) { 2 })
        assertNull(open.waitingChapter)
        assertNull(chapterPrice(open, 2))
    }

    @Test fun `a short balance opens nothing`() {
        val waiting = atGate(List(10) { 1 })
        val result = openChapterEarly(rich(waiting, balance = 49), 2, 1)
        assertEquals(SpendResult.Short(price = 50, balance = 49), result)
    }

    // ---------------------------------------------------------------------------------------------
    // Skipping a level

    private fun stuck(losses: Int) = Progress(reached = 7, current = 7, levelStars = List(6) { 1 } + List(44) { 0 }, lossLevel = 7, losses = losses)

    @Test fun `a level can be skipped only after three losses in a row on it`() {
        assertFalse(skipOffered(stuck(2)))
        assertTrue(skipOffered(stuck(3)))
        assertFalse(skipOffered(stuck(3).copy(current = 6)))
        assertEquals(SpendResult.NotOffered, skipLevel(rich(stuck(2)), 1))
    }

    @Test fun `a skipped level has no stars, stays playable, and opens the next`() {
        val after = skipLevel(rich(stuck(3)), 1).done()
        assertEquals(10_000 - 50, after.wallet.balance)
        assertEquals(setOf(7), after.skipped)
        assertEquals(0, after.levelStars[6])
        assertEquals(8, after.reached)
        assertEquals(8, after.current)
        assertTrue(after.isOpen(7))
        assertTrue(after.isOpen(8))
        assertEquals(0, after.losses)
        assertFalse(skipOffered(after))
    }

    @Test fun `the last level can't be skipped`() {
        val end = Progress(reached = 50, current = 50, lossLevel = 50, losses = 5, chapters = 5, levelStars = List(49) { 2 } + 0)
        assertFalse(skipOffered(end))
    }

    @Test fun `a skip into a locked chapter opens the level but waits at the gate`() {
        val gate = Progress(reached = 10, current = 10, levelStars = List(9) { 1 } + List(41) { 0 }, lossLevel = 10, losses = 3)
        val after = skipLevel(rich(gate), 1).done()
        assertEquals(11, after.reached)
        assertEquals(10, after.current)
        assertEquals(2, after.waitingChapter)
    }

    @Test fun `a skipped level, won later, earns its stars as usual`() {
        val after = skipLevel(rich(stuck(3)), 1).done().copy(current = 7)
        val round = Round(phase = Phase.Won, level = levelAt(7), left = levelAt(7).roundMs / 2, misses = 0, score = 1_000, deck = deal(levelAt(7)))
        val (won, report) = settleRound(after, round, MothId.Luna)
        assertEquals(3, won.levelStars[6])
        assertEquals(3 + 5 + 8, report.earnings.filterIsInstance<Earning.NewStar>().sumOf { it.amount })
    }

    // ---------------------------------------------------------------------------------------------
    // Lead moths early

    @Test fun `a lead moth can be bought before its level`() {
        val fresh = rich()
        assertFalse(fresh.unlocked(MothId.Sorbet))
        assertTrue(earlyLeadOffered(fresh, MothId.Sorbet))
        val after = buyLeadEarly(fresh, MothId.Sorbet, 1).done()
        assertTrue(after.unlocked(MothId.Sorbet))
        assertEquals(10_000 - 150, after.wallet.balance)
        assertFalse(earlyLeadOffered(after, MothId.Sorbet))
        assertEquals(SpendResult.NotOffered, buyLeadEarly(after, MothId.Sorbet, 1))
    }

    @Test fun `a moth already met or not a lead is not on offer`() {
        assertFalse(earlyLeadOffered(Progress(), MothId.Luna))
        assertFalse(earlyLeadOffered(Progress(xp = 2_000), MothId.Sorbet))
        assertFalse(earlyLeadOffered(Progress(), MothId.Rose))
    }

    @Test fun `a moth bought early isn't announced again at its level`() {
        val early = buyLeadEarly(rich(), MothId.Wick, 1).done()
        val (_, report) = settleRound(early.copy(xp = 199), Round(phase = Phase.Lost, left = 0, deck = deal()), MothId.Luna)
        assertTrue(MothId.Wick !in report.newMoths)
    }

    // ---------------------------------------------------------------------------------------------
    // Looks

    @Test fun `every look for sale has a price in the economy table, and every price a look`() {
        val forSale = LOOK_CATALOG.filter { it.gift == null }
        assertTrue(forSale.all { it.price != null })
        assertEquals(forSale.map { it.id }.toSet(), Economy.LOOK_PRICES.keys)
        assertTrue(LOOK_CATALOG.filter { it.gift != null }.all { it.price == null })
        assertEquals(LOOK_CATALOG.size, LOOK_CATALOG.map { it.id }.toSet().size)
    }

    @Test fun `prices sit in the agreed bands`() {
        for (look in LOOK_CATALOG.filter { it.price != null }) {
            val price = look.price!!
            when (look.slot) {
                Slot.Back -> assertTrue(look.id, price in 200..400)
                Slot.Jar -> assertEquals(look.id, 300, price)
                Slot.Moth -> assertEquals(look.id, 150, price)
                Slot.Porch -> assertEquals(look.id, 500, price)
            }
        }
    }

    @Test fun `each slot has one default, and every lead one colour`() {
        for (slot in listOf(Slot.Back, Slot.Jar, Slot.Porch)) assertNotNull(defaultLook(slot))
        assertNull(defaultLook(Slot.Moth))
        assertEquals(LEADS.toSet(), LOOK_CATALOG.filter { it.slot == Slot.Moth }.map { it.moth }.toSet())
        assertTrue(LOOK_CATALOG.all { (it.back != null) == (it.slot == Slot.Back) && (it.porch != null) == (it.slot == Slot.Porch) })
    }

    @Test fun `buying a look takes its price and puts it on`() {
        val look = lookById("back.cherry")!!
        val after = buyLook(rich(), look, month = 6, now = 1).done()
        assertTrue(after.owns(look))
        assertEquals(look, after.wearing("back"))
        assertEquals(10_000 - 250, after.wallet.balance)
        assertEquals(SpendResult.NotOffered, buyLook(after, look, 6, 2))
    }

    @Test fun `looks can be taken off and put back on`() {
        val look = lookById("porch.barn")!!
        val bought = buyLook(rich(), look, 6, 1).done()
        val bare = takeOff(bought, "porch")
        assertNull(bare.wearing("porch"))
        assertTrue(bare.owns(look))
        assertEquals(look, wear(bare, look).wearing("porch"))
        // Putting on the default is the same as taking off.
        assertNull(wear(bought, defaultLook(Slot.Porch)!!).wearing("porch"))
        // Something not owned can't be worn.
        assertNull(wear(Progress(), look).wearing("porch"))
    }

    @Test fun `moth colours are worn one per lead`() {
        val luna = lookById("moth.luna.harvest")!!
        val wick = lookById("moth.wick.blue")!!
        val both = buyLook(buyLook(rich(), luna, 6, 1).done(), wick, 6, 2).done()
        assertEquals(luna, both.wearing(Look.slotFor(MothId.Luna)))
        assertEquals(wick, both.wearing(Look.slotFor(MothId.Wick)))
    }

    @Test fun `seasonal looks show only in season, but are kept for ever`() {
        val frost = lookById("back.frost")!!
        assertFalse(Progress().shows(frost, month = 6))
        assertTrue(Progress().shows(frost, month = 12))
        assertEquals(SpendResult.NotOffered, buyLook(rich(), frost, month = 6, now = 1))
        val owned = buyLook(rich(), frost, month = 1, now = 1).done()
        assertTrue(owned.shows(frost, month = 6))
        assertEquals(frost, owned.wearing("back"))
    }

    @Test fun `pack looks can't be bought with fireflies`() {
        val starter = lookById(STARTER_LOOK)!!
        val supporter = lookById(SUPPORTER_LOOK)!!
        assertFalse(Progress().shows(starter, 6))
        assertEquals(SpendResult.NotOffered, buyLook(rich(), starter, 6, 1))
        assertEquals(SpendResult.NotOffered, buyLook(rich(), supporter, 6, 1))
        assertTrue(Progress(owned = setOf(STARTER_LOOK)).shows(starter, 6))
    }

    @Test fun `a short balance buys nothing`() {
        val result = buyLook(rich(balance = 100), lookById("back.gilded")!!, 6, 1)
        assertEquals(SpendResult.Short(400, 100), result)
    }

    @Test fun `looks never change how a round plays or scores`() {
        val plain = rich()
        val dressed = LOOK_CATALOG.filter { it.price != null }.fold(plain) { p, look ->
            (buyLook(p, look, look.season?.months?.first() ?: 6, 1) as? SpendResult.Spent)?.progress ?: p
        }
        assertEquals(LOOK_CATALOG.count { it.price != null }, dressed.owned.size)
        val round = Round(phase = Phase.Won, level = levelAt(3), left = levelAt(3).roundMs / 2, misses = 1, score = 1_500, deck = deal(levelAt(3)))
        val (a, plainReport) = settleRound(plain, round, MothId.Luna)
        val (b, dressedReport) = settleRound(dressed, round, MothId.Luna)
        assertEquals(plainReport.copy(earnings = emptyList()), dressedReport.copy(earnings = emptyList()))
        assertEquals(plainReport.fireflies, dressedReport.fireflies)
        assertEquals(a.levelStars, b.levelStars)
        assertEquals(a.chapters, b.chapters)
        assertEquals(a.xp, b.xp)
    }

    // ---------------------------------------------------------------------------------------------
    // Save and restore

    @Test fun `skips, early moths and looks survive a restart`() {
        val prefs = MemoryPrefs()
        val store = Store(prefs)
        store.change {
            it.copy(
                skipped = setOf(7), earlyLeads = setOf(MothId.Sorbet),
                owned = setOf("back.cherry", "moth.luna.harvest", "nonsense"),
                equipped = mapOf("back" to "back.cherry", "moth.Luna" to "moth.luna.harvest"),
            )
        }
        val back = Store(prefs).progress
        assertEquals(setOf(7), back.skipped)
        assertEquals(setOf(MothId.Sorbet), back.earlyLeads)
        assertEquals(setOf("back.cherry", "moth.luna.harvest"), back.owned)
        assertEquals(lookById("back.cherry"), back.wearing("back"))
        assertEquals(lookById("moth.luna.harvest"), back.wearing("moth.Luna"))
    }
}
