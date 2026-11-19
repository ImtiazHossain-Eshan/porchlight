package app.porchlight.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Looks: cosmetics bought with Fireflies. They change how the porch looks and never how it plays.
 * Card backs only change the face-down side (every back stays dark, so it can't be taken for a
 * face-up parchment card); moth colours only change a lead moth on the porch and its own pages,
 * never on the board, so a pair always looks like a pair.
 */
enum class Slot(val title: String, val key: String) {
    Back("Card backs", "back"),
    Jar("Jar and bulb", "jar"),
    Moth("Moth colours", "moth"),
    Porch("Porch themes", "porch"),
}

/** A season a seasonal set is shown in. Once bought, a look is kept for ever, in or out of season. */
enum class Season(val title: String, val months: Set<Int>) {
    Frost("Frost", setOf(12, 1, 2)),
    Harvest("Harvest", setOf(9, 10, 11)),
}

/** How a look is had other than for Fireflies. */
enum class Gift { Default, Starter, Supporter }

/** The metal a frame, lid or socket is made of. */
enum class Trim { Brass, Copper, Pewter, Gilt }

/** What is set into the middle of a card back. */
enum class Inlay { None, Moth, Star, Moon, Leaf, Snow, Firefly }

data class BackStyle(val light: Color, val base: Color, val dark: Color, val trim: Trim, val inlay: Inlay)
data class JarStyle(val trim: Trim, val glass: Color)
data class MothColours(val fore: Color, val hind: Color, val body: Color, val antenna: Color)

/** A porch theme: the wall's planks, the railing's wood, and the colour of the light. */
data class PorchStyle(
    val wallTop: Color,
    val wallBottom: Color,
    val plank: Color,
    val rail: Triple<Color, Color, Color>,
    val glow: Color,
    val glowDeep: Color,
)

data class Look(
    val id: String,
    val name: String,
    val slot: Slot,
    /** For a moth colour: the lead it is for. */
    val moth: MothId? = null,
    val season: Season? = null,
    val gift: Gift? = null,
    val back: BackStyle? = null,
    val jar: JarStyle? = null,
    val colours: MothColours? = null,
    val porch: PorchStyle? = null,
) {
    /** The Fireflies it costs, from [Economy.LOOK_PRICES]; null if it can't be bought with them. */
    val price: Int? get() = if (gift != null) null else Economy.LOOK_PRICES[id]

    /** The slot it is worn in: one per kind, and one per lead moth for moth colours. */
    val slotKey: String get() = if (slot == Slot.Moth) slotFor(moth!!) else slot.key

    companion object {
        fun slotFor(moth: MothId) = "moth.${moth.name}"
    }
}

private fun c(hex: Long) = Color(hex)

private val NIGHT_PORCH = PorchStyle(
    wallTop = c(0xFF10171F), wallBottom = c(0xFF1A242E), plank = c(0xFF0A0F15),
    rail = Triple(c(0xFF4A301E), c(0xFF332014), c(0xFF1C110A)),
    glow = c(0xFFFFD27A), glowDeep = c(0xFFFF9F43),
)

