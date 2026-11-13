package app.porchlight.game

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Badge(val title: String, val detail: String) {
    FirstLight("First light", "Finish a round."),
    CleanSweep("Clean sweep", "Win without a single wrong pair."),
    QuickWings("Quick wings", "Win with 15 seconds or more left."),
    Overdrive("Overdrive", "Reach a ×4 streak."),
    NightShift("Night shift", "Play 10 rounds."),
    FullRoster("Full roster", "Win once with every moth leading."),
}

/** Total XP needed to reach [level]: 200, 500, 900, 1400, 2000, ... */
fun xpFor(level: Int) = 50 * (level - 1) * (level + 2)

fun levelFor(xp: Int): Int {
    var level = 1
    while (xp >= xpFor(level + 1)) level++
    return level
}

/** Wick leads from the start; each level up brings the next moth to the porch. */
val MothId.unlockLevel get() = ordinal + 1

data class Progress(
    val xp: Int = 0,
    val rounds: Int = 0,
    val best: Int = 0,
    val badges: Set<Badge> = emptySet(),
    val winners: Set<MothId> = emptySet(),
    val muted: Boolean = false,
) {
    val level get() = levelFor(xp)
    fun unlocked(id: MothId) = level >= id.unlockLevel
}

/** What one round earned, shown on the result card. */
data class Report(
    val stars: Int,
    val xp: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val newBadges: List<Badge>,
    val newMoths: List<MothId>,
    val newBest: Boolean,
)

fun starsFor(round: Round): Int =
    if (round.phase != Phase.Won) 0
    else 1 + (if (round.left >= 10_000) 1 else 0) + (if (round.misses == 0) 1 else 0)

class Store(private val prefs: SharedPreferences) {
    var progress by mutableStateOf(load())
        private set

    private fun load() = Progress(
        xp = prefs.getInt("xp", 0),
        rounds = prefs.getInt("rounds", 0),
        best = prefs.getInt("best", 0),
        badges = prefs.getStringSet("badges", emptySet()).orEmpty()
            .mapNotNull { name -> Badge.entries.find { it.name == name } }.toSet(),
        winners = prefs.getStringSet("winners", emptySet()).orEmpty()
            .mapNotNull { name -> MothId.entries.find { it.name == name } }.toSet(),
        muted = prefs.getBoolean("muted", false),
    )

    private fun save(next: Progress) {
        progress = next
        prefs.edit()
            .putInt("xp", next.xp)
            .putInt("rounds", next.rounds)
            .putInt("best", next.best)
            .putStringSet("badges", next.badges.map { it.name }.toSet())
            .putStringSet("winners", next.winners.map { it.name }.toSet())
            .putBoolean("muted", next.muted)
            .apply()
    }

    fun setMuted(muted: Boolean) = save(progress.copy(muted = muted))

    fun finish(round: Round, lead: MothId): Report {
        val before = progress
        val won = round.phase == Phase.Won
        val stars = starsFor(round)
        val xp = round.score / 10 + (if (won) 50 else 10) + stars * 20
        val winners = if (won) before.winners + lead else before.winners
        val rounds = before.rounds + 1

        val earned = buildSet {
            add(Badge.FirstLight)
            if (won && round.misses == 0) add(Badge.CleanSweep)
            if (won && round.left >= 15_000) add(Badge.QuickWings)
            if (round.bestMultiplier >= MAX_MULTIPLIER) add(Badge.Overdrive)
            if (rounds >= 10) add(Badge.NightShift)
            if (winners.size == MothId.entries.size) add(Badge.FullRoster)
        }
        val after = before.copy(
            xp = before.xp + xp,
            rounds = rounds,
            best = maxOf(before.best, round.score),
            badges = before.badges + earned,
            winners = winners,
        )
        save(after)

        return Report(
            stars = stars,
            xp = xp,
            levelBefore = before.level,
            levelAfter = after.level,
            newBadges = Badge.entries.filter { it in earned && it !in before.badges },
            newMoths = MothId.entries.filter { !before.unlocked(it) && after.unlocked(it) },
            newBest = round.score > before.best,
        )
    }
}
