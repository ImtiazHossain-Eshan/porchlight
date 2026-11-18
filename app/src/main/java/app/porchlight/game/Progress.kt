package app.porchlight.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What a badge is cast in, and the shape pressed into it. */
enum class Metal { Bronze, Silver, Gold }

enum class Mold { Moth, Beetle, Trophy, Star, Flame, Moon, Calendar, Clock, Check, Stamp }

/** Seventeen badges: twelve for how you play, five for the chapters. */
enum class Badge(val title: String, val detail: String, val metal: Metal, val mold: Mold) {
    FirstLight("First light", "Finish a round.", Metal.Bronze, Mold.Flame),
    CleanSweep("Clean sweep", "Win without a single wrong pair.", Metal.Silver, Mold.Check),
    QuickWings("Quick wings", "Win a board of 8 pairs or more with 15 seconds left.", Metal.Silver, Mold.Clock),
    Overdrive("Overdrive", "Reach a ×4 streak.", Metal.Gold, Mold.Flame),
    Melody("Melody", "Find six pairs in a row on a board of 8 pairs or more.", Metal.Silver, Mold.Beetle),
    NightShift("Night shift", "Play 10 rounds.", Metal.Bronze, Mold.Moon),
    FullRoster("Full roster", "Win once with every lead moth.", Metal.Silver, Mold.Moth),
    Wanderers("Wanderers", "Win a level where the cards move.", Metal.Bronze, Mold.Beetle),
    LastLight("Last light", "Win a level with three twists at once.", Metal.Gold, Mold.Trophy),
    Regular("Regular", "Play the daily porch three days running.", Metal.Bronze, Mold.Calendar),
    GoldenWing("Golden wing", "Master a moth: three stars with it leading, on level 10 or later.", Metal.Gold, Mold.Moth),
    GildedPorch("Gilded porch", "Master all six lead moths.", Metal.Gold, Mold.Star),
    Chapter1("The porch", "Finish chapter 1, First Light: beat level 10.", Metal.Bronze, Mold.Stamp),
    Chapter2("The garden", "Finish chapter 2, Wanderers: beat level 20.", Metal.Bronze, Mold.Stamp),
    Chapter3("The woods", "Finish chapter 3, Dead Leaves: beat level 30.", Metal.Silver, Mold.Stamp),
    Chapter4("The rooftops", "Finish chapter 4, Shadows: beat level 40.", Metal.Silver, Mold.Stamp),
    Chapter5("The last porch", "Finish chapter 5, Last Light: beat level 50.", Metal.Gold, Mold.Stamp),
    ;

    companion object {
        /** The badge stamped for finishing [chapter]. */
        fun forChapter(chapter: Int): Badge? = when (chapter) {
            1 -> Chapter1
            2 -> Chapter2
            3 -> Chapter3
            4 -> Chapter4
            5 -> Chapter5
            else -> null
        }
    }
}

/** One day's daily porch: the date seed, and how it went. */
data class DailyEntry(val day: Long, val stars: Int, val score: Int)

/**
 * Everything the daily share card shows, kept after the round so it can be shared later from the
 * daily porch. Holds the order of matches and misses, never where the cards lay.
 */
