package app.porchlight.game

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Tools for tuning the level table, not checks: they run only with PORCH_TUNE set, and write
 * what they find to build/levels-*.txt.
 *
 *   PORCH_TUNE=report ./gradlew testDebugUnitTest --tests '*LevelTuner*'
 *   PORCH_TUNE=tune   ./gradlew testDebugUnitTest --tests '*LevelTuner*'
 *   PORCH_TUNE=docs   ./gradlew testDebugUnitTest --tests '*LevelTuner*'   (rewrites docs/LEVELS.md)
 */
class LevelTuner {
    private val mode = System.getenv("PORCH_TUNE").orEmpty()

    private fun out(name: String, text: String) {
        File("build/levels-$name.txt").writeText(text)
        println(text)
    }

    @Test fun report() {
        assumeTrue(mode == "report")
        val rows = LEVELS.parallelStream().map { level ->
            val stats = Skill.entries.map { Simulator.run(level, it) }
            level to stats
        }.toList()
        val text = buildString {
            appendLine("lvl kind     cards  s   peek  twists                  target  casual  avg    strong  avgStars  avgLeft")
            for ((level, stats) in rows) {
                val (c, a, s) = stats
                appendLine(
                    String.format(
                        Locale.US, "%3d %-8s %5d %3d %5.1f  %-22s %6.1f  %6.1f %6.1f %6.1f  %6.2f  %6.1f",
                        level.number, level.kind, level.cards, level.roundMs / 1000, level.peekMs / 1000.0,
                        level.twists.joinToString(",") { it.name }, targetWinRate(level), c.winRate, a.winRate, s.winRate,
                        a.stars, a.leftMs / 1000,
                    ),
                )
            }
        }
        out("report", text)
    }

    /** Peek overrides for a tuning run: PORCH_PEEK="1=2.5,50=3.4". */
    private val peeks: Map<Int, Double> = System.getenv("PORCH_PEEK").orEmpty().split(",").mapNotNull {
        val (n, v) = it.split("=").takeIf { p -> p.size == 2 } ?: return@mapNotNull null
        n.trim().toInt() to v.trim().toDouble()
    }.toMap()

    private fun rate(level: Level, seconds: Int, skill: Skill = Skill.Average, games: Int = 2_000) =
        Simulator.run(level.copy(roundMs = seconds * 1_000L), skill, games).winRate

