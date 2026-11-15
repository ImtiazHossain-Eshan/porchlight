package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The game's icons, all drawn the same way: a 24-unit box, one 2-unit round stroke, no fills
 * except where [filled]. Nothing on the porch uses a text glyph as an icon.
 */
internal enum class Glyph(val path: String, val filled: Boolean = false) {
    Pause("M9 5 V19 M15 5 V19"),
    Play("M8 5 L19 12 L8 19 Z", filled = true),
    Gear(
        "M12 8.5 A3.5 3.5 0 1 0 12 15.5 A3.5 3.5 0 1 0 12 8.5 Z " +
            "M12 2.5 V5 M12 19 V21.5 M2.5 12 H5 M19 12 H21.5 M5.3 5.3 L7 7 M17 17 L18.7 18.7 M5.3 18.7 L7 17 M17 7 L18.7 5.3",
    ),
    Eye("M2 12 C5.5 6 18.5 6 22 12 C18.5 18 5.5 18 2 12 Z M12 9.5 A2.5 2.5 0 1 0 12 14.5 A2.5 2.5 0 1 0 12 9.5 Z"),
    Left("M15 5 L8 12 L15 19"),
    Right("M9 5 L16 12 L9 19"),
    Close("M6 6 L18 18 M18 6 L6 18"),
    Check("M5 12.5 L10 17.5 L19 7"),
    Lock("M6 11 H18 V20 H6 Z M8.5 11 V8 A3.5 3.5 0 0 1 15.5 8 V11 M12 14.5 V17"),
    Calendar("M4 6 H20 V20 H4 Z M4 10.5 H20 M8 3.5 V7.5 M16 3.5 V7.5"),
    Map("M4 6.5 L9 4.5 L15 6.5 L20 4.5 V17.5 L15 19.5 L9 17.5 L4 19.5 Z M9 4.5 V17.5 M15 6.5 V19.5"),
    Moth(
        "M12 8 V19 M10.5 5 L12 8 L13.5 5 " +
            "M12 10 C9 4 3 5 3 9 C3 13 8 13 12 12 M12 10 C15 4 21 5 21 9 C21 13 16 13 12 12 " +
            "M12 13 C9 13 6 15 7 18 C8 20 11 18 12 16 M12 13 C15 13 18 15 17 18 C16 20 13 18 12 16",
    ),
    Beetle(
        "M12 8 V20 M12 8 C7 8 6 12 6 15 C6 18.5 9 20 12 20 C15 20 18 18.5 18 15 C18 12 17 8 12 8 Z " +
            "M9.5 6.5 A2.5 2 0 1 0 14.5 6.5 M10 4.5 L8 2.5 M14 4.5 L16 2.5 M6.5 11 L3.5 9.5 M6 15 H3 M17.5 11 L20.5 9.5 M18 15 H21",
    ),
    Trophy("M8 4 H16 V10 A4 4 0 0 1 8 10 Z M8 6 H5 A3 3 0 0 0 8 10 M16 6 H19 A3 3 0 0 1 16 10 M12 14 V18 M8 20.5 H16"),
    Star("M12 3 L14.6 9 L21 9.6 L16.2 13.9 L17.7 20.3 L12 17 L6.3 20.3 L7.8 13.9 L3 9.6 L9.4 9 Z", filled = true),
    Flame("M12 3 C13 7 17 9 17 14 A5 5 0 0 1 7 14 C7 11 9 10 9.5 7.5 C11 8.5 11.5 10 11.5 11.5 C12.5 10 13 6 12 3 Z"),
    Clock("M12 4 A8 8 0 1 0 12 20 A8 8 0 1 0 12 4 Z M12 8 V12 L15 14"),
    Moon("M19 14.5 A8 8 0 1 1 9.5 5 A6.5 6.5 0 0 0 19 14.5 Z"),
    Sound("M4 10 H7 L11 6 V18 L7 14 H4 Z M15 9 A4 4 0 0 1 15 15 M17.5 6.5 A8 8 0 0 1 17.5 17.5"),
    Cricket("M4 15 C6 10 13 9 18 12 C20 13 20 16 17 16 H7 M9 16 L7 20 M14 16 L16 20 M18 12 L21 8 M16 11 L18 6"),
    Buzz("M9 4 H15 V20 H9 Z M5.5 8.5 V15.5 M18.5 8.5 V15.5 M2.5 10.5 V13.5 M21.5 10.5 V13.5"),
    Motion("M3 8 C7 4 10 12 14 8 S19 5 21 8 M3 16 C7 12 10 20 14 16 S19 13 21 16"),
    Share("M12 15 V4 M8 8 L12 4 L16 8 M5 13 V20 H19 V13"),
    Porch("M4 11 L12 4 L20 11 M6 10 V20 H18 V10 M10 20 V14 H14 V20"),
    Book("M4 5 C7 4 10 4 12 6 C14 4 17 4 20 5 V19 C17 18 14 18 12 20 C10 18 7 18 4 19 Z M12 6 V20"),
}

private val line = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Draws [glyph] [width] pixels wide, centred on [centre]. */
internal fun DrawScope.glyph(glyph: Glyph, centre: Offset, width: Float, tint: Color, alpha: Float = 1f) {
    val s = width / 24f
    translate(centre.x - width / 2f, centre.y - width / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            if (glyph.filled) drawPath(svg(glyph.path), tint, alpha = alpha)
            drawPath(svg(glyph.path), tint, alpha = alpha, style = line)
        }
    }
}

@Composable
internal fun GlyphArt(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { glyph(glyph, Offset(size.width / 2f, size.height / 2f), size.minDimension, tint) }
}