/** Every look there is. The first of each slot is its default, owned by everyone. */
val LOOK_CATALOG: List<Look> = listOf(
    // Card backs.
    Look("back.ebony", "Ebony and brass", Slot.Back, gift = Gift.Default,
        back = BackStyle(c(0xFF33231A), c(0xFF241710), c(0xFF120B07), Trim.Brass, Inlay.None)),
    Look("back.walnut", "Walnut moth", Slot.Back,
        back = BackStyle(c(0xFF5A3A22), c(0xFF3F2716), c(0xFF22140A), Trim.Brass, Inlay.Moth)),
    Look("back.cherry", "Cherry and copper", Slot.Back,
        back = BackStyle(c(0xFF6A2C1E), c(0xFF4C1D13), c(0xFF280D08), Trim.Copper, Inlay.Star)),
    Look("back.night", "Night and pewter", Slot.Back,
        back = BackStyle(c(0xFF2C3A48), c(0xFF1B2530), c(0xFF0C1218), Trim.Pewter, Inlay.Moon)),
    Look("back.moss", "Mossy green", Slot.Back,
        back = BackStyle(c(0xFF2F4A2C), c(0xFF1F331D), c(0xFF0E1A0D), Trim.Brass, Inlay.Leaf)),
    Look("back.gilded", "Gilded navy", Slot.Back,
        back = BackStyle(c(0xFF22334A), c(0xFF16233A), c(0xFF0A1220), Trim.Gilt, Inlay.Star)),
    Look("back.firefly", "Firefly lacquer", Slot.Back, gift = Gift.Starter,
        back = BackStyle(c(0xFF1E2A1A), c(0xFF121C10), c(0xFF070D06), Trim.Gilt, Inlay.Firefly)),
    Look("back.frost", "Frost", Slot.Back, season = Season.Frost,
        back = BackStyle(c(0xFF2E4458), c(0xFF1D2F40), c(0xFF0D1824), Trim.Pewter, Inlay.Snow)),
    Look("back.harvest", "Harvest", Slot.Back, season = Season.Harvest,
        back = BackStyle(c(0xFF6A3A14), c(0xFF4C280C), c(0xFF281405), Trim.Copper, Inlay.Leaf)),

    // Jar and bulb.
    Look("jar.brass", "Brass and clear glass", Slot.Jar, gift = Gift.Default, jar = JarStyle(Trim.Brass, c(0xFFCFE6F2))),
    Look("jar.copper", "Copper and amber glass", Slot.Jar, jar = JarStyle(Trim.Copper, c(0xFFFFC98A))),
    Look("jar.pewter", "Pewter and sea glass", Slot.Jar, jar = JarStyle(Trim.Pewter, c(0xFF9FE3D0))),
    Look("jar.gilt", "Gilt and rose glass", Slot.Jar, jar = JarStyle(Trim.Gilt, c(0xFFF6B8C8))),

    // Moth colours, one for each lead.
    Look("moth.luna.harvest", "Harvest Luna", Slot.Moth, moth = MothId.Luna,
        colours = MothColours(c(0xFFF2C46A), c(0xFFF2C46A), c(0xFFFFF1D6), c(0xFFB0661E))),
    Look("moth.wick.blue", "Blue flame Wick", Slot.Moth, moth = MothId.Wick,
        colours = MothColours(c(0xFF3B8CF0), c(0xFF14213A), c(0xFF3B8CF0), c(0xFF0E1830))),
    Look("moth.atlas.ghost", "Ghost Atlas", Slot.Moth, moth = MothId.Atlas,
        colours = MothColours(c(0xFFE8E0D0), c(0xFFE8E0D0), c(0xFFD8CFBE), c(0xFF8A7E6C))),
    Look("moth.dot.ink", "Inkblot Dot", Slot.Moth, moth = MothId.Dot,
        colours = MothColours(c(0xFF2E2A30), c(0xFF2E2A30), c(0xFF4A4450), c(0xFFF4EEDF))),
    Look("moth.iris.rose", "Rose Iris", Slot.Moth, moth = MothId.Iris,
        colours = MothColours(c(0xFFF2A6C8), c(0xFFF2A6C8), c(0xFFD884AE), c(0xFFB0507E))),
    Look("moth.sorbet.lime", "Lime Sorbet", Slot.Moth, moth = MothId.Sorbet,
        colours = MothColours(c(0xFF7FD84A), c(0xFFD6F5A8), c(0xFFFF9FC4), c(0xFF3F8A1E))),

    // Porch themes.
    Look("porch.night", "Night porch", Slot.Porch, gift = Gift.Default, porch = NIGHT_PORCH),
    Look("porch.birch", "Birch and lantern", Slot.Porch, porch = PorchStyle(
        c(0xFF1C2226), c(0xFF2A3136), c(0xFF12171A), Triple(c(0xFF8C8478), c(0xFF6A6358), c(0xFF3E3A33)),
        c(0xFFFFF0C8), c(0xFFFFC870))),
    Look("porch.barn", "Red barn", Slot.Porch, porch = PorchStyle(
        c(0xFF2A1210), c(0xFF3A1A16), c(0xFF180806), Triple(c(0xFF6E4A2C), c(0xFF4E3220), c(0xFF2A1A10)),
        c(0xFFFFC46A), c(0xFFFF8A3A))),
    Look("porch.harbour", "Harbour blue", Slot.Porch, porch = PorchStyle(
        c(0xFF0E1A2A), c(0xFF16283C), c(0xFF08111C), Triple(c(0xFFD8DCE0), c(0xFFA8AEB4), c(0xFF5E666E)),
        c(0xFFE6F2FF), c(0xFF8EC2FF))),
    Look("porch.golden", "Golden hour", Slot.Porch, gift = Gift.Supporter, porch = PorchStyle(
        c(0xFF2A1A0C), c(0xFF3C2610), c(0xFF180E05), Triple(c(0xFFD7A566), c(0xFFB07C42), c(0xFF7A4F24)),
        c(0xFFFFE08A), c(0xFFFFA83A))),
    Look("porch.frost", "Frost porch", Slot.Porch, season = Season.Frost, porch = PorchStyle(
        c(0xFF101C28), c(0xFF1C2C3C), c(0xFF0A121A), Triple(c(0xFFE4ECF2), c(0xFFB4C2CE), c(0xFF6A7A88)),
        c(0xFFEAF6FF), c(0xFF9CC8F0))),
    Look("porch.harvest", "Harvest porch", Slot.Porch, season = Season.Harvest, porch = PorchStyle(
        c(0xFF201208), c(0xFF301C0C), c(0xFF140A04), Triple(c(0xFF8A5A2A), c(0xFF6A4220), c(0xFF3A2410)),
        c(0xFFFFC870), c(0xFFFF7A2A))),
)

