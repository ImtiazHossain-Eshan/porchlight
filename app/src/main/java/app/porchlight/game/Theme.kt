/*
 * DIRECTION CONTRACT (the owner's concept art pins this world; seed: brief-pinned, no roll)
 * THESIS: a porch at night built by hand. Every surface is a made object: timber, brass, parchment,
 *   velvet, glass. Refuses the flat dark-mode game UI of pills and glass cards.
 * OWN-WORLD: night-stained planks; walnut and oak panels with grain and bevels; brass and copper
 *   plates, rivets, gears and pipes; parchment with ink; plum velvet; gilded navy for rewards.
 *   Light colours are states: amber burning, teal bioluminescence for the peek and matches, gold
 *   for Overdrive, red for the last seconds. Alfa Slab One for signs and numbers.
 * STORY: light the porch, memorise, find the pairs before the bulb dies, collect moths and badges.
 * FIRST VIEWPORT: the lobby. Logo top; a lit mason jar hanging centre with felt moths around it;
 *   an oak sign reading LIGHT THE PORCH below; four brass-framed menu tiles along the bottom.
 * FORM: the concept art's 12-screen walkthrough, position 1 of 1.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the
 *   verdict, DESIGN.md, and every shipping raster carrying its provenance.
 */
package app.porchlight.game

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The porch at night. Every colour on screen comes from here or from [Craft]. */
internal object Dusk {
    // The wall: planks stained by the night.
    val wallTop = Color(0xFF10171F)
    val wallBottom = Color(0xFF1A242E)
    val plank = Color(0xFF0A0F15)

    // Timber panels and their brass trim.
    val panel = Color(0xFF2E1D12)
    val panelEdge = Color(0xFF7A5A2A)

    val glow = Color(0xFFFFD27A)
    val glowDeep = Color(0xFFFF9F43)

    val cardBack = Color(0xFF2A1B11)
    val cardEdge = Color(0xFF8C6A36)
    val cardEmblem = Color(0xFFB8873B)
    val paper = Color(0xFFEBD9B0)
    val paperInk = Color(0xFF2E2014)
    val slot = Color(0xFF0E0906)
    val shadow = Color(0xFF040201)

    val amber = Color(0xFFFFC24A)
    val ember = Color(0xFFFF4B36)
    val ink = Color(0xFFF6E8CC)
    val inkSoft = Color(0xFFC4AC88)
    val locked = Color(0xFF6A5642)
}

/** The materials and the state lights. */
internal object Craft {
    val brass = Color(0xFFB8873B)
    val brassLight = Color(0xFFF2D38A)
    val brassDark = Color(0xFF5E4019)
    val copper = Color(0xFFB5673A)
    val copperLight = Color(0xFFE9A06E)
    val copperDark = Color(0xFF5C2F17)
    val iron = Color(0xFF3A3F45)
    val ironDark = Color(0xFF191C20)

    val parchment = Color(0xFFEBD9B0)
    val parchmentShade = Color(0xFFC6A870)
    val parchmentInk = Color(0xFF3B2A17)
    val parchmentInkSoft = Color(0xFF6F5636)

    val velvet = Color(0xFF5A2450)
    val velvetDeep = Color(0xFF260F24)

    val navy = Color(0xFF1B2A3D)
    val navyDeep = Color(0xFF101925)
    val gold = Color(0xFFFFCB55)
    val goldDeep = Color(0xFFB07A1E)

    /** Bioluminescence: the peek, and every found pair. */
    val bio = Color(0xFF3CF2D0)
    val bioDeep = Color(0xFF1C8FD8)

    /** The last seconds. */
    val danger = Color(0xFFFF3B2E)
    val dangerDeep = Color(0xFF7A0F0A)
}

/**
 * Alfa Slab One (SIL Open Font License): wood type, only for the big words. Screen titles, the
 * Results headline, the score, the level number, OVERDRIVE, the peek count and GO!.
 */
internal val DisplayFont: FontFamily = FontFamily(Font(R.font.alfa_slab_one, FontWeight.Normal))

/**
 * Fredoka (SIL Open Font License): a rounded, friendly sans for everything else, with real
 * weights. It is a variable font, so each weight sets its axis explicitly.
 */
@OptIn(ExperimentalTextApi::class)
internal val BodyFont: FontFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/**
 * The type scale. Nothing is set outside it, and no card or panel uses more than three of these.
 * Micro is for the share card only.
 */
internal object TypeScale {
    val Hero = 56.sp
    val Title = 28.sp
    val Heading = 18.sp
    val Body = 15.sp
    val Label = 11.sp
    val Micro = 9.sp
}

/** Big words in wood type. Tabular figures keep a changing number from jittering. */
internal val Display = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum", letterSpacing = 0.5.sp)

/** Every line, caption and sentence. Tabular figures, so changing numbers stay still. */
internal val BodyText = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = TypeScale.Body, fontFeatureSettings = "tnum")

/** Small labels: capitals with +1.5 sp tracking, in the body font. Write them in capitals. */
internal val Label = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = TypeScale.Label,
    letterSpacing = 1.5.sp,
    fontFeatureSettings = "tnum",
)

// One text treatment per surface. Use these instead of ad-hoc shadows.

/** Painted on wood (screen signs, plank buttons): ink pressed into the grain, a light edge below. */
internal val TextCarved = Display.copy(shadow = Shadow(Color.White.copy(alpha = 0.32f), Offset(0f, 1.5f), 0f))

/**
 * Lit by the bulb (the score, OVERDRIVE, the Results headline, the PORCHLIGHT title): an amber
 * gradient with a soft glow around it, and no dark shadow. Don't pass a colour with it.
 */
internal val TextGlow = Display.copy(
    brush = Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFF2A93B))),
    shadow = Shadow(Dusk.glowDeep.copy(alpha = 0.75f), Offset.Zero, 22f),
)

/** Engraved in metal (the level number, badge text, coin labels): a light edge below the cut. */
internal val TextEngraved = Display.copy(shadow = Shadow(Color.White.copy(alpha = 0.45f), Offset(0f, 1f), 0f))

/** On paper (cards, the best-days list): plain ink, no effects. */
internal val TextInk = BodyText

/** A glow the colour of [colour], for a lit word in another colour (red for the last number). */
internal fun glowOf(colour: Color, blur: Float = 22f) = Shadow(colour.copy(alpha = 0.75f), Offset.Zero, blur)

internal fun now() = SystemClock.elapsedRealtime()
