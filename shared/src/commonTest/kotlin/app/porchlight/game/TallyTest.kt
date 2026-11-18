package app.porchlight.game

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class TallyTest {
    /** Pairs side by side: (0,1) Luna, (2,3) Wick, ... */
    private val sorted = LEADS.flatMap { listOf(it, it) }

    private fun playing() = Round.start(MothId.Luna, CLASSIC).copy(deck = sorted).go(0)

    private fun Round.pair(a: Int, b: Int) = flip(a, 0).flip(b, 0).settle()

    private fun won(missFirst: Boolean = false): Round {
        val start = if (missFirst) playing().pair(0, 2) else playing()
        return (0 until 6).fold(start) { r, i -> r.pair(i * 2, i * 2 + 1) }
    }

    @Test fun `the points on the tally add up to the score`() {
        for (round in listOf(won(), won(missFirst = true), playing().pair(0, 1).pair(2, 3))) {
            assertEquals(round.score, tally(round).sumOf { it.points })
        }
    }

    @Test fun `a win lists pairs - streak and time - and no misses line when clean`() {
        val round = won()
        assertEquals(Phase.Won, round.phase)
        assertEquals(listOf(TallyKind.Pairs, TallyKind.Streak, TallyKind.Time), tally(round).map { it.kind })
        assertEquals("6 pairs", tally(round).first().text)
    }

    @Test fun `misses get a line only when there are some`() {
        val misses = tally(won(missFirst = true)).filter { it.kind == TallyKind.Misses }
        assertEquals(listOf("1 miss"), misses.map { it.text })
    }

    @Test fun `a lost round has no time line and never says none or plus nothing`() {
        val lost = playing().pair(0, 1).tick(Long.MAX_VALUE)
        assertEquals(Phase.Lost, lost.phase)
        val lines = tally(lost)
        assertTrue(lines.none { it.kind == TallyKind.Time })
        assertTrue(lines.none { "none" in it.text })
        assertEquals("1 pair", lines.single { it.kind == TallyKind.Pairs }.text)
    }

    @Test fun `a round with no pairs has an empty tally`() {
        val lost = playing().tick(Long.MAX_VALUE)
        assertEquals(emptyList<TallyLine>(), tally(lost))
    }

    @Test fun `a close loss says how many pairs were left`() {
        val five = (0 until 5).fold(playing()) { r, i -> r.pair(i * 2, i * 2 + 1) }.tick(Long.MAX_VALUE)
        assertEquals("So close. 1 pair to go.", lossNote(five))
        val four = (0 until 4).fold(playing()) { r, i -> r.pair(i * 2, i * 2 + 1) }.tick(Long.MAX_VALUE)
        assertEquals("So close. 2 pairs to go.", lossNote(four))
    }

    @Test fun `a big loss gets a tip - the same one every time for that round`() {
        val lost = playing().copy(seed = 7).tick(Long.MAX_VALUE)
        val note = lossNote(lost)
        assertTrue(note in LOSS_TIPS)
        assertEquals(note, lossNote(lost))
    }

    @Test fun `a win has no loss note`() = assertNull(lossNote(won()))

    @Test fun `star hints say what earns each star`() {
        val round = won()
        assertEquals("Find every pair", starHint(0, round))
        assertEquals("8 s left", starHint(1, round)) // a quarter of CLASSIC's 30 s, rounded up
        assertEquals("No misses", starHint(2, round)) // CLASSIC allows none
        val four = round.copy(level = levelAt(4))
        assertEquals("9 s left", starHint(1, four.copy(level = four.level.copy(roundMs = 36_000))))
        assertEquals("2 misses at most", starHint(2, four))
        val daily = round.copy(level = DAILY, daily = true)
        assertEquals("No misses", starHint(2, daily))
    }
}
