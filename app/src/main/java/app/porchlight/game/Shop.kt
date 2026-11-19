package app.porchlight.game

/**
 * What Fireflies buy, as pure functions over [Progress]. Each one checks it is on offer, takes the
 * price through [spend] (which fails cleanly on a short balance) and hands over what was bought.
 * None of them can buy stars or touch the daily porch.
 */

/** The price of [boosts] together. */
fun priceOf(boosts: Set<Boost>): Int = boosts.sumOf { it.price }

/** Pays for boosts about to be used on a round. Nothing is charged for an empty set. */
fun buyBoosts(progress: Progress, boosts: Set<Boost>, now: Long): SpendResult {
    if (boosts.isEmpty()) return SpendResult.Spent(progress)
    return spend(progress, Cost("boost." + boosts.sortedBy { it.ordinal }.joinToString("+") { it.name }, priceOf(boosts)), now)
}

/**
 * Opening a locked chapter early: offered only for the chapter the player has reached and is
 * waiting at, for [Economy.CHAPTER_PRICE_PER_STAR] a missing star. Null when not on offer.
 */
fun chapterPrice(progress: Progress, chapter: Int): Int? {
    if (progress.waitingChapter != chapter) return null
    val missing = progress.starsTo(chapter)
    return if (missing > 0) missing * Economy.CHAPTER_PRICE_PER_STAR else null
}

fun openChapterEarly(progress: Progress, chapter: Int, now: Long): SpendResult {
    val price = chapterPrice(progress, chapter) ?: return SpendResult.NotOffered
    return when (val paid = spend(progress, Cost("chapter.$chapter", price), now)) {
        is SpendResult.Spent -> SpendResult.Spent(
            paid.progress.copy(chapters = maxOf(paid.progress.chapters, chapter), current = paid.progress.reached),
        )
        else -> paid
    }
}

/**
 * Skipping a level: offered only on the furthest level reached, after [Economy.SKIP_AFTER_LOSSES]
 * losses in a row on it, and never on the last level (there is nothing after it to open).
 */
fun skipOffered(progress: Progress): Boolean {
    val n = progress.current
    return n == progress.reached && n < LEVELS.size && progress.lossLevel == n &&
        progress.losses >= Economy.SKIP_AFTER_LOSSES && progress.levelStars[n - 1] == 0 && progress.isOpen(n)
}

/** The level is marked skipped (no stars, still playable) and the next one opens. */
fun skipLevel(progress: Progress, now: Long): SpendResult {
    if (!skipOffered(progress)) return SpendResult.NotOffered
    val n = progress.current
    return when (val paid = spend(progress, Cost("skip.$n", Economy.SKIP_LEVEL_PRICE), now)) {
        is SpendResult.Spent -> {
            val p = paid.progress
            val next = n + 1
            SpendResult.Spent(
                p.copy(
                    skipped = p.skipped + n,
                    reached = maxOf(p.reached, next),
                    current = if (p.chapterOpen(levelAt(next).chapter)) next else n,
                    lossLevel = 0,
                    losses = 0,
                ),
            )
        }
        else -> paid
    }
}

/** A lead moth can be bought early until the level that brings it. */
fun earlyLeadOffered(progress: Progress, id: MothId) = id.isLead && !progress.unlocked(id)

fun buyLeadEarly(progress: Progress, id: MothId, now: Long): SpendResult {
    if (!earlyLeadOffered(progress, id)) return SpendResult.NotOffered
    return when (val paid = spend(progress, Cost("lead.${id.name}", Economy.EARLY_LEAD_PRICE), now)) {
        is SpendResult.Spent -> SpendResult.Spent(paid.progress.copy(earlyLeads = paid.progress.earlyLeads + id))
        else -> paid
    }
}

/** Owned: a default, or bought, or given by a pack. */
fun Progress.owns(look: Look) = look.gift == Gift.Default || look.id in owned

/**
 * Whether the shop shows [look] in [month] (1-12): anything owned; anything for Fireflies, a
 * seasonal one only in its season; never a pack's own look that isn't owned (that is shown with its pack).
 */
fun Progress.shows(look: Look, month: Int): Boolean = when {
    owns(look) -> true
    look.price == null -> false
    look.season != null -> month in look.season.months
    else -> true
}

/** Buys [look] and puts it on. Not on offer if already owned, out of season, or not for Fireflies. */
fun buyLook(progress: Progress, look: Look, month: Int, now: Long): SpendResult {
    val price = look.price
    if (price == null || progress.owns(look) || !progress.shows(look, month)) return SpendResult.NotOffered
    return when (val paid = spend(progress, Cost("look.${look.id}", price), now)) {
        is SpendResult.Spent -> SpendResult.Spent(wear(paid.progress.copy(owned = paid.progress.owned + look.id), look))
        else -> paid
    }
}

/** Puts on an owned look; anything else is ignored. */
fun wear(progress: Progress, look: Look): Progress =
    if (!progress.owns(look)) progress
    else if (look.gift == Gift.Default) progress.copy(equipped = progress.equipped - look.slotKey)
    else progress.copy(equipped = progress.equipped + (look.slotKey to look.id))

/** Takes off whatever is worn in [slotKey]: it goes back to its default. */
fun takeOff(progress: Progress, slotKey: String): Progress = progress.copy(equipped = progress.equipped - slotKey)

/** The look worn in [slotKey], if it is a real, owned look for that slot; null means the default. */
fun Progress.wearing(slotKey: String): Look? =
    equipped[slotKey]?.let(::lookById)?.takeIf { owns(it) && it.slotKey == slotKey && it.gift != Gift.Default }