data class DailyCard(
    val day: Long,
    val lead: MothId,
    val won: Boolean,
    val stars: Int,
    val score: Int,
    val pairs: Int,
    val pairCount: Int,
    val leftMs: Long,
    val misses: Int,
    val bestMultiplier: Int,
    val streak: Int,
    val log: List<LogEntry>,
) {
    val overdrive get() = bestMultiplier >= MAX_MULTIPLIER

    /** "8/8 pairs · 12.4 s left · 1 miss" */
    val line: String get() {
        if (!won) return "The bulb burned out · $pairs/$pairCount pairs"
        val left = if (won) " · ${String.format(java.util.Locale.US, "%.1f", leftMs / 1000f)} s left" else ""
        val missText = when (misses) {
            0 -> "no misses"
            1 -> "1 miss"
            else -> "$misses misses"
        }
        return "$pairs/$pairCount pairs$left · $missText"
    }

    fun encode(): String = listOf(
        day, lead.name, won, stars, score, pairs, pairCount, leftMs, misses, bestMultiplier, streak, encodeLog(log),
    ).joinToString("|")

    companion object {
        fun of(round: Round, lead: MothId, day: Long, stars: Int, streak: Int) = DailyCard(
            day = day, lead = lead, won = round.phase == Phase.Won, stars = stars, score = round.score,
            pairs = round.pairs, pairCount = round.pairCount, leftMs = round.left, misses = round.misses,
            bestMultiplier = round.bestMultiplier, streak = streak, log = flightLog(round),
        )

        /** Reads [encode]'s string back, or null if it isn't one. */
        fun decode(text: String?): DailyCard? = runCatching {
            val p = text!!.split("|")
            require(p.size == 12)
            DailyCard(
                day = p[0].toLong(), lead = MothId.valueOf(p[1]), won = p[2].toBooleanStrict(), stars = p[3].toInt(),
                score = p[4].toInt(), pairs = p[5].toInt(), pairCount = p[6].toInt(), leftMs = p[7].toLong(),
                misses = p[8].toInt(), bestMultiplier = p[9].toInt(), streak = p[10].toInt(), log = decodeLog(p[11]),
            )
        }.getOrNull()
    }
}

/** Total XP needed to reach [level]: 200, 500, 900, 1400, 2000, ... */
fun xpFor(level: Int) = 50 * (level - 1) * (level + 2)

fun levelFor(xp: Int): Int {
    var level = 1
    while (xp >= xpFor(level + 1)) level++
    return level
}

/** Wick leads from the start; each level up brings the next lead moth to the porch. */
val MothId.unlockLevel get() = if (isLead) ordinal + 1 else Int.MAX_VALUE

data class Progress(
    val xp: Int = 0,
    val rounds: Int = 0,
    val best: Int = 0,
    val badges: Set<Badge> = emptySet(),
    val winners: Set<MothId> = emptySet(),
    /** Lead moths won with three stars: their cards turn gold. */
    val mastered: Set<MothId> = emptySet(),
    /** Earned but not yet handed over: they arrive in later rounds, at most [MAX_UNLOCKS] a round. */
    val owedBadges: Set<Badge> = emptySet(),
    val owedMastery: Set<MothId> = emptySet(),
    /** The furthest level a win has opened, and the one picked on the porch. */
    val reached: Int = 1,
    val current: Int = 1,
    /** The best stars earned on each level, level 1 first. */
    val levelStars: List<Int> = List(LEVELS.size) { 0 },
    /** The highest chapter opened. Once open a chapter stays open. */
    val chapters: Int = 1,
    /** Losses in a row on [lossLevel]: three offer the "Easier bulb". */
    val lossLevel: Int = 0,
    val losses: Int = 0,
    /** The player's own daily porches, newest last; the last 14 are kept. */
    val dailyLog: List<DailyEntry> = emptyList(),
    val dailyDay: Long = 0,
    val dailyStreak: Int = 0,
    val dailyScore: Int = 0,
    val dailyStars: Int = 0,
    /** The last scored daily porch, for the share card. */
    val dailyCard: DailyCard? = null,
    val tutorialDone: Boolean = false,
    /** One-line tips already shown: twists and new moths are introduced once. */
    val tips: Set<String> = emptySet(),
    val muted: Boolean = false,
    val music: Boolean = true,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
) {
    val level get() = levelFor(xp)
    fun unlocked(id: MothId) = level >= id.unlockLevel
    fun playedDaily(today: Long) = dailyDay == today

    val totalStars get() = levelStars.sum()

    /** A chapter is open once it has been opened, or once the player holds enough stars for it. */
    fun chapterOpen(chapter: Int) = chapter <= chapters || chapter in 1..CHAPTERS.size && totalStars >= CHAPTERS[chapter - 1].gate

    /** A level is open once a win has reached it and its chapter is open. */
    fun isOpen(number: Int) = number in 1..reached && chapterOpen(levelAt(number).chapter)

    /** Stars still to find before [chapter] opens; zero once it is open. */
    fun starsTo(chapter: Int) = if (chapterOpen(chapter)) 0 else CHAPTERS[chapter - 1].gate - totalStars

    /** The chapter the player has reached but can't enter yet for want of stars, if any. */
    val waitingChapter: Int? get() {
        val next = levelAt(reached)
        return if (!chapterOpen(next.chapter)) next.chapter else null
    }

    /**
     * The open levels most worth replaying for stars: the fewest stars first, and of those the
     * latest, since its stars are the hardest won and the player has the knack of it.
     */
    val replays: List<Int> get() = (1..reached).filter { isOpen(it) && levelStars[it - 1] < 3 && it != reached }
        .sortedWith(compareBy<Int> { levelStars[it - 1] }.thenByDescending { it })

    /** Three losses in a row on the picked level: the gentle retry is on offer. */
    val easierOffered get() = losses >= 3 && lossLevel == current
}

