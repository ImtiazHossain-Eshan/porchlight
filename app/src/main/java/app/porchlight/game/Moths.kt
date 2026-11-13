package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

enum class MothId(val label: String, val role: String, val perkName: String, val perk: String) {
    Wick("Wick", "The night owl", "Long-life bulb", "The round starts with 4 extra seconds."),
    Ember("Ember", "The sprinter", "Hot streak", "Your streak multiplier starts at ×2."),
    Atlas("Atlas", "The mapmaker", "Second look", "Once a round, flash every card open for a moment."),
    Dot("Dot", "The counter", "Quick hands", "A wrong pair folds back in half the time."),
    Iris("Iris", "The watcher", "Long look", "The opening peek lasts 3.4 seconds instead of 2."),
    Sorbet("Sorbet", "The sweet tooth", "Top-up", "Every pair you find adds 1.5 seconds to the bulb."),
}

private val INK = Color(0xFF15181A)
private val EYE_WHITE = Color(0xFFFFF7DC)
private const val ANTENNA = "M62.6 32.4 C66 24 71 18 77.5 14.5"
private const val ABDOMEN = "M53.5 56 Q60 52 66.5 56 Q67.5 78 60 94 Q52.5 78 53.5 56 Z"

private enum class Wing { Fore, Hind }

private sealed interface Mark {
    val on: Wing

    data class Dot(override val on: Wing, val x: Float, val y: Float, val r: Float, val fill: Color) : Mark
    data class Eye(override val on: Wing, val x: Float, val y: Float, val r: Float, val iris: Color) : Mark
    data class Line(override val on: Wing, val d: String, val color: Color, val width: Float) : Mark
    data class Shape(override val on: Wing, val d: String, val fill: Color) : Mark
}

private class Spec(
    val fore: String,
    val hind: String,
    val foreFill: Color,
    val hindFill: Color,
    val marks: List<Mark>,
    val body: Color,
    val antenna: Color,
    val feathery: Boolean = false,
    val stripes: Boolean = false,
    val collar: Color? = null,
    val plump: Boolean = false,
)

