package app.porchlight.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
        lead = prefs.getString("lead", null)?.let { name -> MothId.entries.find { it.name == name && it.isLead } } ?: MothId.Luna,
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
            .putString("lead", next.lead.name)
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
