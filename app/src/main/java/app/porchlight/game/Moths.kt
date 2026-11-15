package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Every card face. The first six can lead a round, and each bends its rules; the next four only join
 * the board on later nights. [Leaf] is the dead leaf, the dud card.
 */
enum class MothId(
    val label: String,
    val role: String = "",
    val perkName: String = "",
    val perk: String = "",
    val isLead: Boolean = true,
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
    Leaf("Dead leaf", isLead = false),
}

/** The six moths that can lead a round. */
val LEADS: List<MothId> = MothId.entries.filter { it.isLead }

private val INK = Color(0xFF2A1A10)
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
) {
    val foreMarks = marks.filter { it.on == Wing.Fore }
    val hindMarks = marks.filter { it.on == Wing.Hind }
}

private val SPECS: Map<MothId, Spec> = mapOf(
    MothId.Luna to Spec(
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
    MothId.Wick to Spec(
        fore = "M64 47 C78 36 102 28 118 30 C110 44 92 55 76 59 C69 61 65 57 64 53 Z",
        hind = "M64 53 C72 54 88 58 92 67 C87 77 75 78 69 72 C65 67 64 60 64 53 Z",
        foreFill = Color(0xFFE8452A), hindFill = Color(0xFF2B1814),
        marks = listOf(
            Mark.Line(Wing.Fore, "M80 30 L90 60", Color(0xFF8D2F12), 6f),
            Mark.Line(Wing.Fore, "M95 24 L105 54", Color(0xFF8D2F12), 6f),
            Mark.Line(Wing.Hind, "M72 77 C82 79 90 73 93 64", Color(0xFFE8452A), 5f),
        ),
        body = Color(0xFFE8452A), antenna = INK, stripes = true,
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
        foreFill = Color(0xFFFBF6E8), hindFill = Color(0xFFFBF6E8),
        marks = listOf(
            Mark.Dot(Wing.Fore, 80f, 38f, 3.4f, INK),
            Mark.Dot(Wing.Fore, 93f, 32f, 2.8f, INK),
            Mark.Dot(Wing.Fore, 98f, 44f, 3.2f, INK),
            Mark.Dot(Wing.Fore, 85f, 51f, 2.8f, INK),
            Mark.Dot(Wing.Fore, 73f, 49f, 2.2f, INK),
            Mark.Dot(Wing.Hind, 79f, 70f, 3.4f, INK),
            Mark.Dot(Wing.Hind, 86f, 80f, 2.6f, INK),
        ),
        body = Color(0xFFE4DCC8), antenna = INK, stripes = true,
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
        foreFill = Color(0xFF2FA8F0), hindFill = Color(0xFF9BE0FF),
        marks = listOf(Mark.Line(Wing.Fore, "M79 24 L87 64", Color(0xFFFFE26A), 14f)),
        body = Color(0xFFFFE26A), antenna = Color(0xFF1C78BE), feathery = true, plump = true,
    ),
    MothId.Sulphur to Spec(
        fore = "M64 46 C72 30 92 22 106 28 C110 42 100 56 84 60 C74 62 66 58 64 54 Z",
        hind = "M64 54 C76 56 90 62 90 74 C88 84 76 86 70 80 C65 74 64 64 64 54 Z",
        foreFill = Color(0xFFFFE03A), hindFill = Color(0xFFFFE03A),
        marks = listOf(
            Mark.Line(Wing.Fore, "M100 24 C110 34 110 46 100 56", Color(0xFF7A4A12), 8f),
            Mark.Dot(Wing.Fore, 86f, 42f, 3.2f, Color(0xFF7A4A12)),
            Mark.Dot(Wing.Hind, 78f, 70f, 4f, Color(0xFFE5862B)),
        ),
        body = Color(0xFFFFE03A), antenna = Color(0xFF7A4A12), plump = true,
    ),
    MothId.Rose to Spec(
        fore = "M64 46 C70 30 90 20 104 26 C110 36 104 50 90 56 C80 60 68 58 64 54 Z",
        hind = "M64 54 C76 56 90 62 90 72 C88 80 82 84 80 94 C78 102 76 108 74 112 C70 104 70 94 68 86 C65 78 64 66 64 54 Z",
        foreFill = Color(0xFFF0479A), hindFill = Color(0xFFF0479A),
        marks = listOf(
            Mark.Shape(Wing.Fore, "M72 42 L98 30 L101 36 L75 48 Z", Color(0xFFFFE6F0)),
            Mark.Line(Wing.Hind, "M70 66 C76 70 84 70 90 68", Color(0xFF8E1450), 4f),
        ),
        body = Color(0xFFF0479A), antenna = INK, stripes = true,
    ),
    MothId.Midnight to Spec(
        fore = "M64 44 C72 24 96 12 112 18 C116 30 112 40 106 46 C110 52 100 62 84 64 C74 66 67 60 64 54 Z",
        hind = "M64 54 C80 56 100 64 100 80 C96 96 80 100 71 90 C65 82 64 68 64 54 Z",
        foreFill = Color(0xFF2B3A8C), hindFill = Color(0xFF2B3A8C),
        marks = listOf(
            Mark.Shape(Wing.Fore, "M84 28 Q98 32 100 46 Q92 36 84 28 Z", Color(0xFFDDE4F0)),
            Mark.Shape(Wing.Hind, "M76 70 Q90 74 90 88 Q84 78 76 70 Z", Color(0xFFDDE4F0)),
        ),
        body = Color(0xFF3B4AA0), antenna = Color(0xFFDDE4F0), feathery = true,
    ),
    MothId.Moss to Spec(
        fore = "M64 46 C74 28 96 18 110 22 C114 38 104 54 86 60 C75 63 67 59 64 54 Z",
        hind = "M64 54 C77 56 94 62 96 76 C94 90 80 94 71 86 C65 80 64 66 64 54 Z",
        foreFill = Color(0xFF2F7A3A), hindFill = Color(0xFF2F7A3A),
        marks = listOf(
            Mark.Line(Wing.Fore, "M68 52 L76 40 L84 50 L92 36 L98 46 L108 28", Color(0xFFF2EBC9), 7f),
            Mark.Line(Wing.Hind, "M68 70 L76 64 L82 74 L90 66 L96 76", Color(0xFFF2EBC9), 7f),
        ),
        body = Color(0xFF2F7A3A), antenna = INK,
    ),
)

private val GOLD_LINE = Color(0xFFC98A12)
private val LEAF = Color(0xFF8A5A2B)
private val LEAF_DARK = Color(0xFF4A2E14)
private const val LEAF_SHAPE = "M60 14 C88 30 96 64 78 94 C72 102 66 106 60 108 C54 106 48 102 42 94 C24 64 32 30 60 14 Z"
private const val LEAF_VEINS = "M60 22 V106 M60 46 L76 36 M60 62 L80 52 M60 78 L76 72 M60 46 L44 36 M60 62 L40 54 M60 80 L44 74"
private const val LEAF_HOLES = "M70 44 C74 42 76 48 72 50 C68 50 67 46 70 44 Z M48 70 C52 68 54 74 50 76 C46 76 45 72 48 70 Z"

private val pathCache = HashMap<String, Path>()

/** Parses SVG path data once; every drawing here uses a 120-unit square. */
internal fun svg(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private val outline = Stroke(width = 2.4f, join = StrokeJoin.Round)
private val feathered = Stroke(width = 6.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f, 2.1f)))
private val antennaLine = Stroke(2.2f, cap = StrokeCap.Round)
private val markLine = Stroke(1.6f, join = StrokeJoin.Round)
private val collarLine = Stroke(3f, cap = StrokeCap.Round)
private val restLine = Stroke(2.6f, cap = StrokeCap.Round)
private val MIRROR_PIVOT = Offset(60f, 0f)
private val WING_PIVOT = Offset(60f, 56f)

