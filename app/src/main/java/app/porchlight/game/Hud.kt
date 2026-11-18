package app.porchlight.game

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** A brass rule round a shape, lit from the top-left. */
internal val BrassRule = Brush.linearGradient(listOf(Craft.brassLight, Craft.brass, Craft.brassDark, Craft.brass))

/** A small ebony plate with a brass rim: where the round keeps its numbers. */
internal fun Modifier.plate(shape: Shape = RoundedCornerShape(10.dp), lit: Color? = null): Modifier =
    this
        .shadow(4.dp, shape, ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
        .wood(Wood.Ebony, 10.dp, seed = 7, bevel = false)
        .clip(shape)
        .border(1.5.dp, if (lit != null) Brush.linearGradient(listOf(lit, Craft.brassLight, lit)) else BrassRule, shape)


/** Score, top left of the round. It counts up instead of jumping. */
@Composable
internal fun ScoreReadout(score: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .plate()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Score $score" },
    ) {
        Text("SCORE", style = Label, color = Dusk.inkSoft)
        CountUp(score, TextGlow, TypeScale.Title, durationMs = 450)
    }
}

/**
 * Streak multiplier, top right of the round. It pops as it climbs, turns gold for as long as
 * Overdrive holds, and cracks and drops when the streak breaks.
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
    Column(
        modifier
            .plate(lit = if (over) Craft.gold else null)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Streak times $multiplier" },
        horizontalAlignment = Alignment.End,
    ) {
        Text(if (over) "OVERDRIVE" else "STREAK", style = Label, color = if (over) Craft.gold else Dusk.inkSoft)
        // ×1 is no streak at all, so it shows a dash until the second pair in a row.
        Text(
            if (multiplier <= 1) "–" else "×$multiplier",
            style = TextGlow,
            fontSize = TypeScale.Title,
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

/** A round brass button with a drawn icon. Unlit, it is dull iron. */
@Composable
internal fun BrassButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // Pressed, the coin sinks onto its edge with a spring, and its mark goes with it.
    val sink by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "sink")
    Box(
        modifier
            .size(size)
            .drawBehind {
                val (c, r) = coinFace(this.size)
                coin(c, r, press = sink, dull = !enabled)
            }
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // The mark is stamped into the face: a dark cut with a light edge below it.
        Box(Modifier.graphicsLayer { translationY = (sink * 0.05f - 0.04f) * this.size.height * 2f }) {
            GlyphArt(glyph, Color.White.copy(alpha = if (enabled) 0.45f else 0.15f), Modifier.size(size * 0.46f).offset(y = 1.dp))
            GlyphArt(glyph, if (enabled) Color(0xFF3E2810) else Dusk.locked, Modifier.size(size * 0.46f))
        }
    }
}

/** XP still needed for the next level. */
internal fun xpToGo(xp: Int): Int = xpFor(levelFor(xp) + 1) - xp

/** How far through the current level [xp] is, 0..1. */
internal fun levelFill(xp: Int): Float {
    val level = levelFor(xp)
    return ((xp - xpFor(level)).toFloat() / (xpFor(level + 1) - xpFor(level))).coerceIn(0f, 1f)
}

/**
 * The level in the top bar: the emblem with its XP ring, the level as a label, and what's next in
 * words ("172 XP to level 12") rather than raw totals.
 */
@Composable
internal fun LevelPlate(xp: Int, modifier: Modifier = Modifier, still: Boolean = false) {
    val level = levelFor(xp)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Level $level. ${xpToGo(xp).grouped()} XP to level ${level + 1}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The scorecard's emblem flies home to this spot.
        Emblem(level, Modifier.flies("emblem").size(64.dp), xp = levelFill(xp), still = still)
        Column {
            Text("LEVEL $level", style = Label, color = Dusk.ink, maxLines = 1)
            Text("${xpToGo(xp).grouped()} XP to level ${level + 1}", style = BodyText, color = Dusk.inkSoft, maxLines = 1)
        }
    }
}

/** A gold star, bevelled; an unearned one is an empty iron socket. */
@Composable
internal fun Star(filled: Boolean, shown: Boolean, modifier: Modifier = Modifier, instant: Boolean = false) {
    val scale by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "star",
    )
    Canvas(
        modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = (1f - scale) * -40f
            },
    ) {
        val c = Offset(size.width / 2, size.height / 2 + size.height * 0.04f)
        val outer = size.minDimension / 2 - 2.dp.toPx()
        val inner = outer * 0.47f
        val star = Path()
        // Every other facet is lit, so the star reads as cut metal.
        val facets = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = c.x + (r * cos(a)).toFloat()
            val y = c.y + (r * sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            if (i % 2 == 0) {
                val b = Math.toRadians(-90.0 + (i + 1) * 36.0)
                facets.moveTo(c.x, c.y)
                facets.lineTo(x, y)
                facets.lineTo(c.x + (inner * cos(b)).toFloat(), c.y + (inner * sin(b)).toFloat())
                facets.close()
            }
        }
        star.close()
        if (filled) {
            drawPath(star, Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Craft.gold, Craft.goldDeep)))
            drawPath(facets, Color.White, alpha = 0.28f)
            drawPath(star, Craft.goldDeep, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        } else {
            drawPath(star, Craft.navyDeep)
            drawPath(star, Craft.iron, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

/** The main action: an oak sign with carved lettering, held on by two brass rivets. */
@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    SignButton(text, Wood.Oak, Dusk.paperInk, signOnOak, modifier, onClick, busy)
}

/** A quieter action: a walnut sign with gilt lettering. */
@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SignButton(text, Wood.Walnut, Dusk.amber, signOnWalnut, modifier, onClick)
}

