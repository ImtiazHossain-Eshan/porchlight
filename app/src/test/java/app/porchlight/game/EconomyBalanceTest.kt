package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** One simulated day of a free player. */
data class EconDay(
    val day: Int,
    val earned: Int,
    val spent: Int,
    val balance: Int,
    val level: Int,
    val reached: Int,
    val stars: Int,
    val chapters: Int,
    val looks: Int,
    /** Rounds this day spent replaying for stars because the next chapter was waiting at its gate. */
    val gateRounds: Int,
    /** Chapters opened early this day with Fireflies earned by playing (never money). */
    val gatesBought: Int,
)

/**
 * An average free player, day by day: no money spent, never a boost, never a skip. Each day they open
 * the app (the daily gift), play the daily porch, watch two ads in the shop, and play 19 more rounds:
 * the newest level when it is open; otherwise (waiting at a chapter's star gate) they replay the
 * easiest level still short of three stars. After three losses in a row they take the free "Easier
 * bulb". If half a day of replays hasn't opened the gate, they open it with Fireflies they earned
 * (never money); while waiting they save for that rather than buying looks. Otherwise, whenever they
 * hold enough for a typical look they buy the cheapest one they don't own: that is the "one cosmetic
 * a week" the economy is meant to allow.
 */
object EconomySim {
    const val ROUNDS_A_DAY = 20
    const val ADS_A_DAY = 2

    /** Replays at a gate before the player opens it with earned Fireflies instead. */
    const val PATIENCE = 10

    /** A typical look's price: what "about one cosmetic a week" is measured in. */
    val TYPICAL_LOOK = Economy.LOOK_PRICES.values.sorted().let { it[it.size / 2] }

    fun run(days: Int, skill: Skill = Skill.Average, seed: Long = 7): List<EconDay> {
        var p = Progress(tutorialDone = true, birthYear = 2000)
        val out = mutableListOf<EconDay>()
        var round = 0L
        var waited = 0
        val firstDay = 20261101L
        for (d in 1..days) {
            val today = firstDay + d
            val before = p.wallet
            var gate = 0
            var bought = 0
            p = openToday(p, today, d * 1_000L).first
            // The daily porch.
            val daily = Simulator.playRound(DAILY, skill, seed * 7_919 + d)
            p = settleRound(p, daily.copy(daily = true), MothId.Luna, today, today - 1, d * 1_000L).first
            // Two ads in the shop.
            repeat(ADS_A_DAY) { p = rewardShopAd(p, today, d * 1_000L) ?: p }
            // The rest of the day's rounds.
            repeat(ROUNDS_A_DAY - 1) {
                val waiting = p.waitingChapter
                if (waiting != null && waited >= PATIENCE) {
                    (openChapterEarly(p, waiting, d * 1_000L) as? SpendResult.Spent)?.let {
                        p = it.progress
                        bought++
                        waited = 0
                    }
                }
                val stillWaiting = p.waitingChapter != null
                val number = when {
                    !stillWaiting && p.isOpen(p.reached) -> p.reached
                    else -> p.replays.minOrNull() ?: p.reached
                }
                if (stillWaiting) {
                    gate++
                    waited++
                } else {
                    waited = 0
                }
                p = p.copy(current = number)
                val easy = p.easierOffered
                val played = Simulator.playRound(levelAt(number), skill, seed * 1_000_003 + (round++), easy = easy)
                p = settleRound(p, played, MothId.Luna, today, today - 1, d * 1_000L).first
            }
            // A look, when one is affordable (but not while saving to open a waiting chapter).
            while (p.waitingChapter == null && p.wallet.balance >= TYPICAL_LOOK) {
                val look = LOOK_CATALOG.filter { it.price != null && !p.owns(it) && p.shows(it, 6) }.minByOrNull { it.price!! } ?: break
                p = (buyLook(p, look, 6, d * 1_000L) as? SpendResult.Spent)?.progress ?: break
            }
            out += EconDay(
                day = d,
                earned = p.wallet.earned - before.earned,
                spent = p.wallet.spent - before.spent,
                balance = p.wallet.balance,
                level = p.level,
                reached = p.reached,
                stars = p.totalStars,
                chapters = (1..CHAPTERS.size).count { p.chapterOpen(it) },
                looks = p.owned.size,
                gateRounds = gate,
                gatesBought = bought,
            )
        }
        return out
    }

    /** The 30-day table, as Markdown for docs/ECONOMY.md. */
    fun table(days: List<EconDay>): String = buildString {
        appendLine("| Day | Earned | Spent | Balance | Newest level | Stars | Chapters open | Looks owned | Rounds at a gate | Gates opened with Fireflies |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        for (d in days) {
            appendLine("| ${d.day} | ${d.earned} | ${d.spent} | ${d.balance} | ${d.reached} | ${d.stars} | ${d.chapters} | ${d.looks} | ${d.gateRounds} | ${d.gatesBought} |")
        }
    }
}