/** Draws [block] in a centered 120 x 120 space that fills the canvas. */
internal inline fun DrawScope.inUnitSquare(block: DrawScope.() -> Unit) {
    val s = size.minDimension / 120f
    translate((size.width - 120f * s) / 2f, (size.height - 120f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { block() }
    }
}

private fun DrawScope.wing(spec: Spec, wing: Wing, flat: Color?, line: Color) {
    val path = svg(if (wing == Wing.Fore) spec.fore else spec.hind)
    drawPath(path, flat ?: if (wing == Wing.Fore) spec.foreFill else spec.hindFill)
    if (flat != null) return
    clipPath(path) {
        val marks = if (wing == Wing.Fore) spec.foreMarks else spec.hindMarks
        for (i in marks.indices) {
            when (val mark = marks[i]) {
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
                    drawPath(svg(mark.d), INK, style = markLine)
                }
            }
        }
    }
    drawPath(path, line, style = outline)
}

/**
 * Draws a moth into the 120-unit square at the current transform. [open] runs from 0 (wings folded
 * edge-on) to 1. With [flat] set the moth is a single-colour silhouette.
 */
internal fun DrawScope.mothUnit(id: MothId, open: Float, flat: Color? = null, gold: Boolean = false) {
    if (id == MothId.Leaf) {
        // The dud card: a dead, moth-eaten leaf.
        drawPath(svg(LEAF_SHAPE), flat ?: LEAF)
        if (flat == null) {
            drawPath(svg(LEAF_HOLES), Color(0xFFFFF4DA))
            drawPath(svg(LEAF_VEINS), LEAF_DARK, style = antennaLine)
        }
        drawPath(svg(LEAF_SHAPE), flat ?: LEAF_DARK, style = outline)
        return
    }
    val spec = SPECS.getValue(id)
    val ink = flat ?: if (gold) GOLD_LINE else INK
    for (side in 0..1) {
        scale(if (side == 1) -1f else 1f, 1f, pivot = MIRROR_PIVOT) {
            scale(open.coerceIn(0.08f, 1f), 1f, pivot = WING_PIVOT) {
                wing(spec, Wing.Hind, flat, ink)
                wing(spec, Wing.Fore, flat, ink)
            }
            if (spec.feathery) drawPath(svg(ANTENNA), flat ?: spec.antenna, style = feathered)
            drawPath(svg(ANTENNA), if (spec.feathery) flat ?: spec.antenna else ink, style = antennaLine)
        }
    }

    drawPath(svg(ABDOMEN), flat ?: spec.body)
    drawPath(svg(ABDOMEN), ink, style = outline)
    if (spec.stripes && flat == null) {
        for (i in 0..2) {
            val y = 64f + i * 7.5f
            drawLine(INK, Offset(55.5f + i * 1.1f, y), Offset(64.5f - i * 1.1f, y), 2.6f, StrokeCap.Round)
        }
    }
    val rx = if (spec.plump) 10f else 8f
    val ry = if (spec.plump) 10.5f else 9.5f
    val thorax = Offset(60f - rx, 48.5f - ry)
    drawOval(flat ?: spec.body, thorax, Size(rx * 2, ry * 2))
    drawOval(ink, thorax, Size(rx * 2, ry * 2), style = outline)
    if (flat == null) spec.collar?.let { drawPath(svg("M54 44 Q60 48 66 44"), it, style = collarLine) }
    drawCircle(flat ?: spec.body, 5.6f, Offset(60f, 36.5f))
    drawCircle(ink, 5.6f, Offset(60f, 36.5f), style = outline)
    if (flat == null) {
        drawCircle(INK, 1.15f, Offset(57.7f, 35.8f))
        drawCircle(INK, 1.15f, Offset(62.3f, 35.8f))
    }
}

