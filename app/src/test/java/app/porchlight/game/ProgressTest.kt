package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    @Test fun `xp needed per level`() {
        assertEquals(listOf(0, 200, 500, 900, 1_400, 2_000), (1..6).map(::xpFor))
    }

    @Test fun `level boundaries`() {
        assertEquals(1, levelFor(0))
        assertEquals(1, levelFor(199))
        assertEquals(2, levelFor(200))
        assertEquals(3, levelFor(500))
        assertEquals(6, levelFor(2_000))
        assertEquals(6, levelFor(2_699))
        assertEquals(7, levelFor(2_700))
    }

    @Test fun `each level up unlocks the next moth`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), LEADS.map { it.unlockLevel })
        assertFalse(Progress(xp = 99_999).unlocked(MothId.Moss))
    }

    @Test fun `a new player only has wick`() {
        val fresh = Progress()
        assertTrue(fresh.unlocked(MothId.Wick))
        assertFalse(fresh.unlocked(MothId.Ember))
    }

    @Test fun `level six unlocks the whole porch`() {
        val veteran = Progress(xp = 2_000)
        assertTrue(LEADS.all { veteran.unlocked(it) })
    }

    @Test fun `a lost round earns no stars`() {
        assertEquals(0, starsFor(Round(phase = Phase.Lost, left = 0)))
    }

    @Test fun `a slow win with misses earns one star`() {
        assertEquals(1, starsFor(Round(phase = Phase.Won, left = 4_000, misses = 3)))
    }

    @Test fun `ten seconds left earns a star`() {
        assertEquals(2, starsFor(Round(phase = Phase.Won, left = 10_000, misses = 1)))
    }

    @Test fun `no wrong pairs earns a star`() {
        assertEquals(2, starsFor(Round(phase = Phase.Won, left = 2_000, misses = 0)))
    }

    @Test fun `a fast clean win earns three stars`() {
        assertEquals(3, starsFor(Round(phase = Phase.Won, left = 20_000, misses = 0)))
    }

    @Test fun `streak multiplier caps at four`() {
        val rules = Rules()
        assertEquals(listOf(1, 2, 3, 4, 4, 4), (0..5).map { multiplierAt(it, rules) })
    }

    @Test fun `every badge has a title and a rule`() {
        assertTrue(Badge.entries.size >= 12)
        Badge.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.glyph.isNotBlank())
            assertTrue(it.detail.endsWith("."))
        }
    }

    @Test fun `every moth has a perk`() {
        LEADS.forEach {
            assertTrue(it.perkName.isNotBlank())
            assertTrue(it.perk.endsWith("."))
        }
    }

    private fun won(night: Night = NIGHTS[0], left: Long = 20_000, misses: Int = 0, score: Int = 2_000) =
        Round(phase = Phase.Won, night = night, left = left, misses = misses, score = score, deck = deal(night))

    @Test fun `two stars on your newest night opens the next`() {
        val (after, report) = settleRound(Progress(), won(misses = 1), MothId.Wick)
        assertEquals(2, after.nights)
        assertEquals(2, after.night)
        assertEquals(2, report.newNight)
    }

    @Test fun `one star does not open the next night`() {
        val (after, report) = settleRound(Progress(), won(left = 2_000, misses = 2), MothId.Wick)
        assertEquals(1, after.nights)
        assertEquals(null, report.newNight)
    }

    @Test fun `replaying an old night opens nothing new`() {
        val (after, _) = settleRound(Progress(nights = 4, night = 1), won(), MothId.Wick)
        assertEquals(4, after.nights)
    }

    @Test fun `three stars masters the lead`() {
        val (after, report) = settleRound(Progress(), won(), MothId.Wick)
        assertTrue(MothId.Wick in after.mastered)
        assertEquals(MothId.Wick, report.newMastery)
        assertTrue(Badge.GoldenWing in report.newBadges)
    }

    @Test fun `the daily porch is scored once a day`() {
        val daily = won(DAILY).copy(daily = true)
        val (first, report) = settleRound(Progress(), daily, MothId.Wick, today = 20261114)
        assertTrue(report.xp > 0)
        assertTrue(first.playedDaily(20261114))
        val (_, again) = settleRound(first, daily, MothId.Wick, today = 20261114)
        assertTrue(again.practice)
        assertEquals(0, again.xp)
    }

    @Test fun `daily streaks count consecutive days`() {
        val daily = won(DAILY).copy(daily = true)
        val (one, _) = settleRound(Progress(), daily, MothId.Wick, today = 20261112, yesterday = 20261111)
        val (two, _) = settleRound(one, daily, MothId.Wick, today = 20261113, yesterday = 20261112)
        val (three, report) = settleRound(two, daily, MothId.Wick, today = 20261114, yesterday = 20261113)
        assertEquals(3, three.dailyStreak)
        assertTrue(Badge.Regular in report.newBadges)
        val (gap, _) = settleRound(three, daily, MothId.Wick, today = 20261120, yesterday = 20261119)
        assertEquals(1, gap.dailyStreak)
    }

    @Test fun `share text shows the stars and score`() {
        assertEquals("Porchlight daily 20261114 ★★☆ 2,340", dailyShare(20261114, 2, 2_340))
    }

    @Test fun `stars scale with the night's bulb`() {
        val night = NIGHTS[2] // 50 s: a third is about 16.7 s
        assertEquals(2, starsFor(won(night, left = 17_000, misses = 1)))
        assertEquals(1, starsFor(won(night, left = 12_000, misses = 1)))
    }
}