/** What one round earned, shown on the results. */
data class Report(
    val stars: Int,
    val xp: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val newBadges: List<Badge>,
    val newMoths: List<MothId>,
    val newBest: Boolean,
    /** The level a win just opened. */
    val newLevel: Int? = null,
    /** A boss beaten for the first time: the chapter it finishes. */
    val chapterDone: Int? = null,
    /** A chapter whose star gate this round's stars opened. */
    val newChapter: Int? = null,
    val newMastery: MothId? = null,
    /** A daily porch that was already played today: it shows a score but earns nothing. */
    val practice: Boolean = false,
    val dailyStreak: Int = 0,
)

/** The most things one round may unlock, so rewards arrive a few at a time. */
const val MAX_UNLOCKS = 2

/**
 * One star for winning; two for winning with a quarter of the bulb left; three for that and no more
 * wrong pairs than the level allows. The "Easier bulb" retry earns one star at most. The daily porch
 * keeps its own rule: one for winning, one for a third of the bulb left, one for no wrong pairs.
 */
fun starsFor(round: Round): Int {
    if (round.phase != Phase.Won) return 0
    if (round.daily) return 1 + (if (round.left * 3 >= round.level.roundMs) 1 else 0) + (if (round.misses == 0) 1 else 0)
    if (round.easy || round.left * 4 < round.level.roundMs) return 1
    return if (round.misses <= round.level.maxMissesFor3Stars) 3 else 2
}

/** XP for a round: boss wins earn half again. */
fun xpFor(round: Round, stars: Int): Int {
    val won = round.phase == Phase.Won
    val xp = round.score / 10 + (if (won) 50 else 10) + stars * 20
    return if (won && round.level.boss && !round.daily) xp * 3 / 2 else xp
}

/** The share line for today's porch: "Porchlight daily 20261114 ★★☆ 2,340". */
fun dailyShare(day: Long, stars: Int, score: Int): String =
    "Porchlight daily $day ${"★".repeat(stars)}${"☆".repeat(3 - stars)} ${String.format(java.util.Locale.US, "%,d", score)}"

/**
 * Adds a finished round to [before]. Pure, so it can be tested without storage.
 * [today] and [yesterday] are daily seeds; they only matter for a daily round.
 */
