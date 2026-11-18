package app.porchlight.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The moths that keep the sign company, and the share of the board's width each one perches at. */
private val SIGN_MOTHS = listOf(MothId.Sulphur to 0.26f, MothId.Rose to 0.76f)

/**
 * The porch's name: PORCHLIGHT cut into a dark board and filled with gold leaf, hung on two short
 * chains under the title. The board swings gently and a glint crosses the gold now and then. Two
 * small moths keep it company: they flutter round it on loops that pass behind the board, and every
 * so often each one lands on its top edge, rests with its wings breathing, and takes off again.
 * Under Reduce motion the board hangs still and the moths sit on it.
 */
@Composable
internal fun CarvedTitle(calm: Boolean) {
    val loop = if (calm) null else rememberInfiniteTransition(label = "title")
    val clock = loop?.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val t = { clock?.value ?: 0f }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val chain = 14.dp
    Box(
        Modifier
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                if (!calm) rotationZ = sin(t() * 1.1f) * 1.6f
            }
            .onSizeChanged { box = it }
            .semantics(mergeDescendants = true) { heading(); contentDescription = "Porchlight" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The chains.
            Canvas(Modifier.width(220.dp).height(chain)) {
                for (x in listOf(size.width * 0.18f, size.width * 0.82f)) {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Craft.brassDark, Offset(x, y), Offset(x, y + 5.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                        y += 7.dp.toPx()
                    }
                }
            }
            Box(
                Modifier
                    .widthIn(min = 240.dp)
                    .shadow(10.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                    .plank(Wood.Ebony, corner = 8.dp, elevation = 0.dp, seed = 31)
                    .drawBehind {
                        // A brass rule inset round the board.
                        val inset = 4.dp.toPx()
                        drawRoundRect(BrassRule, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(5.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (!calm) {
                            // The glint: across the gold in a moment, then a rest.
                            val run = ((t() % 5f) / 1.4f)
                            if (run < 1f) {
                                val x = -size.width * 0.3f + run * size.width * 1.6f
                                drawRect(
                                    Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(x - size.height, 0f), Offset(x, size.height)),
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The cut: a dark edge above, gold leaf in the letters.
                Text("PORCHLIGHT", style = Display.copy(fontSize = 30.sp, letterSpacing = 3.sp, color = Color.Black.copy(alpha = 0.6f)), modifier = Modifier.graphicsLayer { translationY = -1.5f * density })
                Text(
                    "PORCHLIGHT",
                    style = Display.copy(
                        fontSize = 30.sp,
                        letterSpacing = 3.sp,
                        brush = Brush.verticalGradient(listOf(Color(0xFFFFF2B8), Color(0xFFF0B83C), Color(0xFFA8701A))),
                    ),
                )
            }
        }

        // The moths, drawn over the sign; they dim as their loop takes them behind it.
        SIGN_MOTHS.forEachIndexed { k, (moth, perchAt) ->
            MothArt(
                moth,
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        if (box == IntSize.Zero) return@graphicsLayer
                        val m = signMoth(k, t(), calm)
                        val half = 12.dp.toPx()
                        val boardTop = chain.toPx()
                        val perch = Offset(box.width * perchAt, boardTop - 8.dp.toPx())
                        val orbit = Offset(box.width / 2f + m.loop.x * box.width * 0.6f, boardTop + (box.height - boardTop) / 2f + m.loop.y * box.height * 0.95f)
                        val pos = Offset(perch.x + (orbit.x - perch.x) * m.air, perch.y + (orbit.y - perch.y) * m.air)
                        translationX = pos.x - half
                        translationY = pos.y - half
                        val d = m.front * m.air
                        scaleX = 0.85f + 0.2f * d
                        scaleY = 0.85f + 0.2f * d
                        alpha = 1f - 0.55f * (-d).coerceAtLeast(0f)
                        // In the air it leans the way it is going; on the board it sits upright.
                        rotationZ = Math.toDegrees(atan2(-m.loop.y.toDouble(), m.loop.x.toDouble())).toFloat() * m.air * 0.4f
                    },
                open = signMoth(k, t(), calm).wings,
            )
        }
    }
}

/**
 * Where a sign moth is: its place on its loop round the board ([loop], -1..1 each way), how far in
 * front of the board it is ([front], -1 behind to 1 in front), how far it is off the board ([air], 0
 * perched to 1 in flight), and how open its wings are.
 */
private class SignMoth(val loop: Offset, val front: Float, val air: Float, val wings: Float)

/** Sign moth [k] at [t]: each flies for a while, then perches for a few seconds, out of step with the other. */
private fun signMoth(k: Int, t: Float, calm: Boolean): SignMoth {
    if (calm) return SignMoth(Offset.Zero, 0f, 0f, 1f)
    val period = 11f + k * 2.3f
    val perchFor = 4.2f
    val local = (t + k * 5.1f) % period
    val flyFor = period - perchFor
    // Perched: wings breathing slowly.
    if (local >= flyFor) return SignMoth(Offset.Zero, 0f, 0f, 0.85f + 0.15f * sin(t * 1.5f + k))
    val f = local / flyFor
    fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val air = minOf(smooth(f / 0.14f), smooth((1f - f) / 0.14f))
    val a = f * 2f * PI.toFloat() * 1.5f + k * PI.toFloat()
    val loop = Offset(cos(a), sin(a) * 0.55f + sin(t * 3f + k) * 0.08f)
    return SignMoth(loop, sin(a), air, 0.35f + 0.65f * abs(sin(t * 20f + k)))
}