private val SPECS: Map<MothId, Spec> = mapOf(
    MothId.Wick to Spec(
        fore = "M64 46 C74 26 98 14 114 20 C113 38 100 54 82 60 C73 62 66 58 64 54 Z",
        hind = "M64 54 C78 56 94 62 95 76 C95 86 88 92 85 102 C84 108 86 113 84 117 C79 112 78 103 74 95 C68 85 64 70 64 54 Z",
        foreFill = Color(0xFFC8EEC2), hindFill = Color(0xFFC8EEC2),
        marks = listOf(
            Mark.Line(Wing.Fore, "M64 46 C74 26 98 14 114 20", Color(0xFF7A2F52), 9f),
            Mark.Eye(Wing.Fore, 92f, 39f, 5f, Color(0xFFF4B63F)),
            Mark.Eye(Wing.Hind, 82f, 74f, 5f, Color(0xFFF4B63F)),
        ),
        body = Color(0xFFF8F3E6), antenna = Color(0xFFC9862A), feathery = true,
    ),
    MothId.Ember to Spec(
        fore = "M64 47 C78 36 102 28 118 30 C110 44 92 55 76 59 C69 61 65 57 64 53 Z",
        hind = "M64 53 C72 54 88 58 92 67 C87 77 75 78 69 72 C65 67 64 60 64 53 Z",
        foreFill = Color(0xFFE0672B), hindFill = Color(0xFFF58AA4),
        marks = listOf(
            Mark.Line(Wing.Fore, "M80 30 L90 60", Color(0xFF8D2F12), 6f),
            Mark.Line(Wing.Fore, "M95 24 L105 54", Color(0xFF8D2F12), 6f),
            Mark.Line(Wing.Hind, "M72 77 C82 79 90 73 93 64", INK, 5f),
        ),
        body = Color(0xFFE0672B), antenna = INK, stripes = true,
    ),
    MothId.Atlas to Spec(
        fore = "M64 44 C70 22 94 8 110 10 C119 12 120 22 113 27 C117 44 103 60 84 64 C74 66 67 60 64 54 Z",
        hind = "M64 54 C80 57 104 64 104 84 C100 100 81 104 71 93 C65 84 64 68 64 54 Z",
        foreFill = Color(0xFFB4552F), hindFill = Color(0xFFB4552F),
        marks = listOf(
            Mark.Line(Wing.Fore, "M110 10 C121 26 108 56 84 64", Color(0xFF6F2D19), 12f),
            Mark.Line(Wing.Hind, "M104 72 C106 96 84 106 71 93", Color(0xFF6F2D19), 12f),
            Mark.Shape(Wing.Fore, "M80 34 L95 38 L84 50 Z", Color(0xFFF8E9C9)),
            Mark.Shape(Wing.Hind, "M78 70 L91 77 L78 85 Z", Color(0xFFF8E9C9)),
        ),
        body = Color(0xFFB4552F), antenna = Color(0xFF6F2D19), feathery = true, collar = Color(0xFFF8E9C9),
    ),
    MothId.Dot to Spec(
        fore = "M64 46 C73 29 94 21 108 25 C112 41 102 56 86 62 C75 65 67 60 64 54 Z",
        hind = "M64 54 C75 56 92 62 94 76 C92 88 78 92 70 84 C65 78 64 66 64 54 Z",
        foreFill = Color(0xFFF8F0DA), hindFill = Color(0xFFF59A2C),
        marks = listOf(
            Mark.Dot(Wing.Fore, 80f, 38f, 3.4f, INK),
            Mark.Dot(Wing.Fore, 93f, 32f, 2.8f, INK),
            Mark.Dot(Wing.Fore, 98f, 44f, 3.2f, INK),
            Mark.Dot(Wing.Fore, 85f, 51f, 2.8f, INK),
            Mark.Dot(Wing.Fore, 73f, 49f, 2.2f, INK),
            Mark.Dot(Wing.Hind, 79f, 70f, 3.4f, INK),
            Mark.Dot(Wing.Hind, 86f, 80f, 2.6f, INK),
        ),
        body = Color(0xFFF59A2C), antenna = INK, stripes = true,
    ),
    MothId.Iris to Spec(
        fore = "M64 45 C75 25 100 17 114 25 C116 43 104 58 86 64 C75 66 67 60 64 54 Z",
        hind = "M64 54 C79 56 100 62 102 80 C100 96 82 102 71 92 C65 84 64 68 64 54 Z",
        foreFill = Color(0xFFB7A6EA), hindFill = Color(0xFFB7A6EA),
        marks = listOf(
            Mark.Line(Wing.Fore, "M114 25 C116 43 104 58 86 64", Color(0xFF6D58B3), 9f),
            Mark.Line(Wing.Hind, "M102 80 C100 96 82 102 71 92", Color(0xFF6D58B3), 9f),
            Mark.Eye(Wing.Fore, 91f, 41f, 8f, Color(0xFF2F6FDC)),
            Mark.Eye(Wing.Hind, 83f, 77f, 9f, Color(0xFF2F6FDC)),
        ),
        body = Color(0xFF8F7FD0), antenna = Color(0xFF6D58B3), feathery = true,
    ),
    MothId.Sorbet to Spec(
        fore = "M64 47 C71 33 88 27 101 31 C103 44 95 56 82 60 C73 62 67 58 64 54 Z",
        hind = "M64 54 C73 56 86 60 88 72 C86 82 76 86 69 80 C65 74 64 64 64 54 Z",
        foreFill = Color(0xFFF78FB3), hindFill = Color(0xFFFFE26A),
        marks = listOf(Mark.Line(Wing.Fore, "M79 24 L87 64", Color(0xFFFFE26A), 14f)),
        body = Color(0xFFFFE26A), antenna = Color(0xFFF0689A), feathery = true, plump = true,
    ),
)

private val pathCache = HashMap<String, Path>()

