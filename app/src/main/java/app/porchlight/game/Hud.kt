package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Score, top left of the round. It counts up instead of jumping. */
@Composable
internal fun ScoreReadout(score: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(score, tween(450, easing = FastOutSlowInEasing), label = "score")
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "Score $score" }) {
        Text("SCORE", style = Label, color = Dusk.inkSoft)
        Text(shown.grouped(), style = Display, fontSize = 30.sp, color = Dusk.amber)
    }
}

/**
 * Streak multiplier, top right of the round. It pops as it climbs, glows for as long as Overdrive
 * holds, and cracks and drops when the streak breaks.
 */
@Composable
internal fun StreakReadout(multiplier: Int, calm: Boolean, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    val crack = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(multiplier) }
    LaunchedEffect(multiplier) {
        val was = last
        last = multiplier
        if (multiplier > was) {
            pulse.snapTo(1.3f + 0.12f * multiplier)
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else if (multiplier < was && !calm) {
            crack.snapTo(1f)
            crack.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
    }
    val over = multiplier >= MAX_MULTIPLIER
    val glow by animateFloatAsState(if (over) 1f else 0f, tween(200), label = "glow")
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Streak times $multiplier" },
        horizontalAlignment = Alignment.End,
    ) {
        Text(if (over) "OVERDRIVE" else "STREAK", style = Label, color = if (over) Dusk.ember else Dusk.inkSoft)
        Text(
            "×$multiplier",
            style = Display.copy(shadow = if (over) Shadow(Dusk.ember, Offset.Zero, 30f) else null),
            fontSize = 30.sp,
            color = androidx.compose.ui.graphics.lerp(Dusk.amber, Dusk.glow, glow),
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
                rotationZ = crack.value * 22f
                translationY = crack.value * 14.dp.toPx()
                transformOrigin = TransformOrigin(1f, 0.5f)
            },
        )
    }
}

/** A small round button that sits by the bulb. */
@Composable
internal fun RoundIconButton(
    label: String,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) Dusk.amber else Dusk.panel)
            .border(1.dp, if (enabled) Dusk.glow else Dusk.panelEdge, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Display, fontSize = 15.sp, color = if (enabled) Dusk.paperInk else Dusk.inkSoft)
    }
}

/** Level circle with the XP bar beside it. */
@Composable
internal fun LevelBar(xp: Int, modifier: Modifier = Modifier, animate: Boolean = true) {
    val level = levelFor(xp)
    val floor = xpFor(level)
    val next = xpFor(level + 1)
    val fill by animateFloatAsState(
        ((xp - floor).toFloat() / (next - floor)).coerceIn(0f, 1f),
        tween(if (animate) 800 else 0, easing = FastOutSlowInEasing),
        label = "xp",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Dusk.amber)
                .semantics { contentDescription = "Level $level" },
            contentAlignment = Alignment.Center,
        ) {
            Text("$level", style = Display, fontSize = 19.sp, color = Dusk.paperInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text("LEVEL $level", style = Label, color = Dusk.ink, modifier = Modifier.weight(1f))
                Text("${xp - floor} / ${next - floor} XP", style = Label, color = Dusk.inkSoft)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Dusk.slot),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Dusk.amber),
                )
            }
        }
    }
}

@Composable
internal fun Star(filled: Boolean, shown: Boolean, modifier: Modifier = Modifier, instant: Boolean = false) {
    val scale by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "star",
    )
    Canvas(
        modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = (1f - scale) * -40f
            },
    ) {
        val c = Offset(size.width / 2, size.height / 2 + size.height * 0.04f)
        val outer = size.minDimension / 2
        val inner = outer * 0.45f
        val star = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians(-90.0 + i * 36.0)
                val pt = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
            close()
        }
        drawPath(star, if (filled) Dusk.amber else Dusk.slot)
        drawPath(star, if (filled) Dusk.glow else Dusk.panelEdge, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** A round medal with the badge's mark on it; grey until earned. */
@Composable
internal fun BadgeMedal(badge: Badge, earned: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (earned) Dusk.amber else Dusk.slot)
            .border(2.dp, if (earned) Dusk.glow else Dusk.panelEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(badge.glyph, style = Display, fontSize = 14.sp, color = if (earned) Dusk.paperInk else Dusk.locked, maxLines = 1)
    }
}

@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PillButton(text, Dusk.amber, Dusk.paperInk, null, modifier, onClick)
}

@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PillButton(text, Dusk.panel, Dusk.ink, Dusk.panelEdge, modifier, onClick)
}

@Composable
private fun PillButton(text: String, fill: Color, ink: Color, edge: Color?, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(fill)
            .then(if (edge != null) Modifier.border(1.5.dp, edge, RoundedCornerShape(28.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Display, fontSize = 19.sp, color = ink, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
internal fun Chip(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Label,
        fontSize = 12.sp,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
