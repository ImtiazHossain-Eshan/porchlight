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

    @Test fun `a new player only has luna`() {
        val fresh = Progress()
        assertTrue(fresh.unlocked(MothId.Luna))
        assertFalse(fresh.unlocked(MothId.Wick))
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
        assertEquals(12, Badge.entries.size)
        Badge.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.detail.endsWith("."))
        }
        // No two badges are the same metal and mold.
        assertEquals(12, Badge.entries.map { it.metal to it.mold }.toSet().size)
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
        val (after, report) = settleRound(Progress(), won(misses = 1), MothId.Luna)
        assertEquals(2, after.nights)
        assertEquals(2, after.night)
        assertEquals(2, report.newNight)
    }

    @Test fun `one star does not open the next night`() {
        val (after, report) = settleRound(Progress(), won(left = 2_000, misses = 2), MothId.Luna)
        assertEquals(1, after.nights)
        assertEquals(null, report.newNight)
    }

    @Test fun `replaying an old night opens nothing new`() {
        val (after, _) = settleRound(Progress(nights = 4, night = 1), won(), MothId.Luna)
        assertEquals(4, after.nights)
    }

    @Test fun `three stars on night three or later masters the lead`() {
        val (easy, none) = settleRound(Progress(), won(), MothId.Luna)
        assertFalse(MothId.Luna in easy.mastered) // night 1 is too easy to count
        assertEquals(null, none.newMastery)

        val (after, report) = settleRound(Progress(xp = 5_000, nights = 6, night = 3), won(NIGHTS[2]), MothId.Luna)
        assertTrue(MothId.Luna in after.mastered)
        assertEquals(MothId.Luna, report.newMastery)
    }

    private fun unlocks(report: Report) =
        (if (report.newNight != null) 1 else 0) + report.newMoths.size + (if (report.newMastery != null) 1 else 0) + report.newBadges.size

    @Test fun `a strong new player never gets more than two unlocks a round`() {
        var progress = Progress()
        val counts = mutableListOf<Int>()
        repeat(10) {
            val night = NIGHTS[progress.nights - 1]
            // A clean, fast win: every pair in a row, half the bulb left.
            val score = (1..night.pairs).sumOf { PAIR_POINTS * minOf(MAX_MULTIPLIER, it) } + (night.roundMs / 2000).toInt() * SECOND_POINTS
            val round = Round(
                phase = Phase.Won, night = night, deck = deal(night), left = night.roundMs / 2, misses = 0,
                score = score, bestMultiplier = MAX_MULTIPLIER, bestRun = night.pairs,
            )
            val (next, report) = settleRound(progress, round, MothId.Luna)
            counts += unlocks(report)
            progress = next
        }
        assertTrue("unlocks per round were $counts", counts.all { it <= MAX_UNLOCKS })
        assertTrue("rounds 4 to 10 should still give something: $counts", counts.drop(3).any { it > 0 })
        assertTrue("the first round should give something: $counts", counts.first() > 0)
    }

    @Test fun `rewards that don't fit are owed and arrive later`() {
        val (one, first) = settleRound(Progress(), won(), MothId.Luna)
        assertEquals(MAX_UNLOCKS, unlocks(first))
        assertTrue(one.owedBadges.isNotEmpty())
        // A plain lost round later still hands over what was owed.
        val lost = Round(phase = Phase.Lost, left = 0, score = 0)
        val (two, second) = settleRound(one, lost, MothId.Luna)
        assertTrue(second.newBadges.isNotEmpty())
        assertTrue(two.owedBadges.size < one.owedBadges.size)
    }

    @Test fun `quick wings and melody need a board of eight pairs`() {
        val small = won(left = 20_000).copy(bestRun = 6)
        val (a, _) = settleRound(Progress(), small, MothId.Luna)
        assertFalse(Badge.QuickWings in a.badges + a.owedBadges)
        assertFalse(Badge.Melody in a.badges + a.owedBadges)
        val large = won(NIGHTS[1], left = 20_000).copy(bestRun = 8)
        val (b, _) = settleRound(Progress(nights = 2, night = 2), large, MothId.Luna)
        assertTrue(Badge.QuickWings in b.badges + b.owedBadges)
        assertTrue(Badge.Melody in b.badges + b.owedBadges)
    }

    @Test fun `night one shortens after a first win`() {
        assertEquals(30_000L, nightFor(1, seasoned = false).roundMs)
        assertEquals(24_000L, nightFor(1, seasoned = true).roundMs)
        assertEquals(NIGHTS[1], nightFor(2, seasoned = true))
    }

    @Test fun `the daily porch is scored once a day`() {
        val daily = won(DAILY).copy(daily = true)
        val (first, report) = settleRound(Progress(), daily, MothId.Luna, today = 20261114)
        assertTrue(report.xp > 0)
        assertTrue(first.playedDaily(20261114))
        val (_, again) = settleRound(first, daily, MothId.Luna, today = 20261114)
        assertTrue(again.practice)
        assertEquals(0, again.xp)
    }

    @Test fun `daily streaks count consecutive days`() {
        val daily = won(DAILY).copy(daily = true)
        val (one, _) = settleRound(Progress(), daily, MothId.Luna, today = 20261112, yesterday = 20261111)
        val (two, _) = settleRound(one, daily, MothId.Luna, today = 20261113, yesterday = 20261112)
        val (three, _) = settleRound(two, daily, MothId.Luna, today = 20261114, yesterday = 20261113)
        assertEquals(3, three.dailyStreak)
        assertTrue(Badge.Regular in three.badges + three.owedBadges)
        val (gap, _) = settleRound(three, daily, MothId.Luna, today = 20261120, yesterday = 20261119)
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

    @Test fun `each night keeps its best stars`() {
        val (one, _) = settleRound(Progress(), won(misses = 1), MothId.Luna)
        assertEquals(2, one.nightStars[0])
        val (two, _) = settleRound(one, won(left = 2_000, misses = 3), MothId.Luna)
        assertEquals(2, two.nightStars[0]) // a worse round doesn't lower it
        val (three, _) = settleRound(two, won(), MothId.Luna)
        assertEquals(3, three.nightStars[0])
        assertEquals(0, three.nightStars[1])
    }

    @Test fun `the daily porch keeps the player's own history`() {
        val daily = won(DAILY).copy(daily = true)
        val (one, _) = settleRound(Progress(), daily, MothId.Luna, today = 20261113, yesterday = 20261112)
        val (two, _) = settleRound(one, daily, MothId.Luna, today = 20261114, yesterday = 20261113)
        assertEquals(listOf(20261113L, 20261114L), two.dailyLog.map { it.day })
        val (again, _) = settleRound(two, daily, MothId.Luna, today = 20261114, yesterday = 20261113)
        assertEquals(2, again.dailyLog.size) // a practice round is not logged
    }

    @Test fun `every lead has stats for its showcase`() {
        LEADS.forEach {
            val stats = leadStats(it)
            assertTrue(stats.time in 1..3 && stats.look in 1..3 && stats.streak in 1..3)
        }
        assertEquals(3, leadStats(MothId.Luna).time)
        assertEquals(3, leadStats(MothId.Wick).streak)
        assertEquals(3, leadStats(MothId.Iris).look)
    }
}
