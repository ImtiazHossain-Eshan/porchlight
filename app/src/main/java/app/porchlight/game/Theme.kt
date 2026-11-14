package app.porchlight.game

import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

/** The night porch. Every colour on screen comes from here. */
internal object Dusk {
    val wallTop = Color(0xFF0E1A24)
    val wallBottom = Color(0xFF16283A)
    val plank = Color(0xFF1C3346)
    val panel = Color(0xFF14232F)
    val panelEdge = Color(0xFF2E4A60)

    val glow = Color(0xFFFFD27A)
    val glowDeep = Color(0xFFFF9F43)

    val cardBack = Color(0xFF1E3446)
    val cardEdge = Color(0xFF2E4A60)
    val cardEmblem = Color(0xFF2A465C)
    val paper = Color(0xFFFFF4DA)
    val paperInk = Color(0xFF1B1710)
    val slot = Color(0xFF0A141C)
    val shadow = Color(0xFF000814)

    val amber = Color(0xFFFFB938)
    val ember = Color(0xFFFF5A3C)
    val ink = Color(0xFFF3EBDD)
    val inkSoft = Color(0xFF9DB2C4)
    val locked = Color(0xFF3A4E5E)
}

/** Fredoka (SIL Open Font License), a variable font: the weight picks the instance. */
internal val DisplayFont: FontFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Numbers, titles and bursts. Tabular figures keep a counting score from jittering. */
internal val Display = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
internal val Label = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 11.sp,
    letterSpacing = 1.2.sp,
)

internal fun now() = SystemClock.elapsedRealtime()

internal fun Int.grouped(): String = String.format(Locale.US, "%,d", this)