fun settleRound(before: Progress, round: Round, lead: MothId, today: Long = 0, yesterday: Long = -1): Pair<Progress, Report> {
    val won = round.phase == Phase.Won
    val stars = starsFor(round)
    if (round.daily && before.playedDaily(today)) {
        val report = Report(stars, 0, before.level, before.level, emptyList(), emptyList(), false, practice = true, dailyStreak = before.dailyStreak)
        return before.copy(rounds = before.rounds + 1) to report
    }

    val xp = xpFor(round, stars)
    val winners = if (won) before.winners + lead else before.winners
    val rounds = before.rounds + 1
    val number = round.level.number
    val played = !round.daily && number in 1..LEVELS.size
    val levelStars = if (played) {
        before.levelStars.mapIndexed { i, best -> if (i == number - 1) maxOf(best, stars) else best }
    } else before.levelStars
    val totalStars = levelStars.sum()
    // Chapters open by stars held, and stay open.
    val chapters = maxOf(before.chapters, CHAPTERS.last { totalStars >= it.gate }.number)
    // A win on the furthest level reached opens the next one.
    val reaches = played && won && number == before.reached && number < LEVELS.size
    val reached = if (reaches) number + 1 else before.reached
    val opens = reaches && levelAt(reached).chapter <= chapters
    // A gate that opens while the player stands at it is news; one opened further ahead is not yet.
    val newChapter = if (chapters > before.chapters && levelAt(reached).chapter in (before.chapters + 1)..chapters) levelAt(reached).chapter else null
    val chapterDone = if (played && won && round.level.boss && before.levelStars[number - 1] == 0) round.level.chapter else null
    val (lossLevel, losses) = when {
        !played -> before.lossLevel to before.losses
        won -> 0 to 0
        before.lossLevel == number -> number to before.losses + 1
        else -> number to 1
    }
    val dailyStreak = when {
        !round.daily -> before.dailyStreak
        before.dailyDay == yesterday -> before.dailyStreak + 1
        else -> 1
    }

    // Rewards are paced: a round hands over at most MAX_UNLOCKS things. A new level and a new lead
    // always arrive at once, and so does a chapter's stamp; a gold moth and badges wait their turn
    // in later rounds if there is no room.
    val newMoths = LEADS.filter { !before.unlocked(it) && levelFor(before.xp + xp) >= it.unlockLevel }
    var room = (MAX_UNLOCKS - (if (opens) 1 else 0) - newMoths.size).coerceAtLeast(0)

    // Mastery is three stars with that lead on level 10 or later: the first levels are too easy to count.
    val earnsMastery = stars == 3 && lead.isLead && played && number >= 10 && lead !in before.mastered
    val masteryDue = (before.owedMastery + if (earnsMastery) setOf(lead) else emptySet()).sortedBy { it.ordinal }
    val newMastery = masteryDue.take(room.coerceAtMost(1))
    room -= newMastery.size
    val mastered = before.mastered + newMastery

    val big = round.pairCount >= 8
    val met = buildSet {
        add(Badge.FirstLight)
        if (won && round.misses == 0) add(Badge.CleanSweep)
        if (won && big && round.left >= 15_000) add(Badge.QuickWings)
        if (round.bestMultiplier >= MAX_MULTIPLIER) add(Badge.Overdrive)
        if (big && round.bestRun >= 6) add(Badge.Melody)
        if (rounds >= 10) add(Badge.NightShift)
        if (LEADS.all { it in winners }) add(Badge.FullRoster)
        if (won && played && Twist.Wanderer in round.level) add(Badge.Wanderers)
        if (won && played && round.level.twists.size >= 3) add(Badge.LastLight)
        if (dailyStreak >= 3) add(Badge.Regular)
        if (mastered.isNotEmpty()) add(Badge.GoldenWing)
        if (LEADS.all { it in mastered }) add(Badge.GildedPorch)
    }
    val stamp = chapterDone?.let { Badge.forChapter(it) }?.takeIf { it !in before.badges }
    val badgesDue = Badge.entries.filter { (it in met || it in before.owedBadges) && it !in before.badges && it != stamp }
    val newBadges = listOfNotNull(stamp) + badgesDue.take(room)

    val after = before.copy(
        xp = before.xp + xp,
        rounds = rounds,
        best = maxOf(before.best, round.score),
        badges = before.badges + newBadges,
        owedBadges = (badgesDue - newBadges.toSet()).toSet(),
        winners = winners,
        mastered = mastered,
        owedMastery = (masteryDue - newMastery.toSet()).toSet(),
        reached = reached,
        current = if (opens) reached else before.current,
        levelStars = levelStars,
        chapters = chapters,
        lossLevel = lossLevel,
        losses = losses,
        dailyLog = if (round.daily) (before.dailyLog + DailyEntry(today, stars, round.score)).takeLast(14) else before.dailyLog,
        dailyDay = if (round.daily) today else before.dailyDay,
        dailyStreak = dailyStreak,
        dailyScore = if (round.daily) round.score else before.dailyScore,
        dailyStars = if (round.daily) stars else before.dailyStars,
        dailyCard = if (round.daily) DailyCard.of(round, lead, today, stars, dailyStreak) else before.dailyCard,
    )
    val report = Report(
        stars = stars,
        xp = xp,
        levelBefore = before.level,
        levelAfter = after.level,
        newBadges = newBadges,
        newMoths = newMoths,
        newBest = round.score > before.best,
        newLevel = if (opens) reached else null,
        chapterDone = chapterDone,
        newChapter = newChapter,
        newMastery = newMastery.firstOrNull(),
        dailyStreak = dailyStreak,
    )
    return after to report
}

