package app.porchlight.game

/**
 * Every card face. The first six can lead a round, and each bends its rules; the next six only join
 * the board on later levels. [Leaf] is the dead leaf, the dud card; [Domino] and [Lilac] are the
 * near-twins of Dot and Iris that a Lookalikes level slips in.
 */
enum class MothId(
    val label: String,
    val role: String = "",
    val perkName: String = "",
    val perk: String = "",
    val isLead: Boolean = true,
    /** In [BOARD_MOTHS], the moths every board is dealt from, in order. */
    val board: Boolean = true,
) {
    Luna("Luna", "The night owl", "Long-life bulb", "The round starts with 5 extra seconds."),
    Wick("Wick", "The sprinter", "Hot streak", "Streaks start at ×2, but the bulb is 3 seconds shorter."),
    Atlas("Atlas", "The mapmaker", "Second look", "Once a round, flash every card open for a moment."),
    Dot("Dot", "The counter", "Quick hands", "Wrong pairs flip back instantly, and your first miss doesn't break the streak."),
    Iris("Iris", "The watcher", "Long look", "The opening peek lasts 3.4 seconds instead of 2."),
    Sorbet("Sorbet", "The sweet tooth", "Top-up", "Every pair you find adds a second to the bulb."),
    Sulphur("Sulphur", isLead = false),
    Rose("Rose", isLead = false),
    Midnight("Midnight", isLead = false),
    Moss("Moss", isLead = false),
    Ash("Ash", isLead = false),
    Tide("Tide", isLead = false),
    Leaf("Dead leaf", isLead = false, board = false),
    Domino("Domino", isLead = false, board = false),
    Lilac("Lilac", isLead = false, board = false),
}

/** The six moths that can lead a round. */
val LEADS: List<MothId> = MothId.entries.filter { it.isLead }
