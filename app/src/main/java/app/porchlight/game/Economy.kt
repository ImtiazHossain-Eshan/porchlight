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

    // ---------------------------------------------------------------------------------------------
    // Boosts: bought for one round, at most one of each. Never on the daily porch.

    const val BOOST_TIME_PRICE = 15
    const val BOOST_TIME_MS = 10_000L

    const val BOOST_PEEK_PRICE = 10
    const val BOOST_PEEK_MS = 1_500L

    const val BOOST_LOOK_PRICE = 20
    const val BOOST_LOOK_MS = 900L

    const val BOOST_PAIR_PRICE = 25

    const val BOOST_RELIGHT_PRICE = 30
    const val BOOST_RELIGHT_MS = 15_000L

    /** A round that used any boost earns at most this many stars: the last needs a clean, boost-free clear. */
    const val BOOSTED_MAX_STARS = 2

    // ---------------------------------------------------------------------------------------------
    // Progress skips and early moths

    /** Opening a locked chapter early: this much for each star still missing from its gate. */
    const val CHAPTER_PRICE_PER_STAR = 10

    /** Skipping a level, offered only after this many losses in a row on it. */
    const val SKIP_LEVEL_PRICE = 50
    const val SKIP_AFTER_LOSSES = 3

    /** A lead moth before the level that brings it. */
    const val EARLY_LEAD_PRICE = 150

    // ---------------------------------------------------------------------------------------------
    // Rewarded ads: only ever when the player taps to watch one. No interstitials, no banners.

    /** "Watch for 10 Fireflies" in the shop. */
    const val AD_SHOP_REWARD = 10
    const val AD_SHOP_PER_DAY = 5

    /** "Double this level's Fireflies" on the results of a win. */
    const val AD_DOUBLE_PER_DAY = 3

    /** "Relight for free" when the bulb burns out: once a round (like any boost), and this many a day. */
    const val AD_RELIGHT_PER_DAY = 3

    /** Under this age, ads are child-directed: rated G, never personalised. */
    const val CHILD_AGE = 13

    /** Under this age (the highest digital age of consent in the EEA), ads are never personalised. */
    const val CONSENT_AGE = 16

    // ---------------------------------------------------------------------------------------------
    // Looks: cosmetics, never gameplay. Every one listed in [LOOK_CATALOG] needs a price here, or is a gift
    // (the default looks, and the starter pack's and Supporter's own, which can't be bought with Fireflies).

    val LOOK_PRICES: Map<String, Int> = mapOf(
        // Card backs, 200 to 400.
        "back.walnut" to 200,
        "back.cherry" to 250,
        "back.night" to 300,
        "back.moss" to 300,
        "back.gilded" to 400,
        // Jar and bulb styles.
        "jar.copper" to 300,
        "jar.pewter" to 300,
        "jar.gilt" to 300,
        // Moth colours, one for each lead.
        "moth.luna.harvest" to 150,
        "moth.wick.blue" to 150,
        "moth.atlas.ghost" to 150,
        "moth.dot.ink" to 150,
        "moth.iris.rose" to 150,
        "moth.sorbet.lime" to 150,
        // Porch themes: wall, railing and the colour of the light.
        "porch.birch" to 500,
        "porch.barn" to 500,
        "porch.harbour" to 500,
        // Seasonal sets: shown in their season, kept for ever once bought.
        "back.frost" to 300,
        "porch.frost" to 500,
        "back.harvest" to 300,
        "porch.harvest" to 500,
    )
}