/** The save's layout. 1: six nights. 2: fifty levels in five chapters. */
const val SCHEMA = 2

/** Where each of the old six nights sits among the fifty levels. */
val NIGHT_TO_LEVEL = listOf(4, 8, 16, 20, 25, 31)

/** Saved best stars, one per level; anything unreadable counts as none. */
fun readStars(text: String?): List<Int> {
    val parts = text?.split(",").orEmpty()
    return List(LEVELS.size) { i -> parts.getOrNull(i)?.trim()?.toIntOrNull()?.coerceIn(0, 3) ?: 0 }
}

/**
 * Carries a six-night save across to the levels. Each old night's best stars go to its level in
 * [NIGHT_TO_LEVEL]; every level before it counts as cleared with one star; the level after it opens.
 * A night the old save had opened was won before, so it counts as won even if its stars are lost.
 * Chapters the player had reached stay open whatever their stars. Junk in the old values reads as
 * nothing, never as a crash.
 */
fun migrateNights(nights: Int, nightStars: String?): Progress {
    val opened = nights.coerceIn(1, NIGHT_TO_LEVEL.size)
    val old = nightStars?.split(",").orEmpty().map { it.trim().toIntOrNull()?.coerceIn(0, 3) ?: 0 }
    val stars = MutableList(LEVELS.size) { 0 }
    var reached = 1
    for ((i, level) in NIGHT_TO_LEVEL.withIndex()) {
        val best = maxOf(old.getOrNull(i) ?: 0, if (i + 1 < opened) 1 else 0)
        if (best == 0) continue
        stars[level - 1] = maxOf(stars[level - 1], best)
        for (j in 0 until level - 1) if (stars[j] == 0) stars[j] = 1
        reached = maxOf(reached, minOf(level + 1, LEVELS.size))
    }
    val total = stars.sum()
    val chapters = maxOf(levelAt(reached).chapter, CHAPTERS.last { total >= it.gate }.number)
    return Progress(reached = reached, current = reached, levelStars = stars, chapters = chapters)
}

class Store(private val prefs: SharedPreferences) {
    var progress by mutableStateOf(load())
        private set

    private inline fun <reified T : Enum<T>> names(key: String): Set<T> =
        prefs.getStringSet(key, emptySet()).orEmpty()
            .mapNotNull { name -> enumValues<T>().find { it.name == name } }.toSet()

    /**
     * Version 2 renamed two leads: the green moth Wick became Luna, and the red moth Ember became
     * Wick. Saved names from before are carried across once.
     */
    private fun migrateNames() {
        if (prefs.getInt("names", 1) >= 2) return
        val renamed = mapOf("Wick" to "Luna", "Ember" to "Wick")
        val edit = prefs.edit()
        for (key in listOf("winners", "mastered")) {
            val old = prefs.getStringSet(key, null) ?: continue
            edit.putStringSet(key, old.map { renamed[it] ?: it }.toSet())
        }
        edit.putInt("names", 2).apply()
    }

    /**
     * Schema 2 replaced the six nights with fifty levels. A save from before carries its nights
     * across once (see [migrateNights]); a fresh install just starts at level 1.
     */
    private fun migrateLevels() {
        if (prefs.getInt("schema", 1) >= SCHEMA) return
        val edit = prefs.edit()
        if (prefs.contains("nights") || prefs.contains("nightStars")) {
            val moved = migrateNights(
                nights = runCatching { prefs.getInt("nights", 1) }.getOrDefault(1),
                nightStars = runCatching { prefs.getString("nightStars", null) }.getOrNull(),
            )
            edit.putInt("reached", moved.reached)
                .putInt("current", moved.current)
                .putString("levelStars", moved.levelStars.joinToString(","))
                .putInt("chapters", moved.chapters)
                .remove("nights").remove("night").remove("nightStars")
        }
        edit.putInt("schema", SCHEMA).apply()
    }

