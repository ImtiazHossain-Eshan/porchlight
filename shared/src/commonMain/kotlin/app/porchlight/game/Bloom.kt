package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Bloom: the bright things (the bulb's core, the sparks, the gold rims of found pairs) drawn again
 * into their own layer, blurred 20dp, and added on top, so they glow past their edges.
 * Needs Android 12 for the blur; below that the scene goes without. At [Gpu.quality] 1 and up the
 * layer is half resolution, which costs a quarter as much.
 */
@Composable
internal fun Bloom(session: Session) {
    if (!Gpu.blur) return
    val core = remember {
        Brush.radialGradient(
            0f to Color(0xFFFFF4D0),
            0.5f to Color(0xFFFFC870).copy(alpha = 0.6f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = 100f,
        )
    }
    BoxWithConstraints {
        val half = Gpu.quality >= 1
        val k = if (half) 0.5f else 1f
        Canvas(
            Modifier
                .size(maxWidth * k, maxHeight * k)
                .graphicsLayer {
                    if (half) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = 2f
                        scaleY = 2f
                    }
                    renderEffect = blurOf(20.dp.toPx() * k)
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Plus
                },
        ) {
            session.clock.seconds // redraw every frame
            scale(k, k, pivot = Offset.Zero) {
                // The bulb's core, as bright as it burns.
                val power = session.power().coerceIn(0f, 1.6f)
                if (power > 0.02f && session.bulbWidth > 0f) {
                    val r = session.bulbWidth * (0.55f + 0.25f * session.timeLeft)
                    val at = session.bulbAt
                    translate(at.x, at.y) {
                        scale(r / 100f, r / 100f, pivot = Offset.Zero) {
                            drawCircle(core, 100f, Offset.Zero, alpha = (0.38f * power).coerceAtMost(1f))
                        }
                    }
                }
                if (!session.screen.isMenu) {
                    session.particles.draw(this)
                }
            }
        }
    }
}