class EconomyBalanceTest {
    private val month = EconomySim.run(30)

    @Test fun `every chapter gate can be met with stars alone`() {
        // Each gate needs no more stars than the levels before it hold, with room to spare, even if
        // every one of them were cleared with only two stars (as a boosted round can earn).
        for (chapter in CHAPTERS.drop(1)) {
            val before = LEVELS.filter { it.chapter < chapter.number }.size
            assertTrue("chapter ${chapter.number}", chapter.gate <= before * Economy.BOOSTED_MAX_STARS)
        }
    }

    @Test fun `a free player is never blocked from progress`() {
        // Nothing is bought with money, yet the gates keep opening: never a whole day stuck at a gate,
        // and every chapter open within the month, for several different runs of luck.
        for (seed in 1L..5L) {
            val run = EconomySim.run(30, seed = seed)
            assertTrue("seed $seed stuck at a gate a whole day: ${run.filter { it.gateRounds >= EconomySim.ROUNDS_A_DAY - 1 }}",
                run.none { it.gateRounds >= EconomySim.ROUNDS_A_DAY - 1 })
            assertTrue("seed $seed: every chapter open within the month", run.last().chapters == CHAPTERS.size)
            assertEquals("seed $seed: reached the last level", LEVELS.size, run.last().reached)
        }
        for (week in month.chunked(7).dropLast(1).zipWithNext()) {
            val (a, b) = week
            val stuck = b.last().reached == a.last().reached && b.last().reached < LEVELS.size
            assertTrue("no new level in week ending day ${b.last().day}", !stuck)
        }
        assertTrue("every chapter open within the month", month.last().chapters == CHAPTERS.size)
    }

    @Test fun `a free player can afford about one look a week`() {
        // Every week of the month, including the quiet weeks after the levels run out.
        for (week in month.chunked(7).filter { it.size == 7 }) {
            val start = month.getOrNull(week.first().day - 2)?.looks ?: 0
            assertTrue("week ending day ${week.last().day}: ${week.last().looks - start} looks", week.last().looks - start >= 1)
        }
    }

    @Test fun `money can't buy a third star or a better daily porch`() {
        val level = levelAt(12)
        val sorted = deal(level).sorted()
        // The best possible boosted round, with every boost: still two stars.
        var round = Round.start(MothId.Luna, level, boosts = Boost.entries.toSet()).copy(deck = sorted).go(0).secondLook().unflash().lightPair(0)
        for (i in sorted.indices step 2) if (!round.matched[i]) round = round.flip(i, 0).flip(i + 1, 0)
        assertEquals(Phase.Won, round.phase)
        assertTrue(starsFor(round) <= Economy.BOOSTED_MAX_STARS)
        // A skipped level has no stars; a chapter opened early adds none.
        val stuck = Progress(reached = 7, current = 7, levelStars = List(6) { 1 } + List(44) { 0 }, lossLevel = 7, losses = 3, wallet = Wallet(balance = 10_000))
        val skipped = (skipLevel(stuck, 1) as SpendResult.Spent).progress
        assertEquals(stuck.totalStars, skipped.totalStars)
        // Everything for sale, bought at once, changes no star and no daily round.
        var rich = Progress(wallet = Wallet(balance = 1_000_000))
        for ((i, product) in Product.entries.withIndex()) rich = grant(rich, product, "t$i", 0)!!
        for (look in LOOK_CATALOG) rich = (buyLook(rich, look, look.season?.months?.first() ?: 6, 0) as? SpendResult.Spent)?.progress ?: rich
        for (lead in LEADS) rich = (buyLeadEarly(rich, lead, 0) as? SpendResult.Spent)?.progress ?: rich
        assertEquals(0, rich.totalStars)
        val daily = Round.daily(MothId.Luna, 20261119)
        assertEquals(Round.daily(MothId.Luna, 20261119), Round.start(MothId.Luna, DAILY, kotlin.random.Random(20261119), 20261119, boosts = Boost.entries.toSet()))
        assertTrue(Boost.entries.none { daily.go(0).canUse(it) })
    }

    /** `PORCH_ECONOMY=docs ./gradlew testDebugUnitTest --tests '*EconomyBalanceTest*'` writes the table. */
    @Test fun `print the month`() {
        val table = EconomySim.table(month)
        println(table)
        if (System.getenv("PORCH_ECONOMY") != "docs") return
        val doc = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "docs/ECONOMY.md") }.first { it.exists() }
        val text = doc.readText()
        val start = "<!-- economy-sim:start -->"
        val end = "<!-- economy-sim:end -->"
        val body = "$start\n$table$end"
        doc.writeText(if (start in text) text.substring(0, text.indexOf(start)) + body + text.substring(text.indexOf(end) + end.length) else text.trimEnd() + "\n\n" + body + "\n")
    }
}
