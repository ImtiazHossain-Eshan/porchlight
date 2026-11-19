package app.porchlight.game

/** Why Fireflies came in or went out: kept in the ledger, and shown on the results' tally. */
enum class Source(val label: String) {
    NewStar("New star"),
    ReplayWin("Replay win"),
    BossFirstClear("Boss beaten"),
    ChapterComplete("Chapter complete"),
    Daily("Daily porch"),
    Badge("Badge"),
    LevelUp("Level up"),
    FirstOpen("Daily gift"),
    Spend("Spent"),
}

/** One line in the wallet's ledger: what moved, how much (negative when spent), and when. */
data class LedgerEntry(val source: String, val amount: Int, val time: Long)

/**
 * The player's Fireflies. [balance] never goes below zero. [earned] and [spent] are lifetime
 * totals; [purchases] holds the ids of things bought with real money, so each is granted exactly
 * once; [ledger] keeps the last [Economy.LEDGER_SIZE] movements; [lastOpenDay] is the day the
 * daily gift was last given.
 */
data class Wallet(
    val balance: Int = 0,
    val earned: Int = 0,
    val spent: Int = 0,
    val purchases: Set<String> = emptySet(),
    val ledger: List<LedgerEntry> = emptyList(),
    val lastOpenDay: Long = 0,
) {
    /** The wallet as one line of text for saving: totals, then purchases, then the ledger. */
    fun encode(): String = buildString {
        append("v1|$balance|$earned|$spent|$lastOpenDay|")
        append(purchases.joinToString(",") { escape(it) })
        append('|')
        append(ledger.joinToString(";") { "${escape(it.source)}:${it.amount}:${it.time}" })
    }

    companion object {
        /** Ids and sources are percent-encoded, so the separators can't appear in them and nothing is lost. */
        private fun escape(text: String) = java.net.URLEncoder.encode(text, "UTF-8")
        private fun unescape(text: String) = runCatching { java.net.URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

        /** Reads a saved wallet. Anything unreadable reads as an empty wallet, never as a crash. */
        fun decode(text: String?): Wallet {
            if (text.isNullOrBlank()) return Wallet()
            val parts = text.split("|")
            if (parts.size < 7 || parts[0] != "v1") return Wallet()
            val balance = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: return Wallet()
            return Wallet(
                balance = balance,
                earned = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                spent = parts[3].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                lastOpenDay = parts[4].toLongOrNull() ?: 0,
                purchases = parts[5].split(",").filter { it.isNotBlank() }.map(::unescape).toSet(),
                ledger = parts[6].split(";").mapNotNull { entry ->
                    val p = entry.split(":")
                    if (p.size != 3) return@mapNotNull null
                    val amount = p[1].toIntOrNull() ?: return@mapNotNull null
                    val time = p[2].toLongOrNull() ?: return@mapNotNull null
                    LedgerEntry(unescape(p[0]), amount, time)
                }.takeLast(Economy.LEDGER_SIZE),
            )
        }
    }
}

/** Something that pays Fireflies, with the amount from [Economy]. */
sealed class Earning(val source: Source) {
    abstract val amount: Int

    /** The [index]th star of a level (1, 2 or 3), earned for the first time. */
    data class NewStar(val index: Int) : Earning(Source.NewStar) {
        override val amount get() = Economy.NEW_STAR[(index - 1).coerceIn(0, 2)]
    }
    data object ReplayWin : Earning(Source.ReplayWin) { override val amount get() = Economy.REPLAY_WIN }
    data object BossFirstClear : Earning(Source.BossFirstClear) { override val amount get() = Economy.BOSS_FIRST_CLEAR }
    data class ChapterComplete(val chapter: Int) : Earning(Source.ChapterComplete) { override val amount get() = Economy.CHAPTER_COMPLETE }
    data class Daily(val streak: Int) : Earning(Source.Daily) { override val amount get() = Economy.daily(streak) }
    data class BadgeEarned(val badge: Badge) : Earning(Source.Badge) { override val amount get() = Economy.BADGE }
    data class LevelUp(val level: Int) : Earning(Source.LevelUp) { override val amount get() = Economy.LEVEL_UP }
    data class FirstOpen(val supporter: Boolean) : Earning(Source.FirstOpen) {
        override val amount get() = Economy.FIRST_OPEN * (if (supporter) Economy.SUPPORTER_GIFT_MULTIPLIER else 1)
    }
}

/** Adds [amount] Fireflies to the wallet, with a ledger line for [source]. */
fun Wallet.credit(source: String, amount: Int, now: Long): Wallet {
    if (amount <= 0) return this
    return copy(
        balance = balance + amount,
        earned = earned + amount,
        ledger = (ledger + LedgerEntry(source, amount, now)).takeLast(Economy.LEDGER_SIZE),
    )
}

/** Pays [event]'s Fireflies into [progress]'s wallet. */
fun earn(progress: Progress, event: Earning, now: Long): Progress =
    progress.copy(wallet = progress.wallet.credit(event.source.name, event.amount, now))

/** What a purchase with Fireflies costs, and what it is for (kept in the ledger). */
data class Cost(val what: String, val price: Int)

/** The outcome of spending: done, or not enough Fireflies (the wallet is left as it was). */
sealed class SpendResult {
    data class Spent(val progress: Progress) : SpendResult()
    data class Short(val price: Int, val balance: Int) : SpendResult() {
        val missing get() = price - balance
    }
}

/**
 * Takes [cost]'s price out of [progress]'s wallet. Fails cleanly, changing nothing, when the
 * balance is too low; never goes negative. A price of zero or less is refused as a mistake.
 */
fun spend(progress: Progress, cost: Cost, now: Long): SpendResult {
    val wallet = progress.wallet
    require(cost.price > 0) { "A price must be positive: $cost" }
    if (wallet.balance < cost.price) return SpendResult.Short(cost.price, wallet.balance)
    val after = wallet.copy(
        balance = wallet.balance - cost.price,
        spent = wallet.spent + cost.price,
        ledger = (wallet.ledger + LedgerEntry("${Source.Spend.name}/${cost.what}", -cost.price, now)).takeLast(Economy.LEDGER_SIZE),
    )
    return SpendResult.Spent(progress.copy(wallet = after))
}

/**
 * The daily gift: the first time the app opens on [day], Fireflies once (doubled for a Supporter).
 * Opening again the same day gives nothing.
 */
fun openToday(progress: Progress, day: Long, now: Long, supporter: Boolean = false): Pair<Progress, Int> {
    if (progress.wallet.lastOpenDay == day) return progress to 0
    val gift = Earning.FirstOpen(supporter)
    val opened = progress.copy(wallet = progress.wallet.copy(lastOpenDay = day))
    return earn(opened, gift, now) to gift.amount
}

/**
 * What a finished round earns, worked out from what changed between [before] and [after]. Only
 * stars better than the level's best pay, each once; a win that earns no new star pays a little.
 * A daily porch already played today (practice) earns nothing.
 */
fun earningsFor(before: Progress, after: Progress, round: Round, report: Report): List<Earning> {
    if (report.practice) return emptyList()
    val won = round.phase == Phase.Won
    val number = round.level.number
    val played = !round.daily && number in 1..LEVELS.size
    return buildList {
        if (played && won) {
            val was = before.levelStars[number - 1]
            val now = after.levelStars[number - 1]
            for (k in (was + 1)..now) add(Earning.NewStar(k))
            if (now <= was) add(Earning.ReplayWin)
        }
        if (report.chapterDone != null) add(Earning.BossFirstClear)
        if (played) {
            val chapter = round.level.chapter
            fun cleared(p: Progress) = LEVELS.filter { it.chapter == chapter }.all { p.levelStars[it.number - 1] > 0 }
            if (cleared(after) && !cleared(before)) add(Earning.ChapterComplete(chapter))
        }
        if (round.daily) add(Earning.Daily(report.dailyStreak))
        report.newBadges.forEach { add(Earning.BadgeEarned(it)) }
        for (level in (report.levelBefore + 1)..report.levelAfter) add(Earning.LevelUp(level))
    }
}