@Composable
private fun SignButton(text: String, wood: Wood, ink: Color, style: TextStyle, modifier: Modifier, onClick: () -> Unit, busy: Boolean = false) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // A sign gives under the finger with a spring, and sinks a little toward the wall.
    val squeeze by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "squeeze")
    Box(
        modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
                translationY = (1f - squeeze) * 60f
            }
            .plank(wood, corner = 12.dp, elevation = 8.dp, seed = text.length)
            .border(1.5.dp, BrassRule, RoundedCornerShape(12.dp))
            .drawBehind {
                rivet(Offset(14.dp.toPx(), size.height / 2), 3.5.dp.toPx())
                rivet(Offset(size.width - 14.dp.toPx(), size.height / 2), 3.5.dp.toPx())
            }
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Work in hand (the share image being made): a small spinner before the words.
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = ink,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 10.dp).size(16.dp),
                )
            }
            FitText(text.uppercase(), style, TypeScale.Heading, ink)
        }
    }
}

/** A small stamped tag. */
@Composable
internal fun Chip(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Label,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/**
 * A brass lever switch: the knob slides along an ebony slot, and the slot glows amber when on.
 * It is a switch to screen readers.
 */
@Composable
internal fun LeverSwitch(on: Boolean, description: String, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val at by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "lever")
    Box(
        modifier
            .size(64.dp, 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            )
            .semantics {
                contentDescription = description
                stateDescription = if (on) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(56.dp, 30.dp)) {
            val r = size.height / 2
            val track = CornerRadius(r)
            drawRoundRect(Dusk.slot, cornerRadius = track)
            drawRoundRect(Dusk.amber, cornerRadius = track, alpha = 0.55f * at)
            drawRoundRect(BrassRule, cornerRadius = track, style = Stroke(1.5.dp.toPx()))
            val x = r + (size.width - 2 * r) * at
            drawCircle(Dusk.shadow, r * 0.86f, Offset(x, r + 2.dp.toPx()), alpha = 0.5f)
            drawCircle(
                Brush.linearGradient(listOf(Craft.brassLight, Craft.brass, Craft.brassDark), Offset(x - r, 0f), Offset(x + r, size.height)),
                r * 0.82f,
                Offset(x, r),
            )
            drawCircle(Craft.brassDark, r * 0.82f, Offset(x, r), style = Stroke(1.dp.toPx()))
            drawCircle(Color.White, r * 0.22f, Offset(x - r * 0.25f, r - r * 0.28f), alpha = 0.55f)
        }
    }
}

/**
 * A plank button's caption, painted on the wood: the body font in bold capitals. On light oak the
 * ink is dark with a light edge below; on dark walnut it is amber paint with a dark cut above.
 */
private val signOnOak = BodyText.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, shadow = TextCarved.shadow)
private val signOnWalnut = BodyText.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, -1.5f), 0f))

/**
 * The play button: the porch's one big action. A taller oak sign with one word in wood type, and a band of light sliding across every
 * few seconds. It sinks under the finger like the other signs. [calm] keeps it still.
 */
@Composable
internal fun PlayButton(text: String, calm: Boolean, modifier: Modifier = Modifier, sub: String? = null, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "squeeze")
    val loop = rememberInfiniteTransition(label = "play")
    val sweep by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(
        modifier
            .height(if (sub != null) 80.dp else 72.dp)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
                translationY = (1f - squeeze) * 60f
            }
            .plank(Wood.Oak, corner = 14.dp, elevation = 12.dp, seed = 4)
            .border(2.dp, BrassRule, RoundedCornerShape(14.dp))
            .drawWithContent {
                drawContent()
                rivet(Offset(16.dp.toPx(), size.height / 2), 4.dp.toPx())
                rivet(Offset(size.width - 16.dp.toPx(), size.height / 2), 4.dp.toPx())
                if (!calm) {
                    // The band of light: across in the first third of each cycle, then a rest.
                    val p = (sweep * 3f).coerceAtMost(1f)
                    if (p < 1f) {
                        val x = -size.width * 0.3f + size.width * 1.6f * p
                        clipRect {
                            rotate(20f, Offset(x, size.height / 2)) {
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), x - 30.dp.toPx(), x + 30.dp.toPx()),
                                    Offset(x - 30.dp.toPx(), -size.height),
                                    androidx.compose.ui.geometry.Size(60.dp.toPx(), size.height * 3f),
                                )
                            }
                        }
                    }
                }
            }
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (sub == null) {
            FitText(text.uppercase(), TextCarved.copy(letterSpacing = 4.sp), TypeScale.Title, Dusk.paperInk)
        } else {
            // A second, smaller line carved under the word: who is leading and what they bring.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FitText(text.uppercase(), TextCarved.copy(letterSpacing = 4.sp), TypeScale.Title, Dusk.paperInk)
                Text(
                    sub,
                    style = BodyText.copy(fontWeight = FontWeight.SemiBold, shadow = TextCarved.shadow),
                    fontSize = 13.sp,
                    color = Dusk.paperInk.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
