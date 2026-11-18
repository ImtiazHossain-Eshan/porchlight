package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlightLogTest {
    /** Pairs side by side: (0,1) Luna, (2,3) Wick, (4,5) Atlas, ... */
    private val sorted = LEADS.flatMap { listOf(it, it) }

    private fun playing(deck: List<MothId> = sorted, lead: MothId = MothId.Luna) =
        Round.start(lead, CLASSIC).copy(deck = deck).go(0)

    private fun Round.pair(a: Int, b: Int) = flip(a, 0).flip(b, 0).settle()

    @Test fun `matches and misses are logged in the order they happen`() {
        val round = playing().pair(0, 1).pair(2, 4).pair(2, 3).pair(4, 5)
        assertEquals(
            listOf(
                LogEntry.Match(MothId.Luna, 1),
                LogEntry.Miss,
                LogEntry.Match(MothId.Wick, 1),
                LogEntry.Match(MothId.Atlas, 2),
            ),
            flightLog(round),
        )
    }

    @Test fun `each match carries the multiplier it scored at`() {
        val round = (0 until 5).fold(playing()) { r, i -> r.pair(i * 2, i * 2 + 1) }
        assertEquals(listOf(1, 2, 3, 4, 4), flightLog(round).map { (it as LogEntry.Match).multiplier })
    }

    @Test fun `the winning pair is logged too`() {
        val round = (0 until 6).fold(playing()) { r, i -> r.pair(i * 2, i * 2 + 1) }
        assertEquals(Phase.Won, round.phase)
        assertEquals(6, flightLog(round).size)
    }

    @Test fun `every miss is recorded, forgiven or not`() {
        val round = playing(lead = MothId.Dot).pair(0, 2).pair(0, 4).pair(0, 1)
        assertEquals(listOf(LogEntry.Miss, LogEntry.Miss), flightLog(round).take(2))
        assertEquals(2, round.misses)
    }

    @Test fun `a dead leaf is not a pair and stays out of the log`() {
        val deck = sorted.dropLast(2) + MothId.Leaf
        val round = playing(deck).flip(deck.lastIndex, 0).pair(0, 1)
        assertEquals(listOf(LogEntry.Match(MothId.Luna, 1)), flightLog(round))
    }

    @Test fun `the log survives saving and restoring the round`() {
        val round = playing().pair(0, 1).pair(2, 4).pair(2, 3)
        val (back, _) = restoreRound(round.saved(MothId.Luna))!!
        assertEquals(flightLog(round), flightLog(back))
    }

    @Test fun `a saved round from before the log existed restores with an empty log`() {
        val saved = playing().pair(0, 1).saved(MothId.Luna) - "history"
        assertEquals(emptyList<LogEntry>(), flightLog(restoreRound(saved)!!.first))
    }

    @Test fun `the log never depends on where the cards lie`() {
        // The same moves on two different deals: the logs must be identical, so no position leaks.
        val a = playing(sorted)
        val shuffled = sorted.shuffled(Random(7))
        val b = playing(shuffled)
        fun at(deck: List<MothId>, moth: MothId) = deck.indices.filter { deck[it] == moth }
        val la = at(sorted, MothId.Atlas); val lb = at(shuffled, MothId.Atlas)
        val wa = at(sorted, MothId.Wick); val wb = at(shuffled, MothId.Wick)
        val ra = a.pair(la[0], wa[0]).pair(la[0], la[1]).pair(wa[0], wa[1])
        val rb = b.pair(lb[0], wb[0]).pair(lb[0], lb[1]).pair(wb[0], wb[1])
        assertNotEquals(la, lb)
        assertEquals(flightLog(ra), flightLog(rb))
    }

    @Test fun `no card index appears in the log or its saved form`() {
        // Entries only hold a moth and a multiplier (1..4); there is no field for a position.
        LogEntry.Match::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach {
            assertTrue("unexpected field ${it.name}", it.name in setOf("moth", "multiplier"))
        }
        val round = playing().pair(10, 11).pair(8, 9)
        assertEquals("Sorbet*1,Iris*2", encodeLog(flightLog(round)))
    }

    @Test fun `the log reads back what it wrote and drops what it can't read`() {
        val log = listOf(LogEntry.Match(MothId.Moss, 3), LogEntry.Miss, LogEntry.Match(MothId.Luna, 1))
        assertEquals(log, decodeLog(encodeLog(log)))
        assertEquals(listOf(LogEntry.Miss), decodeLog("Nobody*2,-,Luna*x"))
        assertEquals(emptyList<LogEntry>(), decodeLog(null))
    }

    // The daily card

    private fun dailyWin(): Round {
        val deck = deal(DAILY, Random(1))
        val start = Round.daily(MothId.Luna, 20261115).copy(deck = deck).go(0)
        val pairs = deck.indices.groupBy { deck[it] }.values.toList()
        val missed = start.pair(pairs[0][0], pairs[1][0])
        return pairs.fold(missed) { r, (a, b) -> r.pair(a, b) }.let { it.copy(left = 12_400) }
    }

    @Test fun `a scored daily porch keeps its card, with the log and the streak`() {
        val round = dailyWin()
        val (after, _) = settleRound(Progress(dailyDay = 20261114, dailyStreak = 4), round, MothId.Luna, today = 20261115, yesterday = 20261114)
        val card = after.dailyCard
        assertNotNull(card)
        card!!
        assertEquals(20261115, card.day)
        assertEquals(5, card.streak)
        assertEquals(flightLog(round), card.log)
        assertEquals("8/8 pairs · 12.4 s left · 1 miss", card.line)
    }

    @Test fun `a lost card says how many pairs, and no time`() {
        val card = DailyCard(1, MothId.Wick, false, 0, 900, 5, 8, 0, 3, 2, 1, emptyList())
        assertEquals("The bulb burned out · 5/8 pairs", card.line)
    }

    @Test fun `a practice round leaves the day's card alone`() {
        val first = settleRound(Progress(), dailyWin(), MothId.Luna, today = 20261115).first
        val again = settleRound(first, dailyWin().copy(score = 1), MothId.Wick, today = 20261115).first
        assertEquals(first.dailyCard, again.dailyCard)
    }

    @Test fun `the card reads back what it wrote`() {
        val card = settleRound(Progress(), dailyWin(), MothId.Luna, today = 20261115).first.dailyCard!!
        assertEquals(card, DailyCard.decode(card.encode()))
        assertNull(DailyCard.decode("junk"))
        assertNull(DailyCard.decode(null))
    }

    @Test fun `the share text line is unchanged`() {
        assertEquals("Porchlight daily 20261115 ★★☆ 2,340", dailyShare(20261115, 2, 2_340))
    }
}
