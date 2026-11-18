package app.porchlight.game

/**
 * How a round opens, as screen timing only: the rules in [Round] are untouched, and the round's
 * clock still starts when the peek ends ([Round.go]).
 *
 * - [Deal]: the cards drop onto the board face down, and the bulb is dark.
 * - [Warm]: the bulb flickers twice and comes on.
 * - [Look]: every card is face up and the countdown runs for exactly the peek's length.
 * - [Go]: the cards have folded shut and GO! shows while the round's own clock starts.
 */
enum class PeekStage { None, Deal, Warm, Look, Go }

/** How long the deal and the warm-up take before the peek, together about 0.9 s. */
const val DEAL_MS = 450L
const val WARM_MS = 450L

/** The gap between one card and the next as they deal, flip up, or fold shut. */
const val DEAL_STAGGER_MS = 25L
const val WAVE_STAGGER_MS = 30L

/** How long GO! stays before it fades. */
const val GO_MS = 450L

/** The last half second of the peek, when the cards' rims brighten as a warning. */
const val PEEK_WARNING_MS = 500L

/**
 * The number the countdown shows with [remainingMs] of the peek left: whole seconds, rounded up.
 * A 2 s peek shows 2 then 1; Iris's 3.4 s peek shows 4, 3, 2, 1. Zero once the peek is over.
 */
fun peekNumber(remainingMs: Long): Int = if (remainingMs <= 0) 0 else ((remainingMs + 999) / 1000).toInt()

/** Every number a peek of [peekMs] shows, in order. */
fun peekCountdown(peekMs: Long): List<Int> = (peekNumber(peekMs) downTo 1).toList()
