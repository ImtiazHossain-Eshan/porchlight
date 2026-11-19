package app.porchlight.game

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTest {
    private fun won(level: Level = LEVELS[0], left: Long = level.roundMs / 2, misses: Int = 0) =
        Round(phase = Phase.Won, level = level, left = left, misses = misses, score = 2_000, deck = deal(level))

    private fun lost(level: Level = LEVELS[0]) = Round(phase = Phase.Lost, level = level, left = 0, score = 0, deck = deal(level))

    /** Every level up to [upTo] cleared with [stars] stars, every badge already held (so badges pay nothing here). */
    private fun cleared(upTo: Int, stars: Int = 1) = Progress(
        reached = upTo + 1, current = upTo + 1, badges = Badge.entries.toSet(),
        levelStars = List(LEVELS.size) { if (it < upTo) stars else 0 },
    )

    /**
     * A seasoned player: every badge held and every lead open, just past a level up with thousands of
     * XP to the next, so only the row under test pays.
     */
    private val veteran = Progress(xp = xpFor(141), badges = Badge.entries.toSet())

    private fun Report.paid(source: Source) = earnings.filter { it.source == source }.sumOf { it.amount }

    // ---------------------------------------------------------------------------------------------
    // The table

    @Test fun `the earning table`() {
        assertEquals(listOf(3, 5, 8), Economy.NEW_STAR)
        assertEquals(1, Economy.REPLAY_WIN)
        assertEquals(25, Economy.BOSS_FIRST_CLEAR)
        assertEquals(50, Economy.CHAPTER_COMPLETE)
        assertEquals(20, Economy.BADGE)
        assertEquals(15, Economy.LEVEL_UP)
        assertEquals(5, Economy.FIRST_OPEN)
        assertEquals(200, Economy.LEDGER_SIZE)
    }

    @Test fun `the daily porch pays ten plus five a streak day, capped at thirty`() {
        assertEquals(listOf(10, 15, 20, 25, 30, 30, 30), (1..7).map(Economy::daily))
        assertEquals(10, Economy.daily(0))
    }

    // ---------------------------------------------------------------------------------------------
    // Stars

    @Test fun `three new stars at once pay all three rows`() {
        val (after, report) = settleRound(veteran, won(), MothId.Luna)
        assertEquals(3, report.stars)
        assertEquals(3 + 5 + 8, report.paid(Source.NewStar))
        assertEquals(0, report.paid(Source.ReplayWin))
        assertEquals(16, after.wallet.balance)
    }

    @Test fun `each star pays once, as it is first earned`() {
        val level = levelAt(4)
        val (one, first) = settleRound(veteran, won(level, left = level.roundMs / 4 - 1), MothId.Luna)
        assertEquals(1, first.stars)
        assertEquals(3, first.paid(Source.NewStar))
        val (two, second) = settleRound(one, won(level, left = level.roundMs / 4, misses = 9), MothId.Luna)
        assertEquals(2, second.stars)
        assertEquals(5, second.paid(Source.NewStar))
        val (three, third) = settleRound(two, won(level), MothId.Luna)
        assertEquals(3, third.stars)
        assertEquals(8, third.paid(Source.NewStar))
        assertEquals(16, three.wallet.balance)
    }

    @Test fun `stars can't be farmed - a replay pays one firefly`() {
        var progress = settleRound(veteran, won(), MothId.Luna).first
        val start = progress.wallet.balance
        repeat(10) {
            val (next, report) = settleRound(progress, won(), MothId.Luna)
            assertEquals(0, report.paid(Source.NewStar))
            assertEquals(Economy.REPLAY_WIN, report.fireflies)
            progress = next
        }
        assertEquals(start + 10, progress.wallet.balance)
    }

    @Test fun `a worse replay win pays the replay row, never a star`() {
        val level = levelAt(4)
        val best = settleRound(veteran, won(level), MothId.Luna).first
        val (_, report) = settleRound(best, won(level, left = level.roundMs / 4 - 1), MothId.Luna)
        assertEquals(listOf(Earning.ReplayWin), report.earnings)
    }

    @Test fun `a lost round pays nothing`() {
        val (after, report) = settleRound(veteran, lost(), MothId.Luna)
        assertTrue(report.earnings.isEmpty())
        assertEquals(0, after.wallet.balance)
    }

    // ---------------------------------------------------------------------------------------------
    // Boss and chapter

    @Test fun `beating a boss the first time pays the boss and the chapter`() {
        val (after, report) = settleRound(cleared(9), won(levelAt(10), left = 1_000), MothId.Luna)
        assertEquals(1, report.chapterDone)
        assertEquals(Economy.BOSS_FIRST_CLEAR, report.paid(Source.BossFirstClear))
        assertEquals(Economy.CHAPTER_COMPLETE, report.paid(Source.ChapterComplete))
        // And a boss replay pays neither again.
        val (_, again) = settleRound(after, won(levelAt(10)), MothId.Luna)
        assertEquals(0, again.paid(Source.BossFirstClear))
        assertEquals(0, again.paid(Source.ChapterComplete))
    }

    @Test fun `a chapter is complete only when all ten levels are cleared`() {
        // Level 5 was never cleared (a skipped level keeps 0 stars): beating the boss is not enough.
        val gap = cleared(9).let { p -> p.copy(levelStars = p.levelStars.mapIndexed { i, s -> if (i == 4) 0 else s }) }
        val (boss, bossReport) = settleRound(gap, won(levelAt(10), left = 1_000), MothId.Luna)
        assertEquals(Economy.BOSS_FIRST_CLEAR, bossReport.paid(Source.BossFirstClear))
        assertEquals(0, bossReport.paid(Source.ChapterComplete))
        // Clearing the gap later completes it, once.
        val (filled, fillReport) = settleRound(boss.copy(current = 5), won(levelAt(5)), MothId.Luna)
        assertEquals(Economy.CHAPTER_COMPLETE, fillReport.paid(Source.ChapterComplete))
        val (_, again) = settleRound(filled, won(levelAt(5)), MothId.Luna)
        assertEquals(0, again.paid(Source.ChapterComplete))
    }

    // ---------------------------------------------------------------------------------------------
    // Daily, badges, level ups

    @Test fun `the daily porch pays by streak, once a day`() {
        val daily = won(DAILY).copy(daily = true)
        val (one, first) = settleRound(veteran, daily, MothId.Luna, today = 20261112, yesterday = 20261111)
        assertEquals(10, first.paid(Source.Daily))
        val (two, second) = settleRound(one, daily, MothId.Luna, today = 20261113, yesterday = 20261112)
        assertEquals(15, second.paid(Source.Daily))
        // Practice on the same day: nothing at all.
        val (_, practice) = settleRound(two, daily, MothId.Luna, today = 20261113, yesterday = 20261112)
        assertTrue(practice.practice)
        assertTrue(practice.earnings.isEmpty())
        // A daily round never pays star rows: its stars aren't a level's.
        assertEquals(0, first.paid(Source.NewStar) + first.paid(Source.ReplayWin))
    }

    @Test fun `a long streak is capped`() {
        val daily = won(DAILY).copy(daily = true)
        val streaky = veteran.copy(dailyDay = 20261113, dailyStreak = 20)
        val (_, report) = settleRound(streaky, daily, MothId.Luna, today = 20261114, yesterday = 20261113)
        assertEquals(Economy.DAILY_CAP, report.paid(Source.Daily))
    }

    @Test fun `a lost daily porch still pays for playing`() {
        val daily = lost(DAILY).copy(daily = true)
        val (_, report) = settleRound(veteran, daily, MothId.Luna, today = 20261114)
        assertEquals(10, report.paid(Source.Daily))
    }

    @Test fun `each badge handed over pays`() {
        val (_, report) = settleRound(Progress(xp = xpFor(141)), lost(), MothId.Luna)
        assertEquals(listOf(Badge.FirstLight), report.newBadges)
        assertEquals(Economy.BADGE, report.paid(Source.Badge))
    }

    @Test fun `each level up pays`() {
        val (_, report) = settleRound(Progress(badges = Badge.entries.toSet(), xp = xpFor(2) - 1), lost(), MothId.Luna)
        assertTrue(report.levelAfter > report.levelBefore)
        assertEquals(Economy.LEVEL_UP * (report.levelAfter - report.levelBefore), report.paid(Source.LevelUp))
    }

    @Test fun `the report's total is every line added up, and lands in the wallet`() {
        val (after, report) = settleRound(Progress(), won(), MothId.Luna, now = 42)
        assertEquals(report.earnings.sumOf { it.amount }, report.fireflies)
        assertEquals(report.fireflies, after.wallet.balance)
        assertEquals(report.fireflies, after.wallet.earned)
        assertEquals(report.earnings.size, after.wallet.ledger.size)
        assertTrue(after.wallet.ledger.all { it.time == 42L && it.amount > 0 })
    }

    // ---------------------------------------------------------------------------------------------
    // The daily gift

    @Test fun `the first open of a day gives a gift, once`() {
        val (opened, gift) = openToday(Progress(), day = 20261114, now = 1)
        assertEquals(Economy.FIRST_OPEN, gift)
        assertEquals(Economy.FIRST_OPEN, opened.wallet.balance)
        val (again, none) = openToday(opened, day = 20261114, now = 2)
        assertEquals(0, none)
        assertEquals(opened, again)
        val (tomorrow, more) = openToday(again, day = 20261115, now = 3)
        assertEquals(Economy.FIRST_OPEN, more)
        assertEquals(2 * Economy.FIRST_OPEN, tomorrow.wallet.balance)
    }

    @Test fun `a supporter's gift is doubled`() {
        assertEquals(Economy.FIRST_OPEN * Economy.SUPPORTER_GIFT_MULTIPLIER, openToday(Progress(), 1, 1, supporter = true).second)
    }

    // ---------------------------------------------------------------------------------------------
    // Spending

    @Test fun `spending takes the price and keeps a ledger line`() {
        val rich = Progress(wallet = Wallet(balance = 100, earned = 100))
        val result = spend(rich, Cost("boost.time", 15), now = 7)
        assertTrue(result is SpendResult.Spent)
        val wallet = (result as SpendResult.Spent).progress.wallet
        assertEquals(85, wallet.balance)
        assertEquals(15, wallet.spent)
        assertEquals(100, wallet.earned)
        assertEquals(LedgerEntry("Spend/boost.time", -15, 7), wallet.ledger.last())
    }

    @Test fun `spending more than the balance fails cleanly and changes nothing`() {
        val poor = Progress(wallet = Wallet(balance = 14))
        val result = spend(poor, Cost("boost.time", 15), now = 7)
        assertEquals(SpendResult.Short(price = 15, balance = 14), result)
        assertEquals(1, (result as SpendResult.Short).missing)
    }

    @Test fun `spending exactly the balance leaves zero, never less`() {
        val exact = Progress(wallet = Wallet(balance = 15))
        val after = (spend(exact, Cost("x", 15), 0) as SpendResult.Spent).progress
        assertEquals(0, after.wallet.balance)
        assertTrue(spend(after, Cost("x", 1), 0) is SpendResult.Short)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a free or negative price is refused as a mistake`() {
        spend(Progress(wallet = Wallet(balance = 100)), Cost("x", -5), 0)
    }

    @Test fun `the ledger keeps the last two hundred lines`() {
        var wallet = Wallet()
        repeat(250) { wallet = wallet.credit("t", 1, it.toLong()) }
        assertEquals(Economy.LEDGER_SIZE, wallet.ledger.size)
        assertEquals(50L, wallet.ledger.first().time)
        assertEquals(249L, wallet.ledger.last().time)
        assertEquals(250, wallet.balance)
    }

    // ---------------------------------------------------------------------------------------------
    // Save and restore

    private val sample = Wallet(
        balance = 321, earned = 900, spent = 579, lastOpenDay = 20261114,
        purchases = setOf("gpa.1234-5678", "token:with|odd;chars"),
        ledger = listOf(LedgerEntry("NewStar", 3, 10), LedgerEntry("Spend/look.back.ember", -200, 11)),
    )

    @Test fun `the wallet saves and reads back`() {
        // Nothing is lost, not even ids holding the save format's own separators.
        assertEquals(sample, Wallet.decode(sample.encode()))
    }

    @Test fun `an empty wallet saves and reads back`() {
        assertEquals(Wallet(), Wallet.decode(Wallet().encode()))
    }

    @Test fun `junk reads as an empty wallet, never a crash`() {
        for (junk in listOf(null, "", "garbage", "v2|1|2|3|4||", "v1|x|2|3|4||", "v1|5")) {
            assertEquals(Wallet(), Wallet.decode(junk))
        }
        // A negative balance can't be restored.
        assertEquals(0, Wallet.decode("v1|-50|0|0|0||").balance)
        // Broken ledger lines are dropped, good ones kept.
        assertEquals(listOf(LedgerEntry("a", 1, 2)), Wallet.decode("v1|1|1|0|0||a:1:2;bad;c:x:3").ledger)
    }

    @Test fun `the store keeps the wallet across a restart`() {
        val prefs = MemoryPrefs()
        val store = Store(prefs)
        store.change { it.copy(wallet = sample) }
        assertEquals(sample.balance, Store(prefs).progress.wallet.balance)
        assertEquals(sample.ledger, Store(prefs).progress.wallet.ledger)
        // A win and the daily gift are saved too.
        store.finish(won(), MothId.Luna, now = 99)
        assertEquals(Economy.FIRST_OPEN, store.open(day = 20261115, now = 100))
        val back = Store(prefs).progress.wallet
        assertEquals(store.progress.wallet, back)
        assertEquals(20261115L, back.lastOpenDay)
        assertEquals(0, store.open(day = 20261115, now = 101))
        assertEquals(Economy.FIRST_OPEN, store.gift)
    }

    @Test fun `an old save without a wallet opens with an empty one`() {
        val prefs = MemoryPrefs()
        prefs.edit().putInt("xp", 500).putInt("schema", SCHEMA).apply()
        val progress = Store(prefs).progress
        assertEquals(500, progress.xp)
        assertEquals(Wallet(), progress.wallet)
        assertNull(prefs.getString("wallet", null))
        assertFalse(progress.wallet.balance < 0)
    }
}

/** A SharedPreferences kept in memory, for tests of the Store. */
class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = (values[key] as Set<String>?)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            removed.forEach { values.remove(it) }
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        }
    }
}
