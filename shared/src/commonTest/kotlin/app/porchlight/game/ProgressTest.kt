package app.porchlight.game

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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

    // Level 4: 36 s, so a quarter of the bulb is 9 s; three stars allow 2 misses in chapter 1.
    private val four get() = levelAt(4)

    @Test fun `a slow win earns one star`() {
        assertEquals(1, starsFor(Round(phase = Phase.Won, level = four, left = four.roundMs / 4 - 1, misses = 0)))
    }

    @Test fun `a quarter of the bulb left earns two stars`() {
        assertEquals(2, starsFor(Round(phase = Phase.Won, level = four, left = four.roundMs / 4, misses = 3)))
    }

    @Test fun `a quick win with few misses earns three stars`() {
        assertEquals(3, starsFor(Round(phase = Phase.Won, level = four, left = four.roundMs / 2, misses = 2)))
        assertEquals(2, starsFor(Round(phase = Phase.Won, level = four, left = four.roundMs / 2, misses = 3)))
    }

    @Test fun `three stars allow more misses in later chapters and one fewer on a boss`() {
        assertEquals(listOf(2, 2, 3, 3, 4), listOf(1, 11, 21, 31, 41).map { levelAt(it).maxMissesFor3Stars })
        assertEquals(listOf(1, 1, 2, 2, 3), listOf(10, 20, 30, 40, 50).map { levelAt(it).maxMissesFor3Stars })
    }

    @Test fun `the easier bulb earns one star at most`() {
        val easy = Round(phase = Phase.Won, level = four, left = four.roundMs, misses = 0, easy = true)
        assertEquals(1, starsFor(easy))
    }

    @Test fun `the daily porch keeps its own star rule`() {
        val daily = Round(phase = Phase.Won, level = DAILY, daily = true, left = 20_000, misses = 0)
        assertEquals(3, starsFor(daily))
        assertEquals(2, starsFor(daily.copy(misses = 1)))
        assertEquals(1, starsFor(daily.copy(left = 2_000, misses = 1)))
    }

    @Test fun `streak multiplier caps at four`() {
        val rules = Rules()
        assertEquals(listOf(1, 2, 3, 4, 4, 4), (0..5).map { multiplierAt(it, rules) })
    }

    @Test fun `every badge has a title and a rule`() {
        assertEquals(17, Badge.entries.size)
        assertEquals((1..5).map { Badge.forChapter(it) }, Badge.entries.filter { it.mold == Mold.Stamp })
        Badge.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.detail.endsWith("."))
        }
        // No two of the play badges are the same metal and mold; the chapter stamps share a mold.
        val play = Badge.entries.filter { it.mold != Mold.Stamp }
        assertEquals(play.size, play.map { it.metal to it.mold }.toSet().size)
    }

    @Test fun `every moth has a perk`() {
        LEADS.forEach {
            assertTrue(it.perkName.isNotBlank())
            assertTrue(it.perk.endsWith("."))
        }
    }

    private fun won(level: Level = LEVELS[0], left: Long = level.roundMs / 2, misses: Int = 0, score: Int = 2_000) =
        Round(phase = Phase.Won, level = level, left = left, misses = misses, score = score, deck = deal(level))

    private fun lost(level: Level = LEVELS[0]) = Round(phase = Phase.Lost, level = level, left = 0, score = 0, deck = deal(level))

    /** Every level up to [upTo] cleared with [stars] stars. */
    private fun cleared(upTo: Int, stars: Int = 1) =
        Progress(reached = upTo + 1, current = upTo + 1, levelStars = List(LEVELS.size) { if (it < upTo) stars else 0 })

    @Test fun `there are fifty levels in five chapters of ten`() {
        assertEquals(50, LEVELS.size)
        assertEquals((1..50).toList(), LEVELS.map { it.number })
        for (chapter in CHAPTERS) {
            assertEquals(10, chapter.levels.size)
            assertEquals(Kind.Boss, chapter.boss.kind)
            assertEquals(1, chapter.levels.count { it.breather })
        }
        assertEquals(listOf(0, 15, 35, 55, 80), CHAPTERS.map { it.gate })
    }

    @Test fun `every board is one of the six grids - no bigger than 4 by 6`() {
        for (level in LEVELS) {
            assertTrue(level.cards in GRIDS, "level ${level.number} has ${level.cards} cards")
            assertTrue(level.cards <= 24)
            assertTrue(level.pairs <= BOARD_MOTHS.size)
            assertEquals(level.leaves > 0, Twist.Leaves in level)
            assertTrue(level.twists.size <= if (level.number == 50) 3 else 2, "level ${level.number} has too many twists")
        }
        assertEquals(12, BOARD_MOTHS.size)
        assertEquals(3, levelAt(50).twists.size)
    }

    @Test fun `each twist arrives alone on a small board`() {
        for (twist in Twist.entries) {
            val first = LEVELS.first { twist in it }
            assertEquals(twist, introducedOn(first))
            assertEquals(setOf(twist), first.twists, "$twist arrives with others")
            assertTrue(first.cards <= 16, "$twist arrives on a big board")
        }
        assertEquals(listOf(11, 21, 31, 34), LEVELS.filter { introducedOn(it) != null }.map { it.number })
        assertEquals(null, introducedOn(levelAt(12)))
    }

    @Test fun `the two new moths arrive on level 36`() {
        assertTrue(LEVELS.take(35).all { it.pairs <= 10 })
        assertEquals(12, levelAt(36).pairs)
        assertTrue(deal(levelAt(36)).containsAll(listOf(MothId.Ash, MothId.Tide)))
    }

    @Test fun `a lookalikes board holds a moth and its near-twin`() {
        for (seed in 0L until 20L) {
            val deck = deal(levelAt(34), kotlin.random.Random(seed))
            val twins = TWINS.filter { (twin, of) -> twin in deck && of in deck }
            assertEquals(1, twins.size)
            assertEquals(16, deck.size)
            assertEquals(8, deck.toSet().size)
        }
        assertTrue(TWINS.keys.none { it in deal(levelAt(36)) })
    }

    @Test fun `the daily porch deals as it always has`() {
        val deck = deal(DAILY, kotlin.random.Random(20261114))
        assertTrue(deck.all { it in DAILY_MOTHS })
        assertEquals(10, DAILY_MOTHS.size)
    }

    @Test fun `any win on your newest level opens the next`() {
        val (after, report) = settleRound(Progress(), won(left = 1_000, misses = 5), MothId.Luna)
        assertEquals(1, after.levelStars[0])
        assertEquals(2, after.reached)
        assertEquals(2, after.current)
        assertEquals(2, report.newLevel)
    }

    @Test fun `a loss opens nothing`() {
        val (after, report) = settleRound(Progress(), lost(), MothId.Luna)
        assertEquals(1, after.reached)
        assertEquals(null, report.newLevel)
    }

    @Test fun `replaying an old level opens nothing new`() {
        val (after, _) = settleRound(cleared(4).copy(current = 1), won(), MothId.Luna)
        assertEquals(5, after.reached)
        assertEquals(1, after.current)
    }

    @Test fun `chapters wait behind their star gates`() {
        // All of chapter 1 cleared with one star: 10 stars, short of the 15 chapter 2 needs.
        val (after, report) = settleRound(cleared(9), won(levelAt(10), left = 1_000), MothId.Luna)
        assertEquals(11, after.reached)
        assertEquals(null, report.newLevel)
        assertFalse(after.isOpen(11))
        assertEquals(2, after.waitingChapter)
        assertEquals(5, after.starsTo(2))
        assertEquals(10, after.current) // the pick stays put until the gate opens
        // Stars won back on an old level open the gate.
        val replay = after.copy(current = 3)
        val (opened, news) = settleRound(replay, won(levelAt(3), left = levelAt(3).roundMs, misses = 0), MothId.Luna)
        assertEquals(12, opened.totalStars)
        assertFalse(opened.isOpen(11))
        val (more, _) = settleRound(opened, won(levelAt(4), left = levelAt(4).roundMs), MothId.Luna)
        val (gate, gateNews) = settleRound(more, won(levelAt(5), left = levelAt(5).roundMs), MothId.Luna)
        assertTrue(gate.isOpen(11))
        assertEquals(2, gateNews.newChapter)
        assertEquals(null, news.newChapter)
    }

    @Test fun `with stars enough the boss opens the next chapter at once`() {
        val (after, report) = settleRound(cleared(9, stars = 2), won(levelAt(10)), MothId.Luna)
        assertTrue(after.isOpen(11))
        assertEquals(11, report.newLevel)
        assertEquals(1, report.chapterDone)
        assertTrue(Badge.Chapter1 in report.newBadges)
    }

    @Test fun `the best levels to replay are the ones with fewest stars`() {
        val stars = List(LEVELS.size) { i -> when (i) { 0 -> 3; 1 -> 2; 2 -> 1; 3 -> 1; else -> 0 } }
        val progress = Progress(reached = 5, levelStars = stars)
        assertEquals(listOf(4, 3, 2), progress.replays)
    }

    @Test fun `three losses in a row offer the easier bulb`() {
        var progress = cleared(6).copy(current = 7)
        repeat(2) { progress = settleRound(progress, lost(levelAt(7)), MothId.Luna).first }
        assertFalse(progress.easierOffered)
        progress = settleRound(progress, lost(levelAt(7)), MothId.Luna).first
        assertTrue(progress.easierOffered)
        // Another level breaks the run; a win ends it.
        assertFalse(settleRound(progress, lost(levelAt(3)), MothId.Luna).first.copy(current = 7).easierOffered)
        assertFalse(settleRound(progress, won(levelAt(7)), MothId.Luna).first.easierOffered)
    }

    @Test fun `boss wins earn half again as much xp`() {
        val normal = won(levelAt(9))
        val boss = won(levelAt(10))
        assertEquals(xpFor(normal, 3) * 3 / 2, xpFor(boss, 3))
        assertEquals(xpFor(lost(levelAt(9)), 0), xpFor(lost(levelAt(10)), 0))
    }

    @Test fun `three stars on level ten or later masters the lead`() {
        val (easy, none) = settleRound(Progress(), won(), MothId.Luna)
        assertFalse(MothId.Luna in easy.mastered) // the first levels are too easy to count
        assertEquals(null, none.newMastery)

        val (after, report) = settleRound(cleared(15).copy(xp = 5_000), won(levelAt(12)), MothId.Luna)
        assertTrue(MothId.Luna in after.mastered)
        assertEquals(MothId.Luna, report.newMastery)
    }

    private fun unlocks(report: Report) =
        (if (report.newLevel != null) 1 else 0) + report.newMoths.size + (if (report.newMastery != null) 1 else 0) +
            report.newBadges.count { it.mold != Mold.Stamp }

    @Test fun `a strong new player never gets more than two unlocks a round`() {
        var progress = Progress()
        val counts = mutableListOf<Int>()
        repeat(14) {
            val level = levelAt(progress.current)
            // A clean, fast win: every pair in a row, half the bulb left.
            val score = (1..level.pairs).sumOf { PAIR_POINTS * minOf(MAX_MULTIPLIER, it) } + (level.roundMs / 2000).toInt() * SECOND_POINTS
            val round = Round(
                phase = Phase.Won, level = level, deck = deal(level), left = level.roundMs / 2, misses = 0,
                score = score, bestMultiplier = MAX_MULTIPLIER, bestRun = level.pairs,
            )
            val (next, report) = settleRound(progress, round, MothId.Luna)
            counts += unlocks(report)
            progress = next
        }
        assertTrue(counts.all { it <= MAX_UNLOCKS }, "unlocks per round were $counts")
        assertTrue(counts.drop(3).any { it > 0 }, "rounds 4 to 14 should still give something: $counts")
        assertTrue(counts.first() > 0, "the first round should give something: $counts")
        assertEquals(15, progress.current) // three stars a level: straight through the first gate
    }

    @Test fun `rewards that don't fit are owed and arrive later`() {
        val (one, first) = settleRound(Progress(), won(), MothId.Luna)
        assertEquals(MAX_UNLOCKS, unlocks(first))
        assertTrue(one.owedBadges.isNotEmpty())
        // A plain lost round later still hands over what was owed.
        val (two, second) = settleRound(one, lost(), MothId.Luna)
        assertTrue(second.newBadges.isNotEmpty())
        assertTrue(two.owedBadges.size < one.owedBadges.size)
    }

    @Test fun `quick wings and melody need a board of eight pairs`() {
        val small = won(levelAt(4), left = 20_000).copy(bestRun = 6)
        val (a, _) = settleRound(Progress(), small, MothId.Luna)
        assertFalse(Badge.QuickWings in a.badges + a.owedBadges)
        assertFalse(Badge.Melody in a.badges + a.owedBadges)
        val large = won(levelAt(8), left = 20_000).copy(bestRun = 8)
        val (b, _) = settleRound(cleared(7), large, MothId.Luna)
        assertTrue(Badge.QuickWings in b.badges + b.owedBadges)
        assertTrue(Badge.Melody in b.badges + b.owedBadges)
    }

    // Saves from the six-night version

    @Test fun `a fresh install starts at level one`() {
        val fresh = Progress()
        assertEquals(1, fresh.reached)
        assertEquals(1, fresh.current)
        assertEquals(0, fresh.totalStars)
        assertTrue(fresh.isOpen(1))
        assertFalse(fresh.isOpen(2))
        assertEquals(List(50) { 0 }, readStars(null))
    }

    @Test fun `old nights move to their levels`() {
        // Nights 1 and 2 won with 3 and 2 stars, night 3 opened but not yet won.
        val moved = migrateNights(nights = 3, nightStars = "3,2,0,0,0,0")
        assertEquals(3, moved.levelStars[3])  // night 1 -> level 4
        assertEquals(2, moved.levelStars[7])  // night 2 -> level 8
        assertEquals(List(3) { 1 } + 3 + List(3) { 1 } + 2 + List(42) { 0 }, moved.levelStars)
        assertEquals(9, moved.reached)
        assertEquals(9, moved.current)
        assertTrue(moved.isOpen(9))
        assertFalse(moved.isOpen(10))
    }

    @Test fun `a player who beat every night lands at level 32 with chapter 4 open`() {
        val moved = migrateNights(nights = 6, nightStars = "3,3,2,2,1,1")
        assertEquals(listOf(4, 8, 16, 20, 25, 31).map { moved.levelStars[it - 1] }, listOf(3, 3, 2, 2, 1, 1))
        assertEquals(32, moved.reached)
        assertTrue(moved.isOpen(32)) // only about 40 stars, but chapters already reached stay open
        assertTrue((1..31).all { moved.levelStars[it - 1] >= 1 })
        assertEquals(0, moved.levelStars[31])
        assertEquals(4, moved.chapters)
    }

    @Test fun `a corrupted old save migrates safely`() {
        val junk = migrateNights(nights = 99, nightStars = "x,,7,-3")
        // Six nights opened means nights 1 to 5 were won, whatever their stars say.
        assertTrue((1..25).all { junk.levelStars[it - 1] >= 1 })
        assertEquals(3, junk.levelStars[15]) // night 3's "7" reads as 3 stars, on level 16
        assertEquals(26, junk.reached)
        assertTrue(junk.levelStars.all { it in 0..3 })
        val empty = migrateNights(nights = -4, nightStars = null)
        assertEquals(Progress(), empty)
        assertEquals(List(50) { 0 }, readStars("garbage"))
        assertEquals(listOf(2, 0, 3) + List(47) { 0 }, readStars("2,x,9"))
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

    @Test fun `each level keeps its best stars`() {
        val (one, _) = settleRound(Progress(), won(misses = 3), MothId.Luna)
        assertEquals(2, one.levelStars[0])
        val (two, _) = settleRound(one, won(left = 1_000, misses = 3), MothId.Luna)
        assertEquals(2, two.levelStars[0]) // a worse round doesn't lower it
        val (three, _) = settleRound(two, won(), MothId.Luna)
        assertEquals(3, three.levelStars[0])
        assertEquals(0, three.levelStars[1])
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
