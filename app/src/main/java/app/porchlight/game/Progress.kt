package app.porchlight.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What a badge is cast in, and the shape pressed into it. */
enum class Metal { Bronze, Silver, Gold }

enum class Mold { Moth, Beetle, Trophy, Star, Flame, Moon, Calendar, Clock, Check }

/** Twelve badges. Each is its own pairing of metal and mold. */
enum class Badge(val title: String, val detail: String, val metal: Metal, val mold: Mold) {
    FirstLight("First light", "Finish a round.", Metal.Bronze, Mold.Flame),
    CleanSweep("Clean sweep", "Win without a single wrong pair.", Metal.Silver, Mold.Check),
    QuickWings("Quick wings", "Win a board of 8 pairs or more with 15 seconds left.", Metal.Silver, Mold.Clock),
    Overdrive("Overdrive", "Reach a ×4 streak.", Metal.Gold, Mold.Flame),
    Melody("Melody", "Find six pairs in a row on a board of 8 pairs or more.", Metal.Silver, Mold.Beetle),
    NightShift("Night shift", "Play 10 rounds.", Metal.Bronze, Mold.Moon),
    FullRoster("Full roster", "Win once with every lead moth.", Metal.Silver, Mold.Moth),
    Wanderers("Wanderers", "Beat night 4, where the cards move.", Metal.Bronze, Mold.Beetle),
    LastLight("Last light", "Beat night 6.", Metal.Gold, Mold.Trophy),
    Regular("Regular", "Play the daily porch three days running.", Metal.Bronze, Mold.Calendar),
    GoldenWing("Golden wing", "Master a moth: three stars with it leading, on night 3 or later.", Metal.Gold, Mold.Moth),
    GildedPorch("Gilded porch", "Master all six lead moths.", Metal.Gold, Mold.Star),
}

/** One day's daily porch: the date seed, and how it went. */
data class DailyEntry(val day: Long, val stars: Int, val score: Int)

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
    /** The highest night unlocked, and the one picked on the porch. */
    val nights: Int = 1,
    val night: Int = 1,
    /** The best stars earned on each night, night 1 first. */
    val nightStars: List<Int> = List(NIGHTS.size) { 0 },
    /** The player's own daily porches, newest last; the last 14 are kept. */
    val dailyLog: List<DailyEntry> = emptyList(),
    val dailyDay: Long = 0,
    val dailyStreak: Int = 0,
    val dailyScore: Int = 0,
    val dailyStars: Int = 0,
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
    val newNight: Int? = null,
    val newMastery: MothId? = null,
    /** A daily porch that was already played today: it shows a score but earns nothing. */
    val practice: Boolean = false,
    val dailyStreak: Int = 0,
)

/** The most things one round may unlock, so rewards arrive a few at a time. */
const val MAX_UNLOCKS = 2

/** One star for winning, one for a third of the bulb left, one for no wrong pairs. */
fun starsFor(round: Round): Int =
    if (round.phase != Phase.Won) 0
    else 1 + (if (round.left * 3 >= round.night.roundMs) 1 else 0) + (if (round.misses == 0) 1 else 0)

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

    val xp = round.score / 10 + (if (won) 50 else 10) + stars * 20
    val winners = if (won) before.winners + lead else before.winners
    val rounds = before.rounds + 1
    val night = round.night.number
    val unlocks = !round.daily && stars >= 2 && night == before.nights && night < NIGHTS.size
    val nights = if (unlocks) night + 1 else before.nights
    val dailyStreak = when {
        !round.daily -> before.dailyStreak
        before.dailyDay == yesterday -> before.dailyStreak + 1
        else -> 1
    }

    // Rewards are paced: a round hands over at most MAX_UNLOCKS things. A new night and a new lead
    // always arrive at once; a gold moth and badges wait their turn in later rounds if there is no room.
    val newMoths = LEADS.filter { !before.unlocked(it) && levelFor(before.xp + xp) >= it.unlockLevel }
    var room = (MAX_UNLOCKS - (if (unlocks) 1 else 0) - newMoths.size).coerceAtLeast(0)

    // Mastery is three stars with that lead on night 3 or later: night 1 is too easy to count.
    val earnsMastery = stars == 3 && lead.isLead && !round.daily && night >= 3 && lead !in before.mastered
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
        if (won && !round.daily && night >= 4) add(Badge.Wanderers)
        if (won && !round.daily && night >= 6) add(Badge.LastLight)
        if (dailyStreak >= 3) add(Badge.Regular)
        if (mastered.isNotEmpty()) add(Badge.GoldenWing)
        if (LEADS.all { it in mastered }) add(Badge.GildedPorch)
    }
    val badgesDue = Badge.entries.filter { (it in met || it in before.owedBadges) && it !in before.badges }
    val newBadges = badgesDue.take(room)

    val after = before.copy(
        xp = before.xp + xp,
        rounds = rounds,
        best = maxOf(before.best, round.score),
        badges = before.badges + newBadges,
        owedBadges = (badgesDue - newBadges.toSet()).toSet(),
        winners = winners,
        mastered = mastered,
        owedMastery = (masteryDue - newMastery.toSet()).toSet(),
        nights = nights,
        night = if (unlocks) nights else before.night,
        nightStars = if (!round.daily && night in 1..NIGHTS.size) {
            before.nightStars.mapIndexed { i, best -> if (i == night - 1) maxOf(best, stars) else best }
        } else before.nightStars,
        dailyLog = if (round.daily) (before.dailyLog + DailyEntry(today, stars, round.score)).takeLast(14) else before.dailyLog,
        dailyDay = if (round.daily) today else before.dailyDay,
        dailyStreak = dailyStreak,
        dailyScore = if (round.daily) round.score else before.dailyScore,
        dailyStars = if (round.daily) stars else before.dailyStars,
    )
    val report = Report(
        stars = stars,
        xp = xp,
        levelBefore = before.level,
        levelAfter = after.level,
        newBadges = newBadges,
        newMoths = newMoths,
        newBest = round.score > before.best,
        newNight = if (unlocks) nights else null,
        newMastery = newMastery.firstOrNull(),
        dailyStreak = dailyStreak,
    )
    return after to report
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

    private fun load(): Progress {
        migrateNames()
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
        nights = prefs.getInt("nights", 1),
        night = prefs.getInt("night", 1),
        nightStars = prefs.getString("nightStars", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == NIGHTS.size } ?: List(NIGHTS.size) { 0 },
        dailyLog = prefs.getString("dailyLog", null).orEmpty().split(";").mapNotNull { entry ->
            val parts = entry.split(":").mapNotNull { it.toLongOrNull() }
            if (parts.size == 3) DailyEntry(parts[0], parts[1].toInt(), parts[2].toInt()) else null
        },
        dailyDay = prefs.getLong("dailyDay", 0),
        dailyStreak = prefs.getInt("dailyStreak", 0),
        dailyScore = prefs.getInt("dailyScore", 0),
        dailyStars = prefs.getInt("dailyStars", 0),
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
            .putInt("nights", next.nights)
            .putInt("night", next.night)
            .putString("nightStars", next.nightStars.joinToString(","))
            .putString("dailyLog", next.dailyLog.joinToString(";") { "${it.day}:${it.stars}:${it.score}" })
            .putLong("dailyDay", next.dailyDay)
            .putInt("dailyStreak", next.dailyStreak)
            .putInt("dailyScore", next.dailyScore)
            .putInt("dailyStars", next.dailyStars)
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