    /** The fewest whole seconds at which [skill] wins at least [target]% of [level]. */
    private fun secondsFor(level: Level, target: Double, skill: Skill = Skill.Average): Int {
        var lo = 3
        var hi = 150
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (rate(level, mid, skill) >= target) hi = mid else lo = mid + 1
        }
        return lo
    }

    /**
     * For each level, the whole seconds that bring the average player closest to its target.
     * Levels 1 to 5 then get any extra seconds a casual player needs to win 90%, as long as the
     * average player stays within five points of target.
     */
    @Test fun tune() {
        assumeTrue(mode == "tune")
        val rows = LEVELS.parallelStream().map { base ->
            val level = peeks[base.number]?.let { base.copy(peekMs = (it * 1000).toLong()) } ?: base
            val target = targetWinRate(level)
            fun closest(l: Level): Int {
                val up = secondsFor(l, target)
                return if (rate(l, up) - target <= target - rate(l, up - 1)) up else up - 1
            }
            var l = level
            var best = closest(l)
            when {
                // Levels 1 to 5: a casual player must win 90%. Find the longest peek (from the
                // level's own, down) where the seconds that give a casual player 90% still keep
                // the average player within reach of the target.
                level.number <= 5 -> {
                    var peek = level.peekMs
                    while (peek >= 500) {
                        val tryLevel = level.copy(peekMs = peek)
                        val s = secondsFor(tryLevel, 90.5, Skill.Casual)
                        if (rate(tryLevel, s) <= target + 4.5) {
                            l = tryLevel
                            best = s
                            break
                        }
                        peek -= 100
                    }
                }
                // Level 50: a strong player must win 50 to 70% while the average player is at target.
                level.number == 50 -> {
                    var peek = level.peekMs
                    while (peek <= 8_000) {
                        val tryLevel = level.copy(peekMs = peek)
                        val s = closest(tryLevel)
                        if (rate(tryLevel, s, Skill.Strong) >= 53.0) {
                            l = tryLevel
                            best = s
                            break
                        }
                        peek += 100
                    }
                }
            }
            l = l.copy(roundMs = best * 1_000L)
            Triple(l, best, Skill.entries.map { Simulator.run(l, it).winRate })
        }.toList()
        out("tune", rows.joinToString("\n") { (level, s, r) ->
            val kind = when (level.kind) { Kind.Normal -> "N"; Kind.Breather -> "BREATHER"; Kind.Boss -> "BOSS" }
            val twists = level.twists.joinToString("") { ", " + it.name.uppercase() }
            String.format(
                Locale.US, "    level(%d, %d, %d, %d, %.1f, %s%s), // %.0f / %.0f / %.0f  (target %.0f)",
                level.number, level.pairs, level.leaves, s, level.peekMs / 1000.0, kind, twists, r[0], r[1], r[2], targetWinRate(level),
            )
        })
    }

    /** The level table and its simulated win rates, as docs/LEVELS.md. */
    @Test fun docs() {
        assumeTrue(mode == "docs")
        val rows = LEVELS.parallelStream().map { level -> level to Skill.entries.map { Simulator.run(level, it) } }.toList()
        val text = buildString {
            appendLine("# Porchlight levels")
            appendLine()
            appendLine("Fifty levels in five chapters. The table is `LEVELS` in `Game.kt`; this file is written from it by")
            appendLine("`PORCH_TUNE=docs ./gradlew testDebugUnitTest --tests '*LevelTuner*'`, and `LevelCurveTest` checks the curve")
            appendLine("on every build.")
            appendLine()
            appendLine("| Chapter | Name | Scene | Stars to open |")
            appendLine("|---|---|---|---|")
            for (c in CHAPTERS) appendLine("| ${c.number} | ${c.name} | ${c.scene} | ${c.gate} |")
            appendLine()
            appendLine("## How the win rates are measured")
            appendLine()
            appendLine("A simulated player (`Simulator.kt`, test code) plays the real round engine, ${"%,d".format(2_000)} seeded games per level per skill:")
            appendLine()
            appendLine("- From the peek it takes in each card with chance p = clamp(peek per card / 260 ms, 0.15, 0.95), times its skill: casual 0.6, average 1.0, strong 1.3. The Shadow moth's cards can't be taken in.")
            appendLine("- It sees every card it turns, except while a Blackout has the bulb out, and forgets 4% of what it knows per second.")
            appendLine("- It always plays a pair it knows; otherwise it turns an unknown card, then its partner if known, or another unknown card. It avoids dead leaves it knows.")
            appendLine("- A lookalike is taken for its twin 15% of the times it is seen. When the Wanderer swaps two cards, it loses track of both.")
            appendLine("- A turn takes ${Simulator.FLIP_MS / 1000.0} s on average (anywhere from half to one and a half times that), and a wrong pair shows for the game's 0.8 s before it folds back.")
            appendLine("- The lead has no perk, so the numbers are the level's own. Luna, the first lead, adds 5 s; Iris adds 1.4 s of peek.")
            appendLine()
            appendLine("The turn time is the spec's 0.7 s. At that pace the simulated player clears a board quickly, so the tuned rounds are short: 7 to 36 seconds.")
            appendLine()
            appendLine("Targets for the average player: chapter 1 95 to 85%, chapter 2 85 to 75%, chapter 3 78 to 65%, chapter 4 70 to 55%, chapter 5 60 to 40%; breathers 10 points easier, bosses 10 harder. Levels 1 to 5 also need a casual player to win 90%, and level 50 needs a strong player to win 50 to 70%. That is why levels 1 to 5 have short peeks and level 50 a long one.")
            appendLine()
            appendLine("## The table")
            appendLine()
            appendLine("| Level | Cards | Seconds | Peek | Twists | Kind | 3★ misses | Target | Casual | Average | Strong | Avg stars | Avg time left |")
            appendLine("|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|")
            for ((level, stats) in rows) {
                val (c, a, st) = stats
                val cards = if (level.leaves > 0) "${level.cards} (${level.pairs}p+${level.leaves}L)" else "${level.cards} (${level.pairs}p)"
                appendLine(
                    String.format(
                        Locale.US, "| %d | %s | %d | %.1f | %s | %s | %d | %.0f%% | %.1f%% | %.1f%% | %.1f%% | %.2f | %.1f s |",
                        level.number, cards, level.roundMs / 1000, level.peekMs / 1000.0,
                        level.twists.joinToString(", ") { it.title }.ifEmpty { "-" }, level.kind.name, level.maxMissesFor3Stars,
                        targetWinRate(level), c.winRate, a.winRate, st.winRate, a.stars, a.leftMs / 1000,
                    ),
                )
            }
        }
        File("../docs/LEVELS.md").writeText(text)
        println(text)
    }
}