    private fun load(): Progress {
        migrateNames()
        migrateLevels()
        return read()
    }

    private fun read() = Progress(
        xp = prefs.getInt("xp", 0),
        rounds = prefs.getInt("rounds", 0),
        best = prefs.getInt("best", 0),
        badges = names("badges"),
        winners = names("winners"),
        mastered = names("mastered"),
        owedBadges = names("owedBadges"),
        owedMastery = names("owedMastery"),
        reached = prefs.getInt("reached", 1).coerceIn(1, LEVELS.size),
        current = prefs.getInt("current", 1).coerceIn(1, LEVELS.size),
        levelStars = readStars(prefs.getString("levelStars", null)),
        chapters = prefs.getInt("chapters", 1).coerceIn(1, CHAPTERS.size),
        lossLevel = prefs.getInt("lossLevel", 0),
        losses = prefs.getInt("losses", 0),
        dailyLog = prefs.getString("dailyLog", null).orEmpty().split(";").mapNotNull { entry ->
            val parts = entry.split(":").mapNotNull { it.toLongOrNull() }
            if (parts.size == 3) DailyEntry(parts[0], parts[1].toInt(), parts[2].toInt()) else null
        },
        dailyDay = prefs.getLong("dailyDay", 0),
        dailyStreak = prefs.getInt("dailyStreak", 0),
        dailyScore = prefs.getInt("dailyScore", 0),
        dailyStars = prefs.getInt("dailyStars", 0),
        dailyCard = DailyCard.decode(prefs.getString("dailyCard", null)),
        // Anyone who played before the tutorial existed doesn't need it.
        tutorialDone = prefs.getBoolean("tutorialDone", prefs.getInt("rounds", 0) > 0),
        tips = prefs.getStringSet("tips", emptySet()).orEmpty().toSet(),
        muted = prefs.getBoolean("muted", false),
        music = prefs.getBoolean("music", true),
        reduceMotion = prefs.getBoolean("reduceMotion", false),
        haptics = prefs.getBoolean("haptics", true),
    )

    private fun save(next: Progress) {
        progress = next
        prefs.edit()
            .putInt("xp", next.xp)
            .putInt("rounds", next.rounds)
            .putInt("best", next.best)
            .putStringSet("badges", next.badges.map { it.name }.toSet())
            .putStringSet("winners", next.winners.map { it.name }.toSet())
            .putStringSet("mastered", next.mastered.map { it.name }.toSet())
            .putStringSet("owedBadges", next.owedBadges.map { it.name }.toSet())
            .putStringSet("owedMastery", next.owedMastery.map { it.name }.toSet())
            .putInt("reached", next.reached)
            .putInt("current", next.current)
            .putString("levelStars", next.levelStars.joinToString(","))
            .putInt("chapters", next.chapters)
            .putInt("lossLevel", next.lossLevel)
            .putInt("losses", next.losses)
            .putString("dailyLog", next.dailyLog.joinToString(";") { "${it.day}:${it.stars}:${it.score}" })
            .putLong("dailyDay", next.dailyDay)
            .putInt("dailyStreak", next.dailyStreak)
            .putInt("dailyScore", next.dailyScore)
            .putInt("dailyStars", next.dailyStars)
            .putString("dailyCard", next.dailyCard?.encode())
            .putBoolean("tutorialDone", next.tutorialDone)
            .putStringSet("tips", next.tips)
            .putBoolean("muted", next.muted)
            .putBoolean("music", next.music)
            .putBoolean("reduceMotion", next.reduceMotion)
            .putBoolean("haptics", next.haptics)
            .apply()
    }

    fun setMuted(muted: Boolean) = save(progress.copy(muted = muted))

    /** Changes a setting and saves it. */
    fun change(edit: (Progress) -> Progress) = save(edit(progress))

    fun finish(round: Round, lead: MothId, today: Long = 0, yesterday: Long = -1): Report {
        val (after, report) = settleRound(progress, round, lead, today, yesterday)
        save(after)
        return report
    }
}