fun lookById(id: String): Look? = LOOK_CATALOG.find { it.id == id }

/** The look every player has in [slot] (for moths: their true colours, which have no entry). */
fun defaultLook(slot: Slot): Look? = LOOK_CATALOG.firstOrNull { it.slot == slot && it.gift == Gift.Default }

/** The starter pack's card back and the Supporter's porch: had with money, never with Fireflies. */
const val STARTER_LOOK = "back.firefly"
const val SUPPORTER_LOOK = "porch.golden"

/**
 * What the player is wearing right now, for the drawing code. Set from the saved progress on every
 * pass of the root screen; reading it inside a draw block redraws that block when it changes.
 */
internal object Wear {
    private val defaultBack = defaultLook(Slot.Back)!!.back!!
    private val defaultJar = defaultLook(Slot.Jar)!!.jar!!
    val defaultPorch = defaultLook(Slot.Porch)!!.porch!!

    var back by androidx.compose.runtime.mutableStateOf(defaultBack)
    var jar by androidx.compose.runtime.mutableStateOf(defaultJar)
    var porch by androidx.compose.runtime.mutableStateOf(defaultPorch)
    var moths by androidx.compose.runtime.mutableStateOf<Map<MothId, MothColours>>(emptyMap())

    fun from(progress: Progress) {
        back = progress.wearing(Slot.Back.key)?.back ?: defaultBack
        jar = progress.wearing(Slot.Jar.key)?.jar ?: defaultJar
        porch = progress.wearing(Slot.Porch.key)?.porch ?: defaultPorch
        moths = LEADS.mapNotNull { lead -> progress.wearing(Look.slotFor(lead))?.colours?.let { lead to it } }.toMap()
    }

    /** The porch's light colour when a theme changes it; null for the night porch's own warm light. */
    val tint: Color? get() = porch.takeIf { it != defaultPorch }?.glow
}

/** A trim's light, base and dark tones. */
internal val Trim.tones: Triple<Color, Color, Color> get() = when (this) {
    Trim.Brass -> Triple(Craft.brassLight, Craft.brass, Craft.brassDark)
    Trim.Copper -> Triple(Craft.copperLight, Craft.copper, Craft.copperDark)
    Trim.Pewter -> Triple(Color(0xFFE4E8EC), Color(0xFF9AA3AB), Color(0xFF474E54))
    Trim.Gilt -> Triple(Color(0xFFFFF0B8), Craft.gold, Craft.goldDeep)
}

/** The embossed rule of a trim: the same sweep [BrassRule] gives brass. */
internal val Trim.rule: androidx.compose.ui.graphics.Brush get() {
    val (light, base, dark) = tones
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(light, base, dark, base))
}