/** Draws a moth [width] pixels wide, centred on [centre]: the moths in flight use this. */
internal fun DrawScope.moth(
    id: MothId,
    centre: Offset,
    width: Float,
    open: Float,
    tilt: Float = 0f,
    flat: Color? = null,
    gold: Boolean = false,
) {
    val s = width / 120f
    translate(centre.x - width / 2f, centre.y - width / 2f) {
        rotate(tilt, pivot = Offset(width / 2f, width / 2f)) {
            scale(s, s, pivot = Offset.Zero) { mothUnit(id, open, flat, gold) }
        }
    }
}

@Composable
fun MothArt(id: MothId, modifier: Modifier = Modifier, open: Float = 1f, flat: Color? = null, gold: Boolean = false) {
    Canvas(modifier) { inUnitSquare { mothUnit(id, open, flat, gold) } }
}

/** A moth at rest with its wings folded: every card looks like this face down. */
@Composable
fun MothAtRest(color: Color, line: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        inUnitSquare {
            drawPath(svg("M57.4 30 C54 23 49 18 43 15 M62.6 30 C66 23 71 18 77 15"), color, style = restLine)
            drawPath(svg("M60 28 C71 36 89 80 85 98 C77 103 67 100 60 93 C53 100 43 103 35 98 C31 80 49 36 60 28 Z"), color)
            drawLine(line, Offset(60f, 36f), Offset(60f, 90f), 2.6f, StrokeCap.Round)
        }
    }
}