/** Parses SVG path data once; every drawing here uses a 120-unit square. */
private fun svg(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private val outline = Stroke(width = 2.4f, join = StrokeJoin.Round)

/** Draws [block] in a centered 120 x 120 space that fills the canvas. */
private inline fun DrawScope.inUnitSquare(block: DrawScope.() -> Unit) {
    val s = size.minDimension / 120f
    translate((size.width - 120f * s) / 2f, (size.height - 120f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { block() }
    }
}

private fun DrawScope.wing(spec: Spec, wing: Wing) {
    val path = svg(if (wing == Wing.Fore) spec.fore else spec.hind)
    drawPath(path, if (wing == Wing.Fore) spec.foreFill else spec.hindFill)
    clipPath(path) {
        spec.marks.filter { it.on == wing }.forEach { mark ->
            when (mark) {
                is Mark.Dot -> drawCircle(mark.fill, mark.r, Offset(mark.x, mark.y))
                is Mark.Eye -> {
                    val c = Offset(mark.x, mark.y)
                    drawCircle(INK, mark.r, c)
                    drawCircle(EYE_WHITE, mark.r * 0.72f, c)
                    drawCircle(mark.iris, mark.r * 0.46f, c)
                    drawCircle(INK, mark.r * 0.2f, c)
                }
                is Mark.Line -> drawPath(svg(mark.d), mark.color, style = Stroke(mark.width))
                is Mark.Shape -> {
                    drawPath(svg(mark.d), mark.fill)
                    drawPath(svg(mark.d), INK, style = Stroke(1.6f, join = StrokeJoin.Round))
                }
            }
        }
    }
    drawPath(path, INK, style = outline)
}

/**
 * A moth with its wings open. [open] runs from 0 (wings folded edge-on) to 1,
 * which is how a card turning over reads.
 */
@Composable
fun MothArt(id: MothId, modifier: Modifier = Modifier, open: Float = 1f) {
    val spec = SPECS.getValue(id)
    val feathered = remember { Stroke(width = 6.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f, 2.1f))) }
    Canvas(modifier) {
        inUnitSquare {
            for (mirror in listOf(false, true)) {
                scale(if (mirror) -1f else 1f, 1f, pivot = Offset(60f, 0f)) {
                    scale(open.coerceIn(0.08f, 1f), 1f, pivot = Offset(60f, 56f)) {
                        wing(spec, Wing.Hind)
                        wing(spec, Wing.Fore)
                    }
                    if (spec.feathery) drawPath(svg(ANTENNA), spec.antenna, style = feathered)
                    drawPath(
                        svg(ANTENNA),
                        if (spec.feathery) spec.antenna else INK,
                        style = Stroke(2.2f, cap = StrokeCap.Round),
                    )
                }
            }

            drawPath(svg(ABDOMEN), spec.body)
            drawPath(svg(ABDOMEN), INK, style = outline)
            if (spec.stripes) {
                listOf(64f, 71.5f, 79f).forEachIndexed { i, y ->
                    drawLine(INK, Offset(55.5f + i * 1.1f, y), Offset(64.5f - i * 1.1f, y), 2.6f, StrokeCap.Round)
                }
            }
            val rx = if (spec.plump) 10f else 8f
            val ry = if (spec.plump) 10.5f else 9.5f
            val thorax = Offset(60f - rx, 48.5f - ry)
            drawOval(spec.body, thorax, Size(rx * 2, ry * 2))
            drawOval(INK, thorax, Size(rx * 2, ry * 2), style = outline)
            spec.collar?.let {
                drawPath(svg("M54 44 Q60 48 66 44"), it, style = Stroke(3f, cap = StrokeCap.Round))
            }
            drawCircle(spec.body, 5.6f, Offset(60f, 36.5f))
            drawCircle(INK, 5.6f, Offset(60f, 36.5f), style = outline)
            drawCircle(INK, 1.15f, Offset(57.7f, 35.8f))
            drawCircle(INK, 1.15f, Offset(62.3f, 35.8f))
        }
    }
}

/** A moth at rest with its wings folded: every card looks like this face down. */
@Composable
fun MothAtRest(color: Color, line: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        inUnitSquare {
            drawPath(
                svg("M57.4 30 C54 23 49 18 43 15 M62.6 30 C66 23 71 18 77 15"),
                color,
                style = Stroke(2.6f, cap = StrokeCap.Round),
            )
            drawPath(svg("M60 28 C71 36 89 80 85 98 C77 103 67 100 60 93 C53 100 43 103 35 98 C31 80 49 36 60 28 Z"), color)
            drawLine(line, Offset(60f, 36f), Offset(60f, 90f), 2.6f, StrokeCap.Round)
        }
    }
}

/** The porch bulb: the round's clock. */
@Composable
fun BulbArt(glass: Color, line: Color, filament: Color, socket: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 160f
        translate((size.width - 112f * s) / 2f, (size.height - 160f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                val glassPath = svg("M44 26 C44 44 14 56 14 88 A42 42 0 0 0 98 88 C98 56 68 44 68 26 Z")
                drawPath(glassPath, glass, style = Fill)
                drawPath(glassPath, line, style = Stroke(3f, join = StrokeJoin.Round))
                drawPath(svg("M50 27 V66 M62 27 V66"), line, style = Stroke(2f))
                drawPath(
                    svg("M40 80 L46 66 L53 80 L59 66 L66 80 L72 66"),
                    filament,
                    style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawRect(socket, Offset(40f, 0f), Size(32f, 27f))
                drawRect(line, Offset(40f, 0f), Size(32f, 27f), style = Stroke(3f))
                drawPath(svg("M40 9 H72 M40 18 H72"), line, style = Stroke(2.5f))
            }
        }
    }
}
