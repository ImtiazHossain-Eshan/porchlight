package app.porchlight.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.pow

/*
 * Text that moves, shared by every screen. Each helper takes `calm` (reduce motion): then it
 * falls back to a plain fade or simply shows the final text.
 */

/**
 * A rolling odometer: [value] in grouped digits ("3,240"), each digit rolling on its own as the
 * number climbs, the last digits fastest. With [instant] or [calm] it shows the value at once.
 */
@Composable
internal fun CountUp(
    value: Int,
    style: TextStyle,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    durationMs: Int = 900,
    instant: Boolean = false,
    calm: Boolean = false,
) {
    val shown by animateFloatAsState(
        value.toFloat(),
        tween(if (instant || calm) 0 else durationMs, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    val text = value.coerceAtLeast(0).grouped()
    val digits = text.count { it.isDigit() }
    Row(modifier.semantics { contentDescription = text }) {
        var place = digits - 1
        for (ch in text) {
            if (!ch.isDigit()) {
                Text("$ch", style = style, fontSize = fontSize, color = color)
                continue
            }
            val unit = 10.0.pow(place).toFloat()
            val q = floor(shown / unit)
            val digit = (q.toInt() % 10 + 10) % 10
            // A column turns over only while everything below it runs from 9 back to 0.
            val below = shown - q * unit
            val roll = if (place == 0) shown - floor(shown) else ((below - (unit - 1f)) / 1f).coerceIn(0f, 1f)
            Digit(digit, roll, style, fontSize, color)
            place -= 1
        }
    }
}

/** One odometer column: [digit] sliding up out of view by [roll] (0..1) as the next one rises in. */
@Composable
private fun Digit(digit: Int, roll: Float, style: TextStyle, fontSize: TextUnit, color: Color) {
    Box(Modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // An 8 holds the column's size, so a 1 doesn't make it narrower.
        Text("8", style = style.copy(brush = null, shadow = null), fontSize = fontSize, color = Color.Transparent)
        Text("$digit", style = style, fontSize = fontSize, color = color, modifier = Modifier.graphicsLayer { translationY = -roll * size.height })
        if (roll > 0f) {
            Text("${(digit + 1) % 10}", style = style, fontSize = fontSize, color = color, modifier = Modifier.graphicsLayer { translationY = (1f - roll) * size.height })
        }
    }
}

/**
 * Shows [text], punching each new value in: scale 1.3 to 1 on a spring, with a fade. For numbers
 * and short calls (GO!, +400). [calm] fades only.
 */
@Composable
internal fun PunchIn(
    text: String,
    style: TextStyle,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    calm: Boolean = false,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (calm) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
            else (fadeIn(tween(110)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 900f), initialScale = 1.3f)) togetherWith fadeOut(tween(140))
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "punchIn",
    ) { shown ->
        Text(shown, style = style, fontSize = fontSize, color = color)
    }
}

/**
 * A neon sign warming up: the letters of [text] light one by one, 35 ms apart, then hold.
 * [instant] or [calm] shows it lit at once (calm with a fade).
 */
@Composable
internal fun NeonOn(
    text: String,
    style: TextStyle,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    instant: Boolean = false,
    calm: Boolean = false,
    onLetter: () -> Unit = {},
) {
    val lit = remember(text) { Animatable(if (instant) text.length.toFloat() else 0f) }
    val fade = remember(text) { Animatable(if (instant || !calm) 1f else 0f) }
    LaunchedEffect(text, instant, calm) {
        when {
            instant -> lit.snapTo(text.length.toFloat())
            calm -> {
                lit.snapTo(text.length.toFloat())
                fade.animateTo(1f, tween(250))
            }
            else -> for (i in 1..text.length) {
                lit.snapTo(i.toFloat())
                if (text[i - 1] != ' ') onLetter()
                delay(35)
            }
        }
    }
    Text(
        letters(text, style.brush ?: SolidColor(Dusk.amber)) { i -> if (i < lit.value) 1f else 0.08f },
        style = style,
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        modifier = modifier.graphicsLayer { alpha = fade.value }.semantics { contentDescription = text },
    )
}

/**
 * A sign going out: it shows lit, flickers two or three times, then dims to an ember and the
 * letters sag 2 dp as they cool. [instant] or [calm] shows the ember at once.
 */
@Composable
internal fun NeonOff(
    text: String,
    style: TextStyle,
    fontSize: TextUnit,
    ember: Color,
    modifier: Modifier = Modifier,
    instant: Boolean = false,
    calm: Boolean = false,
    onFizzle: () -> Unit = {},
) {
    val glow = remember(text) { Animatable(if (instant || calm) 0f else 1f) }
    val sag = remember(text) { Animatable(if (instant || calm) 1f else 0f) }
    LaunchedEffect(text, instant, calm) {
        if (instant || calm) {
            glow.snapTo(0f)
            sag.snapTo(1f)
            return@LaunchedEffect
        }
        delay(250)
        onFizzle()
        for (dip in listOf(0.25f, 0.85f, 0.15f, 0.7f)) {
            glow.snapTo(dip)
            delay(70)
        }
        launch { sag.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        glow.animateTo(0f, tween(600))
    }
    // Lit, it is the warm glow style; cooled, it is ember-coloured with a faint glow of its own.
    val lit = glow.value > 0.5f
    Text(
        text,
        style = if (lit) style else style.copy(brush = null, shadow = glowOf(ember, 10f)),
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        color = if (lit) Color.Unspecified else ember,
        modifier = modifier.graphicsLayer {
            translationY = 2.dp.toPx() * sag.value
            alpha = if (lit) 1f else 0.9f
        },
    )
}

/** [text] with each letter's alpha set by [alphaAt]. */
private fun letters(text: String, brush: Brush, alphaAt: (Int) -> Float): AnnotatedString = buildAnnotatedString {
    text.forEachIndexed { i, ch -> withStyle(SpanStyle(brush = brush, alpha = alphaAt(i))) { append(ch) } }
}

/**
 * Stamps [content] down: scale 1.6 to 1 with a 3 dp shake as it lands, then calls [onLand] (for a
 * thud). With [instant] it is simply there; with [calm] it fades in.
 */
@Composable
internal fun Stamp(
    modifier: Modifier = Modifier,
    instant: Boolean = false,
    calm: Boolean = false,
    delayMs: Long = 0,
    onLand: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(if (instant || calm) 1f else 1.6f) }
    val alpha = remember { Animatable(if (instant) 1f else 0f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(instant, calm) {
        if (instant) return@LaunchedEffect
        if (delayMs > 0) delay(delayMs)
        if (calm) {
            alpha.animateTo(1f, tween(220))
            return@LaunchedEffect
        }
        launch { alpha.animateTo(1f, tween(90)) }
        scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        onLand()
        for (x in listOf(3f, -3f, 2f, -1f, 0f)) shake.animateTo(x, tween(35))
    }
    Box(
        modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
            translationX = shake.value * density
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * One line of [text] at [size], stepping down only as far as it must to fit (a large font scale,
 * a narrow phone). For signs and plank buttons, where wrapping would break the plank.
 */
@Composable
internal fun FitText(text: String, style: TextStyle, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = TypeScale.Label, maxFontSize = size, stepSize = 1.sp),
    )
}
