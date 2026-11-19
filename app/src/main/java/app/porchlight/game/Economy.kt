package app.porchlight.game

/**
 * Every number in the Fireflies economy, in one place, so it can be tuned without touching the
 * rules or the screens. Fireflies are the game's currency ("Gems" in code). Nothing here takes a
 * life, an energy or a wait away: playing is always free and unlimited.
 *
 * docs/ECONOMY.md explains the reasoning and shows what a free player earns over a month.
 */
object Economy {
    // ---------------------------------------------------------------------------------------------
    // Earning

    /** A level's first, second and third star, each paid once, the first time it is earned. */
    val NEW_STAR = listOf(3, 5, 8)

    /** A win that earns no new star: a little, so replaying is never pointless but never a farm. */
    const val REPLAY_WIN = 1

    /** Beating a chapter's boss for the first time. */
    const val BOSS_FIRST_CLEAR = 25

    /** Every level in a chapter cleared (at least one star on each of its ten). */
    const val CHAPTER_COMPLETE = 50

    /** Playing the scored daily porch: a base, plus a little more for each day of the streak, capped. */
    const val DAILY_BASE = 10
    const val DAILY_PER_STREAK_DAY = 5
    const val DAILY_CAP = 30

    /** Each badge, the moment it is handed over. */
    const val BADGE = 20

    /** Each level up. */
    const val LEVEL_UP = 15

    /** The first time the app opens on a day: the daily gift. A Supporter gets it doubled. */
    const val FIRST_OPEN = 5
    const val SUPPORTER_GIFT_MULTIPLIER = 2

    /** How many transactions the wallet's ledger keeps, newest last, for support and debugging. */
    const val LEDGER_SIZE = 200

    /** Fireflies for the daily porch at a streak of [streak] days (1 = today only). */
    fun daily(streak: Int): Int = (DAILY_BASE + DAILY_PER_STREAK_DAY * (streak - 1).coerceAtLeast(0)).coerceAtMost(DAILY_CAP)
}
